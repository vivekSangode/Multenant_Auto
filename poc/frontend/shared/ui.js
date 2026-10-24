// Tiny DOM toolkit: elements are built with h(); text always goes through textContent (no HTML injection).
import { icon } from './icons.js';

export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else if (k in el && !k.includes('-') && k !== 'list') el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children.flat(Infinity)) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
}

export function mount(container, ...children) {
  container.replaceChildren();
  append(container, children);
  return container;
}

export const ic = icon;

// ---------------------------------------------------------------- formatting
const INR = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const INR2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
export const money = (n) => (n === null || n === undefined || n === '') ? '—' : (Number(n) % 1 === 0 ? INR : INR2).format(Number(n));
export const num = (n) => new Intl.NumberFormat('en-IN').format(Number(n || 0));
export function date(s) {
  if (!s) return '—';
  const d = new Date(s);
  return isNaN(d) ? s : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
export function dateTime(s) {
  if (!s) return '—';
  const d = new Date(s);
  return isNaN(d) ? s : d.toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}
export function ago(s) {
  if (!s) return '—';
  const sec = Math.round((Date.now() - new Date(s).getTime()) / 1000);
  if (isNaN(sec)) return s;
  if (sec < 60) return 'just now';
  if (sec < 3600) return `${Math.floor(sec / 60)} min ago`;
  if (sec < 86400) return `${Math.floor(sec / 3600)} h ago`;
  return date(s);
}
const ACRONYMS = new Set(['SUV', 'MUV', 'CNG', 'UPI', 'CRM', 'QC', 'VIN', 'ID', 'GST', 'HRMS', 'IDP']);
export const titleCase = (s) => (s || '').toLowerCase().replace(/_/g, ' ').replace(/\b\w+/g, (w) =>
  ACRONYMS.has(w.toUpperCase()) ? w.toUpperCase() : w[0].toUpperCase() + w.slice(1));

// ---------------------------------------------------------------- badges
const TONES = {
  ACTIVE: 'ok', COMPLETED: 'ok', VERIFIED: 'ok', CONFIRMED: 'ok', FULFILLED: 'ok', ISSUED_PAID: 'ok', PAID: 'ok', AVAILABLE: 'ok', DELIVERED: 'ok', BOOKED: 'info',
  PROVISIONING: 'info', RUNNING: 'info', QUEUED: 'info', RETRYING: 'warn', QUOTED: 'info', RESERVED: 'warn', CREATED: 'info',
  PENDING: 'warn', PENDING_VERIFICATION: 'warn', PARTIALLY_PAID: 'warn', DRAFT: 'neutral', AWAITING_DATA_IMPORT: 'warn', UNPAID: 'warn',
  SUSPENDED: 'bad', DISABLED: 'bad', FAILED: 'bad', PROVISIONING_FAILED: 'bad', REJECTED: 'bad', CANCELLED: 'neutral', ARCHIVED: 'neutral',
  SOLD: 'neutral', SKIPPED: 'neutral', ENDED: 'neutral', RETIRED: 'neutral',
};
export function badge(status, label) {
  const tone = TONES[status] || 'neutral';
  const live = ['PROVISIONING', 'RUNNING', 'QUEUED', 'RETRYING'].includes(status);
  return h('span', { class: `badge ${tone}${live ? ' pulse' : ''}` }, label || titleCase(status));
}

// ---------------------------------------------------------------- toasts
let toastBox;
export function toast(title, text = '', tone = 'ok') {
  if (!toastBox) { toastBox = h('div', { class: 'toasts', role: 'status', 'aria-live': 'polite' }); document.body.append(toastBox); }
  const t = h('div', { class: `toast ${tone}` },
    ic(tone === 'bad' ? 'alert' : tone === 'warn' ? 'info' : 'check'),
    h('div', {}, h('div', { class: 't-title' }, title), text ? h('div', { class: 't-text' }, text) : null));
  toastBox.append(t);
  setTimeout(() => { t.style.transition = 'opacity .3s'; t.style.opacity = '0'; setTimeout(() => t.remove(), 300); }, tone === 'bad' ? 7000 : 4000);
}
export function toastError(err, title = 'Something went wrong') {
  toast(err?.code ? `${title}: ${titleCase(err.code)}` : title, err?.detail || err?.message || '', 'bad');
}

// ---------------------------------------------------------------- modal / drawer
export function modal({ title, body, actions = [], size = '', onClose, icon: iconName }) {
  const close = () => { overlay.remove(); document.removeEventListener('keydown', onKey); onClose?.(); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const foot = actions.length ? h('div', { class: 'modal-foot' }, actions.map((a) => {
    const b = h('button', { class: `btn ${a.variant || ''}`, type: a.type || 'button', disabled: a.disabled }, a.icon ? ic(a.icon) : null, a.label);
    b.addEventListener('click', async () => {
      if (!a.onClick) return close();
      b.classList.add('loading');
      try { const keep = await a.onClick(b); if (keep !== true) close(); } catch (e) { toastError(e); } finally { b.classList.remove('loading'); }
    });
    return b;
  })) : null;
  const dialog = h('div', { class: `modal ${size}`, role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
    h('div', { class: 'modal-head' }, iconName ? h('div', { class: 'stat-icon', style: { width: '36px', height: '36px' } }, ic(iconName)) : null,
      h('h2', {}, title), h('button', { class: 'btn ghost icon-only sm', 'aria-label': 'Close', onClick: close }, ic('x'))),
    h('div', { class: 'modal-body' }, body), foot);
  const overlay = h('div', { class: 'overlay', onMousedown: (e) => { if (e.target === overlay) close(); } }, dialog);
  document.body.append(overlay);
  document.addEventListener('keydown', onKey);
  setTimeout(() => dialog.querySelector('input,select,textarea')?.focus(), 30);
  return { close, dialog };
}

export function drawer({ title, subtitle, body, actions }) {
  const close = () => { overlay.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const panel = h('aside', { class: 'drawer', role: 'dialog', 'aria-label': title },
    h('div', { class: 'modal-head' }, h('div', { class: 'grow' }, h('h2', {}, title), subtitle ? h('div', { class: 'muted small' }, subtitle) : null),
      actions || null, h('button', { class: 'btn ghost icon-only sm', 'aria-label': 'Close', onClick: close }, ic('x'))),
    h('div', { class: 'modal-body' }, body));
  const overlay = h('div', { class: 'overlay drawer-overlay', onMousedown: (e) => { if (e.target === overlay) close(); } }, panel);
  document.body.append(overlay);
  document.addEventListener('keydown', onKey);
  return { close, panel };
}

/** Resolves to the reason (or true) when confirmed, null when cancelled. */
export function confirmDialog({ title, message, confirmLabel = 'Confirm', danger = false, withReason = false, reasonLabel = 'Reason' }) {
  return new Promise((resolve) => {
    let done = false;
    const reason = withReason ? h('textarea', { class: 'input', placeholder: 'Visible in the audit trail', rows: 3 }) : null;
    const body = h('div', { class: 'stack' }, h('p', { class: 'muted' }, message),
      reason ? field(reasonLabel, reason) : null);
    modal({
      title, body, icon: danger ? 'alert' : 'info',
      onClose: () => { if (!done) resolve(null); },
      actions: [
        { label: 'Cancel' },
        { label: confirmLabel, variant: danger ? 'danger' : 'primary', onClick: () => {
          if (reason && !reason.value.trim()) { reason.classList.add('invalid'); reason.focus(); return true; }
          done = true; resolve(reason ? reason.value.trim() : true);
        } },
      ],
    });
  });
}

// ---------------------------------------------------------------- forms
export function field(label, control, hint, attrs = {}) {
  const id = control.id || `f-${Math.random().toString(36).slice(2, 9)}`;
  control.id = id;
  return h('div', { class: `field ${attrs.class || ''}` }, h('label', { for: id }, label), control,
    hint ? h('div', { class: 'hint' }, hint) : null);
}
export const input = (attrs = {}) => h('input', { class: 'input', ...attrs });
export function select(options, attrs = {}) {
  return h('select', { class: 'input', ...attrs },
    options.map((o) => typeof o === 'string' ? h('option', { value: o }, titleCase(o)) : h('option', { value: o.value, disabled: o.disabled }, o.label)));
}
export function segmented(options, value, onChange) {
  const wrap = h('div', { class: 'segmented', role: 'tablist' });
  const render = (v) => mount(wrap, options.map((o) => h('button', {
    type: 'button', class: o.value === v ? 'on' : '', role: 'tab', 'aria-selected': String(o.value === v),
    onClick: () => { render(o.value); onChange(o.value); },
  }, o.label)));
  render(value);
  return wrap;
}

/** Runs an async action with a spinner on the button and an error toast on failure. */
export async function busy(button, fn) {
  button?.classList.add('loading');
  try { return await fn(); } catch (e) { toastError(e); throw e; } finally { button?.classList.remove('loading'); }
}

// ---------------------------------------------------------------- building blocks
export function table({ columns, rows, onRowClick, empty }) {
  if (!rows.length) return empty || emptyState({ icon: 'search', title: 'Nothing here yet' });
  return h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
    h('thead', {}, h('tr', {}, columns.map((c) => h('th', { class: c.num ? 'num' : '' }, c.label)))),
    h('tbody', {}, rows.map((r) => h('tr', {
      class: onRowClick ? 'clickable' : '', tabIndex: onRowClick ? 0 : undefined,
      onClick: onRowClick ? () => onRowClick(r) : undefined,
      onKeydown: onRowClick ? (e) => { if (e.key === 'Enter') onRowClick(r); } : undefined,
    }, columns.map((c) => h('td', { class: c.num ? 'num' : '' }, c.render ? c.render(r) : r[c.key])))))));
}

export function emptyState({ icon: name = 'sparkles', title, text, action }) {
  return h('div', { class: 'empty' }, h('div', { class: 'empty-icon' }, ic(name)), h('h3', {}, title), text ? h('p', {}, text) : null, action || null);
}

export function skeleton(lines = 4) {
  return h('div', { class: 'card card-pad' }, Array.from({ length: lines }, (_, i) =>
    h('div', { class: 'skeleton sk-line', style: { width: `${90 - (i % 3) * 18}%` } })));
}

export function stat({ icon: name, label, value, tone = '' }) {
  return h('div', { class: 'card stat' }, h('div', { class: `stat-icon ${tone}` }, ic(name)),
    h('div', {}, h('div', { class: 'stat-value' }, value), h('div', { class: 'stat-label' }, label)));
}

export function tabs(items, active, onChange) {
  return h('div', { class: 'tabs', role: 'tablist' }, items.map((t) => h('button', {
    class: `tab ${t.id === active ? 'on' : ''}`, role: 'tab', 'aria-selected': String(t.id === active), onClick: () => onChange(t.id),
  }, t.label)));
}

export function copyButton(text, label) {
  const b = h('button', { class: 'btn sm ghost copy', title: 'Copy', type: 'button' }, label ? h('span', { class: 'mono' }, label) : null, ic('copy'));
  b.addEventListener('click', async (e) => {
    e.stopPropagation();
    try { await navigator.clipboard.writeText(text); toast('Copied', text); } catch { toast('Copy failed', text, 'warn'); }
  });
  return b;
}

export function avatar(text, branding, cls = '') {
  const style = { background: `linear-gradient(135deg, ${branding?.primaryColor || '#4F46E5'}, ${branding?.accentColor || '#F59E0B'})` };
  if (branding?.logoUrl) return h('div', { class: `avatar ${cls}`, style }, h('img', { src: branding.logoUrl, alt: '' }));
  return h('div', { class: `avatar ${cls}`, style }, (text || '?').slice(0, 3).toUpperCase());
}

export function menu(trigger, items) {
  const wrap = h('div', { class: 'menu-wrap' }, trigger);
  let open = null;
  const closeMenu = () => {
    open?.remove(); open = null;
    document.removeEventListener('mousedown', outside); document.removeEventListener('keydown', onKey);
  };
  const outside = (e) => { if (!wrap.contains(e.target)) closeMenu(); };
  const onKey = (e) => { if (e.key === 'Escape') { closeMenu(); trigger.focus(); } };
  trigger.addEventListener('click', () => {
    if (open) return closeMenu();
    open = h('div', { class: 'menu', role: 'menu' }, items().map((it) => it === '-' ? h('div', { class: 'menu-sep' }) :
      it.node ? it.node : h('button', { class: 'menu-item', role: 'menuitem', onClick: () => { closeMenu(); it.onClick(); } }, it.icon ? ic(it.icon) : null, it.label)));
    wrap.append(open);
    document.addEventListener('mousedown', outside);
    document.addEventListener('keydown', onKey);
  });
  return wrap;
}
