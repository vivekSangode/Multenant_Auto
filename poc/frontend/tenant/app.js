// Company portal: one code base for every company. The Company Login Code picks the company's own realm (CD-15);
// its branding re-themes the whole UI; every API call carries that company's token and nothing else.
import { CONFIG } from '../config.js';
import { OidcSession } from '../shared/auth.js';
import { createApi } from '../shared/api.js';
import { createRouter } from '../shared/router.js';
import { applyBranding, PLATFORM_BRANDING } from '../shared/theme.js';
import { createShell, bootScreen } from '../shared/shell.js';
import { h, ic, mount, emptyState, toastError, busy, titleCase } from '../shared/ui.js';
import { MODULE_UI, moduleState } from './lib.js';
import { dashboardPage } from './pages/dashboard.js';
import { vehiclesPage } from './pages/vehicles.js';
import { inventoryPage } from './pages/inventory.js';
import { bookingsPage } from './pages/bookings.js';

const root = document.getElementById('app');
const session = new OidcSession('tenant');
const publicApi = createApi(CONFIG.controlPlane);
const REDIRECT = `${window.location.origin}${window.location.pathname}`;
const LAST_CODE = 'erp.tenant.lastLoginCode';
const BRANDING_CACHE = 'erp.tenant.lastBranding';


applyBranding({ ...PLATFORM_BRANDING, primaryColor: '#334155', accentColor: '#0EA5E9' }, { scope: 'tenant', title: 'Company portal · Nyaalkaran ERP' });
boot();

async function boot() {
  bootScreen(root, 'Opening your workspace…');
  let loginError = null;
  if (OidcSession.isCallback()) {
    try { await session.handleCallback(); } catch (e) { loginError = e; }
  }
  if (!loginError && session.isAuthenticated) {
    const cached = safeGet(BRANDING_CACHE);
    if (cached) { try { applyBranding(JSON.parse(cached), { scope: 'tenant' }); } catch { /* ignore */ } }
    try {
      const api = createApi(CONFIG.controlPlane, () => session.token());
      const me = await api.get('/api/v1/company/me');
      const linkCode = new URLSearchParams(window.location.hash.split('?')[1] || '').get('code');
      if (linkCode && linkCode.toUpperCase() !== me.loginCode) {
        // a portal link of another company: leave this workspace and go to that company's sign-in
        await session.logout();
        window.history.replaceState(null, '', window.location.pathname + '#/');
        return renderLogin({ code: linkCode.toUpperCase() });
      }
      return startApp(me);
    } catch (e) {
      if (e.code === 'NETWORK_ERROR') return renderUnavailable(e);
      await session.logout();
      loginError = { message: e.status === 401 ? 'Your session ended. Please sign in again.' : (e.detail || e.message) };
    }
  }
  const code = new URLSearchParams(window.location.hash.split('?')[1] || '').get('code') || safeGet(LAST_CODE);
  renderLogin({ code, error: loginError });
}

// ------------------------------------------------------------------ login (discovery -> branded sign-in)
function renderLogin({ code, error }) {
  const art = h('section', { class: 'auth-art' });
  const panel = h('div', { class: 'auth-box stack', style: { gap: '18px' } });
  mount(root, h('div', { class: 'auth' }, art, h('section', { class: 'auth-panel' }, panel)));

  const neutralArt = () => mount(art,
    h('div', { class: 'row' }, h('div', { class: 'brand-logo', style: { background: 'rgba(255,255,255,.18)', boxShadow: 'none' } }, 'NE'), h('b', {}, 'Company portal')),
    h('div', {}, h('h1', {}, 'Your company, your workspace.'),
      h('p', {}, 'Enter the login code your administrator gave you. You will sign in to your company\'s own secure realm.')),
    h('div', { class: 'small', style: { opacity: .8 } }, 'Powered by Nyaalkaran ERP'), h('div', { class: 'art-shapes' }));

  function codeStep(prefill, err) {
    applyBranding({ ...PLATFORM_BRANDING, primaryColor: '#334155', accentColor: '#0EA5E9' }, { scope: 'tenant', title: 'Company portal · Nyaalkaran ERP' });
    neutralArt();
    const codeInput = h('input', { class: 'input lg', maxLength: 12, placeholder: 'LOGIN CODE', value: prefill || '', autocomplete: 'organization', 'aria-label': 'Company login code' });
    const go = h('button', { class: 'btn primary lg block', type: 'submit' }, 'Continue', ic('arrowRight'));
    const msg = h('div');
    const form = h('form', { class: 'stack', style: { gap: '14px' } }, codeInput, msg, go);
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const value = codeInput.value.trim().toUpperCase();
      if (!/^[A-Z0-9]{4,12}$/.test(value)) { codeInput.classList.add('invalid'); return; }
      busy(go, () => discover(value)).catch((ex) => {
        codeInput.classList.add('invalid');
        mount(msg, h('div', { class: 'banner bad' }, ic('alert'), h('div', { class: 'small' }, ex.detail || ex.message)));
      });
    });
    mount(panel,
      h('div', {}, h('h2', {}, 'Sign in to your company'), h('p', { class: 'muted' }, 'Step 1 of 2 · Company login code')),
      err ? h('div', { class: 'banner bad' }, ic('alert'), h('div', { class: 'small' }, err.message || err.detail)) : null,
      form,
      h('a', { href: '../platform/', class: 'small muted' }, 'Platform team? Open the platform console →'));
    setTimeout(() => codeInput.focus(), 50);
  }

  async function discover(value) {
    const d = await publicApi.get(`/api/v1/public/login-discovery?loginCode=${encodeURIComponent(value)}`);
    safeSet(LAST_CODE, value);
    brandedStep(d);
  }

  function brandedStep(d, err) {
    const b = d.branding;
    applyBranding(b, { scope: 'tenant', title: `${d.companyName} · Sign in` });
    const logo = h('div', { class: 'brand-logo', style: { width: '52px', height: '52px', fontSize: '18px', borderRadius: '14px', background: 'rgba(255,255,255,.2)', boxShadow: 'none' } },
      b.logoUrl ? h('img', { src: b.logoUrl, alt: '' }) : b.logoText);
    mount(art,
      h('div', { class: 'row' }, logo, h('div', {}, h('div', { style: { fontWeight: 750, fontSize: '17px' } }, d.companyName), h('div', { class: 'small', style: { opacity: .85 } }, 'Company portal'))),
      h('div', {}, h('h1', {}, b.tagline || `Welcome to ${d.companyName}`),
        h('p', {}, 'Vehicles, stock and bookings of your dealership, in one place. Only your company\'s people and data live here.'),
        h('ul', { class: 'feature-list' }, [['car', 'Vehicle catalog'], ['box', 'Live inventory by VIN'], ['calendar', 'Customer bookings']].map(([i, t]) => h('li', {}, ic(i), t)))),
      h('div', { class: 'small', style: { opacity: .8 } }, `Secure sign-in · realm ${d.realm}`), h('div', { class: 'art-shapes' }));
    const signIn = h('button', { class: 'btn primary lg block' }, ic('lock'), `Sign in to ${d.companyName}`);
    signIn.addEventListener('click', () => {
      signIn.classList.add('loading');
      session.login({ issuer: d.issuer, clientId: d.clientId, redirectUri: REDIRECT, extra: { loginCode: d.loginCode } });
    });
    const change = h('button', { class: 'btn ghost block', type: 'button' }, ic('arrowLeft'), 'Use another company code');
    change.addEventListener('click', () => { safeSet(LAST_CODE, ''); codeStep(''); });
    mount(panel,
      h('div', { class: 'row' }, h('div', { class: 'brand-logo' }, b.logoUrl ? h('img', { src: b.logoUrl, alt: '' }) : b.logoText),
        h('div', {}, h('h2', {}, d.companyName), h('div', { class: 'muted small' }, `Step 2 of 2 · login code ${d.loginCode}`))),
      d.status !== 'ACTIVE' ? h('div', { class: 'banner warn' }, ic('pause'), h('div', { class: 'small' },
        h('b', {}, `This workspace is ${titleCase(d.status)}. `), 'You can sign in, but business data is unavailable until your administrator reactivates it.')) : null,
      err ? h('div', { class: 'banner bad' }, ic('alert'), h('div', { class: 'small' }, err.message || err.detail)) : null,
      signIn, change,
      h('p', { class: 'muted small' }, 'First time? Use the temporary password from your administrator; you will be asked to choose your own.'));
  }

  if (code) {
    publicApi.get(`/api/v1/public/login-discovery?loginCode=${encodeURIComponent(code)}`)
      .then((d) => brandedStep(d, error))
      .catch(() => codeStep(code, error));
  } else {
    codeStep('', error);
  }
}

// ------------------------------------------------------------------ the workspace
function startApp(me) {
  applyBranding(me.branding, { scope: `tenant.${me.companyCode}`, title: `${me.displayName} · Company portal` });
  safeSet(BRANDING_CACHE, JSON.stringify(me.branding)); // next load starts in the right colours (no flash)
  const cp = createApi(CONFIG.controlPlane, () => session.token());
  const svc = Object.fromEntries(Object.entries(CONFIG.services).map(([k, url]) => [k, createApi(url, () => session.token())]));
  const mod = (code) => me.modules.find((m) => m.code === code);
  const nav = [{ section: 'Workspace' }, { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', href: '#/' }];
  const withUi = Object.entries(MODULE_UI).filter(([code]) => mod(code));
  if (withUi.length) nav.push({ section: 'Dealership' });
  for (const [code, ui] of withUi) {
    nav.push({ id: code, label: ui.label, icon: ui.icon, href: `#${ui.route}`, locked: !mod(code).accessible, lockedReason: `You have no ${mod(code).gatewayToken} role` });
  }

  const shell = createShell(root, {
    scope: `tenant.${me.companyCode}`,
    brand: { name: me.displayName, sub: me.branding.tagline || 'Company portal', logoText: me.branding.logoText, logoUrl: me.branding.logoUrl },
    nav,
    foot: h('div', {}, h('div', {}, `${me.companyCode} · login code ${me.loginCode}`), h('div', {}, 'Powered by Nyaalkaran ERP')),
    user: { name: me.user.name || me.user.username, email: me.user.email, roles: me.user.roles },
    onLogout: async () => { await session.logout(); renderLogin({ code: me.loginCode }); },
  });

  const ctx = { me, cp, svc, shell, mod, can: (code) => !!mod(code)?.accessible, go: (hash) => router.go(hash) };
  const router = createRouter([
    { path: '/', nav: 'dashboard', page: dashboardPage },
    { path: '/vehicles', nav: 'vehicle', page: vehiclesPage, module: 'vehicle' },
    { path: '/inventory', nav: 'inventory', page: inventoryPage, module: 'inventory' },
    { path: '/bookings', nav: 'booking', page: bookingsPage, module: 'booking' },
  ], async (route, params, query) => {
    shell.setActive(route.nav);
    window.scrollTo(0, 0);
    const notice = me.status !== 'ACTIVE' ? h('div', { class: 'banner bad', style: { marginBottom: '18px' } }, ic('pause'),
      h('div', {}, h('b', {}, `${me.displayName} is ${titleCase(me.status)}.`), h('div', { class: 'small' }, 'Business data is unavailable. Contact your platform administrator.'))) : null;
    try {
      if (route.module && !mod(route.module)) return moduleState(shell, 'MODULE_NOT_ENTITLED', route.module);
      if (route.module && !ctx.can(route.module)) return moduleState(shell, 'MODULE_ACCESS_DENIED', route.module);
      await route.page(ctx, params, query);
      if (notice) shell.content.prepend(notice);
    } catch (e) {
      if (e.status === 401) { await session.logout(); return renderLogin({ code: me.loginCode, error: { message: 'Your session ended. Please sign in again.' } }); }
      if (['COMPANY_SUSPENDED', 'COMPANY_DISABLED', 'COMPANY_ARCHIVED', 'COMPANY_NOT_READY', 'MODULE_NOT_ENTITLED', 'MODULE_ACCESS_DENIED'].includes(e.code)) {
        return moduleState(shell, e.code, route.module, e);
      }
      toastError(e, 'Could not load the page');
      mount(shell.content, h('div', { class: 'card' }, emptyState({ icon: 'alert', title: 'This page could not be loaded', text: e.detail || e.message,
        action: h('button', { class: 'btn primary', onClick: () => router.start() }, ic('refresh'), 'Try again') })));
    }
  });
  router.start();
}


function renderUnavailable(e) {
  mount(root, h('div', { class: 'center-screen' }, h('div', { class: 'card', style: { maxWidth: '520px' } }, emptyState({
    icon: 'server', title: 'The workspace is not reachable right now', text: e.detail || e.message,
    action: h('button', { class: 'btn primary', onClick: boot }, ic('refresh'), 'Retry'),
  }))));
}

function safeGet(k) { try { return localStorage.getItem(k) || ''; } catch { return ''; } }
function safeSet(k, v) { try { localStorage.setItem(k, v); } catch { /* storage blocked */ } }
