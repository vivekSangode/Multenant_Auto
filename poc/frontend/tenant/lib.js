// Helpers shared by the company portal pages.
import { h, ic, mount, emptyState, titleCase } from '../shared/ui.js';

/** Business modules this portal has screens for, keyed by catalog module code. */
export const MODULE_UI = {
  vehicle: { route: '/vehicles', label: 'Vehicles', icon: 'car' },
  inventory: { route: '/inventory', label: 'Inventory', icon: 'box' },
  booking: { route: '/bookings', label: 'Bookings', icon: 'calendar' },
};

/** Friendly full-page states for the backend's access codes. */
export function moduleState(shell, code, module, err) {
  const name = MODULE_UI[module]?.label || titleCase(module || '');
  const states = {
    MODULE_NOT_ENTITLED: ['grid', `${name} is not in your plan`, 'Ask your platform administrator to add this module to your subscription.'],
    MODULE_ACCESS_DENIED: ['lock', `You don't have access to ${name}`, `Ask your company administrator for a ${name} role, such as ${(module || '').toUpperCase()}_USER.`],
    COMPANY_SUSPENDED: ['pause', 'Your company workspace is suspended', 'Your data is safe. Access returns as soon as your platform administrator reactivates the company.'],
    COMPANY_DISABLED: ['lock', 'Your company workspace is disabled', 'Contact your platform administrator.'],
    COMPANY_ARCHIVED: ['box', 'Your company workspace is archived', 'Contact your platform administrator.'],
    COMPANY_NOT_READY: ['server', 'Your workspace is being prepared', err?.detail || 'Try again in a minute.'],
  };
  const [i, title, text] = states[code] || ['alert', titleCase(code), err?.detail || ''];
  shell.setCrumbs(name || 'Workspace');
  mount(shell.content, h('div', { class: 'card' }, emptyState({ icon: i, title, text,
    action: h('a', { class: 'btn', href: '#/' }, ic('arrowLeft'), 'Back to dashboard') })));
}

const COLORS = {
  RED: '#DC2626', BLACK: '#111827', WHITE: '#F9FAFB', SILVER: '#C0C4CC', GREY: '#6B7280', GRAY: '#6B7280', BLUE: '#2563EB',
  GREEN: '#16A34A', PEARL: '#EFE9DC', ORANGE: '#EA580C', YELLOW: '#EAB308', BROWN: '#7C4A21', MAROON: '#7F1D1D', BEIGE: '#D6C7A1',
};
export function colorDot(name) {
  const key = (name || '').toUpperCase();
  return h('span', { class: 'row', style: { gap: '8px' } }, h('span', { class: 'color-dot', style: { background: COLORS[key] || key.toLowerCase() } }), titleCase(key));
}
export const COLOR_SUGGESTIONS = Object.keys(COLORS).filter((c) => c !== 'GRAY');

/** Loads a module's data only if the user may open it; otherwise returns the fallback. */
export async function ifCan(ctx, module, fn, fallback = null) {
  if (!ctx.can(module)) return fallback;
  try { return await fn(); } catch (e) {
    if (['MODULE_ACCESS_DENIED', 'MODULE_NOT_ENTITLED'].includes(e.code)) return fallback;
    throw e;
  }
}
