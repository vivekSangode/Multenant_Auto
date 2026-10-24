// The application frame shared by both portals: sidebar navigation, sticky top bar, user menu, theme toggle.
import { h, ic, mount, menu, avatar, titleCase } from './ui.js';
import { toggleMode } from './theme.js';

/**
 * @param o.brand    { name, sub, logoText, logoUrl }
 * @param o.nav      [{ section } | { id, label, icon, href, locked, tail }]
 * @param o.user     { name, email, roles[] }
 * @param o.scope    theme-mode storage scope
 * @param o.darkRail dark sidebar (platform console)
 */
export function createShell(root, o) {
  const content = h('div', { class: 'content', id: 'content', tabIndex: -1 });
  const crumbs = h('div', { class: 'crumbs' });
  const navEls = new Map();

  const logo = h('div', { class: 'brand-logo' }, o.brand.logoUrl ? h('img', { src: o.brand.logoUrl, alt: '' }) : (o.brand.logoText || '').slice(0, 3));
  const sidebar = h('nav', { class: `sidebar ${o.darkRail ? 'dark-rail' : ''}`, 'aria-label': 'Main' },
    h('div', { class: 'brand' }, logo, h('div', { style: { minWidth: 0 } },
      h('div', { class: 'brand-name' }, o.brand.name), h('div', { class: 'brand-sub' }, o.brand.sub || ''))),
    o.nav.map((n) => {
      if (n.section) return h('div', { class: 'nav-section' }, n.section);
      const el = h('a', {
        class: 'nav-item', href: n.locked ? undefined : n.href, 'aria-disabled': n.locked ? 'true' : undefined,
        title: n.locked ? n.lockedReason : undefined,
      }, ic(n.icon), h('span', {}, n.label), n.locked ? h('span', { class: 'nav-tail' }, ic('lock')) : (n.tail || null));
      navEls.set(n.id, el);
      return el;
    }),
    h('div', { class: 'sidebar-foot' }, o.foot || ''));

  const modeBtn = h('button', { class: 'btn ghost icon-only', 'aria-label': 'Toggle dark mode', title: 'Toggle light / dark' });
  const renderModeIcon = () => mount(modeBtn, ic(document.documentElement.dataset.theme === 'dark' ? 'sun' : 'moon'));
  modeBtn.addEventListener('click', () => { toggleMode(o.scope); renderModeIcon(); });
  renderModeIcon();

  const userBtn = h('button', { class: 'user-chip', 'aria-haspopup': 'menu' },
    avatar(initials(o.user.name || o.user.email), { primaryColor: 'var(--primary)', accentColor: 'var(--accent)' }, 'round'),
    h('span', { class: 'small', style: { fontWeight: 600 } }, o.user.name || o.user.email), ic('arrowRight', 'icon'));
  userBtn.lastChild.style.transform = 'rotate(90deg)';
  userBtn.lastChild.style.width = '14px';

  const topbar = h('header', { class: 'topbar' }, crumbs, h('div', { class: 'spacer' }), o.topExtra || null, modeBtn,
    menu(userBtn, () => [
      { node: h('div', { style: { padding: '10px' } },
        h('div', { style: { fontWeight: 650 } }, o.user.name || o.user.email),
        h('div', { class: 'muted small' }, o.user.email || ''),
        h('div', { class: 'row wrap', style: { marginTop: '8px', gap: '6px' } },
          (o.user.roles || []).slice(0, 6).map((r) => h('span', { class: 'badge brand plain' }, titleCase(r))))) },
      '-',
      ...(o.menuItems || []),
      { label: 'Sign out', icon: 'logout', onClick: o.onLogout },
    ]));

  mount(root, h('div', { class: 'app' }, sidebar, h('main', { class: 'main' }, topbar, content)));

  return {
    content,
    setActive(id) { navEls.forEach((el, k) => el.classList.toggle('active', k === id)); },
    setCrumbs(...parts) {
      mount(crumbs, parts.flatMap((p, i) => [i ? h('span', {}, '/') : null,
        i === parts.length - 1 ? h('b', {}, p.label || p) : h('a', { href: p.href }, p.label)]));
    },
  };
}

export function initials(s) {
  return (s || '?').split(/[\s@._-]+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase();
}

/** Full-screen loading state while a portal boots. */
export function bootScreen(root, text = 'Loading…') {
  mount(root, h('div', { class: 'center-screen' }, h('div', { class: 'stack', style: { alignItems: 'center' } },
    h('div', { class: 'spinner' }), h('div', { class: 'muted' }, text))));
}
