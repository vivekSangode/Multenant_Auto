// Platform console: Platform Super Admins of realm `erp-platform` onboard and run companies.
import { CONFIG } from '../config.js';
import { OidcSession } from '../shared/auth.js';
import { createApi } from '../shared/api.js';
import { createRouter } from '../shared/router.js';
import { applyBranding, PLATFORM_BRANDING } from '../shared/theme.js';
import { createShell, bootScreen } from '../shared/shell.js';
import { h, ic, mount, emptyState, toastError } from '../shared/ui.js';
import { dashboardPage } from './pages/dashboard.js';
import { companiesPage } from './pages/companies.js';
import { companyDetailPage } from './pages/company-detail.js';
import { catalogPage } from './pages/catalog.js';

const root = document.getElementById('app');
const session = new OidcSession('platform');
const publicApi = createApi(CONFIG.controlPlane);
const REDIRECT = `${window.location.origin}${window.location.pathname}`;

applyBranding(PLATFORM_BRANDING, { scope: 'platform', title: 'Platform console · Nyaalkaran ERP' });
boot();

async function boot() {
  bootScreen(root, 'Starting the platform console…');
  let cfg;
  try {
    cfg = await publicApi.get('/api/v1/public/config');
  } catch (e) {
    return renderUnavailable(e);
  }
  const issuer = `${cfg.keycloakUrl}/realms/${cfg.platformRealm}`;
  let loginError = null;
  if (OidcSession.isCallback()) {
    try { await session.handleCallback(); } catch (e) { loginError = e; }
  }
  if (loginError || !session.isAuthenticated || session.issuer !== issuer) {
    return renderLogin(cfg, issuer, loginError);
  }
  const roles = session.claims.realm_access?.roles || [];
  if (!roles.includes('PLATFORM_SUPER_ADMIN')) {
    await session.logout();
    return renderLogin(cfg, issuer, { message: 'This account has no PLATFORM_SUPER_ADMIN role.' });
  }
  startApp(cfg, issuer);
}

function startApp(cfg, issuer) {
  const claims = session.claims;
  const api = createApi(CONFIG.controlPlane, () => session.token());
  const shell = createShell(root, {
    scope: 'platform', darkRail: true,
    brand: { name: 'Nyaalkaran ERP', sub: 'Platform console', logoText: 'NE' },
    nav: [
      { section: 'Overview' },
      { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', href: '#/' },
      { section: 'Tenants' },
      { id: 'companies', label: 'Companies', icon: 'building', href: '#/companies' },
      { id: 'catalog', label: 'Catalog & pricing', icon: 'layers', href: '#/catalog' },
    ],
    foot: h('div', {}, h('div', {}, 'Realm erp-platform'), h('div', {}, 'Signed in as Platform Super Admin')),
    user: { name: claims.name || claims.preferred_username, email: claims.email, roles: (claims.realm_access?.roles || []).filter((r) => r.startsWith('PLATFORM_')) },
    menuItems: [{ label: 'Open company portal', icon: 'external', onClick: () => window.open('../tenant/', '_blank') }],
    onLogout: async () => { await session.logout(); renderLogin(cfg, issuer); },
  });

  const ctx = {
    api, shell, cfg, user: { name: claims.name || claims.preferred_username },
    cache: { branding: new Map(), catalog: new Map() }, go: (hash) => router.go(hash),
  };
  const router = createRouter([
    { path: '/', nav: 'dashboard', page: dashboardPage },
    { path: '/companies', nav: 'companies', page: companiesPage },
    { path: '/companies/:code', nav: 'companies', page: companyDetailPage },
    { path: '/catalog', nav: 'catalog', page: catalogPage },
  ], async (route, params, query) => {
    shell.setActive(route.nav);
    shell.content.scrollTop = 0;
    window.scrollTo(0, 0);
    try {
      await route.page(ctx, params, query);
    } catch (e) {
      if (e.status === 401) { await session.logout(); return renderLogin(cfg, issuer, { message: 'Your session ended. Please sign in again.' }); }
      toastError(e, 'Could not load the page');
      mount(shell.content, h('div', { class: 'card' }, emptyState({
        icon: 'alert', title: 'This page could not be loaded', text: e.detail || e.message,
        action: h('button', { class: 'btn primary', onClick: () => router.start() }, ic('refresh'), 'Try again'),
      })));
    }
  });
  router.start();
}

function renderLogin(cfg, issuer, error) {
  const btn = h('button', { class: 'btn primary lg block' }, ic('shield'), 'Sign in with Keycloak');
  btn.addEventListener('click', () => {
    btn.classList.add('loading');
    session.login({ issuer, clientId: cfg.platformClientId, redirectUri: REDIRECT });
  });
  mount(root, h('div', { class: 'auth' },
    h('section', { class: 'auth-art' },
      h('div', { class: 'row' }, h('div', { class: 'brand-logo', style: { background: 'rgba(255,255,255,.18)', boxShadow: 'none' } }, 'NE'),
        h('div', { style: { fontWeight: 700 } }, 'Nyaalkaran ERP')),
      h('div', {},
        h('h1', {}, 'Run every company from one console.'),
        h('p', {}, 'Onboard companies, quote plans, collect payments and provision isolated workspaces: one database per module, one identity realm per company.'),
        h('ul', { class: 'feature-list' },
          [['building', 'Company lifecycle: draft, active, suspended, archived'], ['receipt', 'Quotes, full payment and invoices'],
            ['server', 'Automated provisioning with live progress'], ['palette', 'White-label branding per company']]
            .map(([i, t]) => h('li', {}, ic(i), t)))),
      h('div', { class: 'small', style: { opacity: .8 } }, 'Platform realm: erp-platform'),
      h('div', { class: 'art-shapes' })),
    h('section', { class: 'auth-panel' }, h('div', { class: 'auth-box stack', style: { gap: '18px' } },
      h('div', {}, h('h2', {}, 'Platform console'), h('p', { class: 'muted' }, 'Sign in with your platform account. Company users sign in through the company portal instead.')),
      error ? h('div', { class: 'banner bad' }, ic('alert'), h('div', {}, h('b', {}, 'Sign-in failed'), h('div', { class: 'small' }, error.message || error.detail))) : null,
      btn,
      h('div', { class: 'banner info' }, ic('info'), h('div', { class: 'small' }, 'Demo login: ', h('b', {}, 'superadmin'), ' / ', h('b', {}, 'change-me'))),
      h('a', { href: '../tenant/', class: 'small' }, 'Go to the company portal →'))),
  ));
}

function renderUnavailable(e) {
  mount(root, h('div', { class: 'center-screen' }, h('div', { class: 'card', style: { maxWidth: '520px' } }, emptyState({
    icon: 'server', title: 'The control plane is not reachable',
    text: `${e.detail || e.message}. Start it on ${CONFIG.controlPlane} (see poc/frontend/README.md), then retry.`,
    action: h('button', { class: 'btn primary', onClick: boot }, ic('refresh'), 'Retry'),
  }))));
}
