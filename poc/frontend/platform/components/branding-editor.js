// White-label editor with a live preview of the company portal in that branding (light or dark).
import { h, ic, mount, field, input, select, segmented, busy, toast } from '../../shared/ui.js';
import { brandingVars, applyVars, resolveMode, contrastText } from '../../shared/theme.js';
import { carArt } from '../../shared/icons.js';
import { forgetBranding } from '../lib.js';

const PRESETS = [
  ['Indigo', '#4F46E5', '#F59E0B'], ['Racing red', '#B91C1C', '#0EA5E9'], ['Ocean', '#0369A1', '#22D3EE'],
  ['Forest', '#15803D', '#EAB308'], ['Graphite', '#334155', '#F97316'], ['Royal', '#7C3AED', '#EC4899'],
  ['Teal', '#0F766E', '#F97316'], ['Midnight', '#1E3A8A', '#FBBF24'],
];

export function brandingEditor(ctx, company, initial, { onSaved } = {}) {
  const b = { ...initial };
  const preview = h('div');

  const colorPair = (key, label) => {
    const text = input({ value: b[key], maxLength: 7, class: 'input mono', style: { maxWidth: '120px' } });
    const picker = h('input', { type: 'color', class: 'swatch-input', value: b[key] });
    picker.addEventListener('input', () => { b[key] = picker.value.toUpperCase(); text.value = b[key]; renderPreview(); });
    text.addEventListener('input', () => { if (/^#[0-9a-f]{6}$/i.test(text.value)) { b[key] = text.value.toUpperCase(); picker.value = b[key]; text.classList.remove('invalid'); renderPreview(); } else text.classList.add('invalid'); });
    const wrap = h('div', { class: 'row' }, picker, text);
    wrap.sync = () => { picker.value = b[key]; text.value = b[key]; };
    return { el: field(label, wrap), sync: wrap.sync };
  };
  const primary = colorPair('primaryColor', 'Primary colour');
  const accent = colorPair('accentColor', 'Accent colour');

  const presets = h('div', { class: 'row wrap', style: { gap: '8px' } }, PRESETS.map(([name, p, a]) => h('button', {
    type: 'button', class: 'chip', title: name,
    onClick: () => { b.primaryColor = p; b.accentColor = a; primary.sync(); accent.sync(); renderPreview(); },
  }, h('span', { class: 'color-dot', style: { background: p } }), h('span', { class: 'color-dot', style: { background: a, marginLeft: '-10px' } }), name)));

  const logoText = input({ value: b.logoText, maxLength: 3, style: { textTransform: 'uppercase', maxWidth: '120px' }, onInput: (e) => { b.logoText = e.target.value.toUpperCase(); renderPreview(); } });
  const logoUrl = input({ value: b.logoUrl || '', placeholder: 'https://… (optional)', onInput: (e) => { b.logoUrl = e.target.value; renderPreview(); } });
  const tagline = input({ value: b.tagline || '', maxLength: 120, placeholder: 'e.g. Drive the future', onInput: (e) => { b.tagline = e.target.value; renderPreview(); } });
  const font = select([{ value: 'MODERN', label: 'Modern (sans-serif)' }, { value: 'CLASSIC', label: 'Classic (serif)' }, { value: 'ROUNDED', label: 'Rounded' }, { value: 'TECHNICAL', label: 'Technical (mono)' }],
    { value: b.fontStyle, onChange: (e) => { b.fontStyle = e.target.value; renderPreview(); } });
  const mode = segmented([{ value: 'LIGHT', label: 'Light' }, { value: 'DARK', label: 'Dark' }, { value: 'SYSTEM', label: 'System' }], b.themeMode, (v) => { b.themeMode = v; renderPreview(); });

  const save = h('button', { class: 'btn primary' }, ic('check'), 'Save branding');
  save.addEventListener('click', () => busy(save, async () => {
    const saved = await ctx.api.put(`/api/v1/platform/companies/${company.companyCode}/branding`, {
      primaryColor: b.primaryColor, accentColor: b.accentColor, themeMode: b.themeMode, fontStyle: b.fontStyle,
      logoText: b.logoText, logoUrl: b.logoUrl || null, tagline: b.tagline || null,
    });
    forgetBranding(ctx, company.companyCode);
    toast('Branding saved', `${company.displayName}'s portal uses it from the next page load.`);
    onSaved?.(saved);
  }));

  function renderPreview() {
    const frame = miniPortal(company, b);
    mount(preview, frame);
  }
  renderPreview();

  return h('div', { class: 'grid side' },
    h('div', { class: 'card card-pad stack', style: { gap: '16px' } },
      h('div', { class: 'stack', style: { gap: '8px' } }, h('h3', {}, 'Presets'), presets),
      h('div', { class: 'divider', style: { margin: 0 } }),
      primary.el, accent.el,
      field('Default appearance', mode, 'Users can still switch light / dark for themselves.'),
      field('Typography', font),
      field('Logo text', logoText, 'Up to 3 letters, shown when there is no logo image.'),
      field('Logo image URL', logoUrl),
      field('Tagline', tagline),
      h('div', { class: 'row' }, initial.customized ? null : h('span', { class: 'badge plain' }, 'Using default palette'), h('div', { class: 'grow' }), save)),
    h('div', { class: 'stack', style: { position: 'sticky', top: '84px' } },
      h('div', { class: 'row' }, h('h3', { class: 'grow' }, 'Live preview'), h('span', { class: 'muted small' }, 'What their users see')),
      preview));
}

/** A scaled-down company portal rendered with the branding: sidebar, hero, KPIs, a model card and buttons. */
export function miniPortal(company, b) {
  const dark = resolveMode(b.themeMode) === 'dark';
  const frame = h('div', { class: `preview-frame theme-scope ${dark ? 'theme-dark' : 'theme-light'}`, style: { fontSize: '13px' } });
  applyVars(frame, brandingVars(b));
  const logo = h('div', { class: 'brand-logo', style: { width: '34px', height: '34px', fontSize: '13px' } },
    b.logoUrl ? h('img', { src: b.logoUrl, alt: '' }) : (b.logoText || '').slice(0, 3));
  const nav = (label, iconName, on) => h('div', { class: `nav-item ${on ? 'active' : ''}`, style: { padding: '7px 9px' } }, ic(iconName), label);
  const kpi = (label, value, iconName, tone) => h('div', { class: 'card stat', style: { padding: '12px' } },
    h('div', { class: `stat-icon ${tone}`, style: { width: '34px', height: '34px' } }, ic(iconName)),
    h('div', {}, h('div', { class: 'stat-value', style: { fontSize: '18px' } }, value), h('div', { class: 'stat-label' }, label)));
  mount(frame, h('div', { style: { display: 'grid', gridTemplateColumns: '170px 1fr', minHeight: '380px' } },
    h('div', { class: 'sidebar', style: { position: 'static', height: 'auto', padding: '14px 10px' } },
      h('div', { class: 'brand', style: { paddingBottom: '12px' } }, logo, h('div', {}, h('div', { class: 'brand-name', style: { fontSize: '13px' } }, company.displayName), h('div', { class: 'brand-sub' }, 'Company portal'))),
      nav('Dashboard', 'dashboard', true), nav('Vehicles', 'car'), nav('Inventory', 'box'), nav('Bookings', 'calendar')),
    h('div', { style: { padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px', background: 'var(--bg)' } },
      h('div', { class: 'hero', style: { padding: '16px 18px' } }, h('div', { style: { fontWeight: 750, fontSize: '17px' } }, `Welcome to ${company.displayName}`),
        h('p', { style: { fontSize: '12.5px' } }, b.tagline || 'Your dealership at a glance')),
      h('div', { class: 'grid cols-3', style: { gap: '10px' } }, kpi('Models', '12', 'car', ''), kpi('In stock', '48', 'box', 'ok'), kpi('Bookings', '9', 'calendar', 'accent')),
      h('div', { class: 'card', style: { padding: '12px', display: 'flex', gap: '12px', alignItems: 'center' } },
        h('div', { class: 'model-visual', style: { height: '54px', width: '90px' } }, carArt('SUV')),
        h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, 'Sample SUV'), h('div', { class: 'cell-sub' }, 'Diesel · SUV')),
        h('span', { class: 'badge ok' }, 'Available')),
      h('div', { class: 'row' }, h('span', { class: 'btn primary sm' }, ic('plus'), 'New booking'), h('span', { class: 'btn accent sm' }, 'Accent'), h('span', { class: 'btn sm' }, 'Secondary'),
        h('span', { class: 'small muted right' }, `Text on primary: ${contrastText(b.primaryColor) === '#FFFFFF' ? 'white' : 'dark'}`)))));
  return frame;
}
