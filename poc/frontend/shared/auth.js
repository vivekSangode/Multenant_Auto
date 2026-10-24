// OpenID Connect Authorization Code + PKCE against Keycloak, without any library (master §14.2: "OIDC login (PKCE)").
// Tokens live in sessionStorage of this tab only. The realm is chosen per login: `erp-platform` for the platform
// console, the company realm `co-<n>` (found through the Company Login Code) for the tenant portal.

const b64url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const randomString = (n = 48) => b64url(crypto.getRandomValues(new Uint8Array(n)));

export function decodeJwt(token) {
  try {
    const p = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(p + '='.repeat((4 - (p.length % 4)) % 4)))));
  } catch { return {}; }
}

export class OidcSession {
  /** @param key distinguishes the two portals' sessions */
  constructor(key) {
    this.key = `erp.oidc.${key}`;
    this.pendingKey = `${this.key}.pending`;
    this.data = this.#load();
    this.refreshing = null;
  }

  #load() { try { return JSON.parse(sessionStorage.getItem(this.key)) || null; } catch { return null; } }
  #save() { if (this.data) sessionStorage.setItem(this.key, JSON.stringify(this.data)); else sessionStorage.removeItem(this.key); }

  get isAuthenticated() { return !!this.data?.refreshToken || (!!this.data?.accessToken && this.data.expiresAt > Date.now()); }
  get claims() { return this.data ? decodeJwt(this.data.accessToken) : {}; }
  get issuer() { return this.data?.issuer; }

  /** Redirects the browser to the realm's login page. `extra` is kept and returned after the callback. */
  async login({ issuer, clientId, redirectUri, extra = {}, loginHint }) {
    const verifier = randomString(64);
    const challenge = b64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
    const state = randomString(16);
    sessionStorage.setItem(this.pendingKey, JSON.stringify({ verifier, state, issuer, clientId, redirectUri, extra }));
    const q = new URLSearchParams({
      response_type: 'code', client_id: clientId, redirect_uri: redirectUri, scope: 'openid profile email',
      state, code_challenge: challenge, code_challenge_method: 'S256',
    });
    if (loginHint) q.set('login_hint', loginHint);
    window.location.assign(`${issuer}/protocol/openid-connect/auth?${q}`);
  }

  /** True when the current URL is Keycloak's redirect back to us. */
  static isCallback() {
    const q = new URLSearchParams(window.location.search);
    return q.has('state') && (q.has('code') || q.has('error'));
  }

  /** Exchanges the code for tokens; returns the `extra` given to login(). */
  async handleCallback() {
    const q = new URLSearchParams(window.location.search);
    const pending = JSON.parse(sessionStorage.getItem(this.pendingKey) || 'null');
    sessionStorage.removeItem(this.pendingKey);
    window.history.replaceState(null, '', window.location.pathname + '#/');
    if (q.get('error')) throw Object.assign(new Error(q.get('error_description') || q.get('error')), { code: 'LOGIN_FAILED' });
    if (!pending || pending.state !== q.get('state')) throw Object.assign(new Error('The sign-in response did not match this browser tab. Please try again.'), { code: 'LOGIN_STATE_MISMATCH' });
    const body = new URLSearchParams({
      grant_type: 'authorization_code', code: q.get('code'), redirect_uri: pending.redirectUri,
      client_id: pending.clientId, code_verifier: pending.verifier,
    });
    await this.#token(pending.issuer, pending.clientId, body);
    return pending.extra || {};
  }

  async #token(issuer, clientId, body) {
    const res = await fetch(`${issuer}/protocol/openid-connect/token`, {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body,
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      this.data = null; this.#save();
      throw Object.assign(new Error(json.error_description || 'Sign-in failed'), { code: (json.error || 'TOKEN_ERROR').toUpperCase(), status: res.status });
    }
    this.data = {
      issuer, clientId, accessToken: json.access_token, refreshToken: json.refresh_token, idToken: json.id_token,
      expiresAt: Date.now() + (json.expires_in || 60) * 1000,
    };
    this.#save();
  }

  /** A valid access token, refreshed shortly before expiry (access tokens live 5 minutes, master §14.1). */
  async token() {
    if (!this.data) throw Object.assign(new Error('Not signed in'), { code: 'NOT_SIGNED_IN', status: 401 });
    if (this.data.expiresAt - Date.now() > 30_000) return this.data.accessToken;
    if (!this.refreshing) {
      const { issuer, clientId, refreshToken } = this.data;
      this.refreshing = this.#token(issuer, clientId, new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken, client_id: clientId }))
        .finally(() => { this.refreshing = null; });
    }
    await this.refreshing;
    return this.data.accessToken;
  }

  /** Ends the Keycloak session (back-channel, no redirect) and forgets the tokens. */
  async logout() {
    const d = this.data;
    this.data = null; this.#save();
    if (d?.refreshToken) {
      await fetch(`${d.issuer}/protocol/openid-connect/logout`, {
        method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ client_id: d.clientId, refresh_token: d.refreshToken }),
      }).catch(() => {});
    }
  }
}
