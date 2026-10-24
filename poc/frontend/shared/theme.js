// Runtime theming: a company's branding (from the control plane) becomes CSS variables, so the same portal
// looks like each tenant's own product. Colour maths stays here; components only use var(--primary) etc.

export const FONTS = {
  MODERN: '"Inter", "Segoe UI", system-ui, -apple-system, Roboto, "Helvetica Neue", Arial, sans-serif',
  CLASSIC: 'Georgia, Cambria, "Times New Roman", "Book Antiqua", serif',
  ROUNDED: '"Nunito", "Segoe UI Rounded", "Trebuchet MS", "Verdana", system-ui, sans-serif',
  TECHNICAL: '"JetBrains Mono", "Cascadia Code", Consolas, "SFMono-Regular", Menlo, monospace',
};

export const PLATFORM_BRANDING = {
  primaryColor: '#4F46E5', accentColor: '#06B6D4', themeMode: 'LIGHT', fontStyle: 'MODERN', logoText: 'NE', tagline: 'Platform console',
};

function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function luminance(hex) {
  const rgb = hexToRgb(hex);
  if (!rgb) return 0;
  const [r, g, b] = rgb.map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Black or white text, whichever reads better on the colour (WCAG contrast). */
export function contrastText(hex) {
  const l = luminance(hex);
  return (1.05) / (l + 0.05) >= (l + 0.05) / 0.05 ? '#FFFFFF' : '#111827';
}

/** CSS variables for a branding; usable on :root or on a preview element. */
export function brandingVars(b) {
  const primary = b?.primaryColor || PLATFORM_BRANDING.primaryColor;
  const accent = b?.accentColor || PLATFORM_BRANDING.accentColor;
  return {
    '--primary': primary,
    '--primary-contrast': contrastText(primary),
    '--accent': accent,
    '--accent-contrast': contrastText(accent),
    '--font': FONTS[b?.fontStyle] || FONTS.MODERN,
  };
}

export function applyVars(el, vars) {
  for (const [k, v] of Object.entries(vars)) el.style.setProperty(k, v);
}

const MODE_KEY = 'erp.themeMode';

/** User's own light/dark choice wins over the company default. */
export function userMode(scope) { try { return localStorage.getItem(`${MODE_KEY}.${scope}`); } catch { return null; } }
export function setUserMode(scope, mode) { try { localStorage.setItem(`${MODE_KEY}.${scope}`, mode); } catch { /* private mode */ } }

export function resolveMode(mode) {
  if (mode === 'DARK') return 'dark';
  if (mode === 'SYSTEM') return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  return 'light';
}

/** Applies a branding to the whole document: colours, font, light/dark, favicon and title. */
export function applyBranding(b, { scope = 'default', title } = {}) {
  applyVars(document.documentElement, brandingVars(b));
  document.documentElement.dataset.theme = resolveMode(userMode(scope) || b?.themeMode || 'LIGHT');
  setFavicon(b);
  if (title) document.title = title;
}

export function setFavicon(b) {
  const text = (b?.logoText || 'E').slice(0, 2);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">`
    + `<stop offset="0" stop-color="${b?.primaryColor || '#4F46E5'}"/><stop offset="1" stop-color="${b?.accentColor || '#06B6D4'}"/></linearGradient></defs>`
    + `<rect width="64" height="64" rx="16" fill="url(#g)"/><text x="32" y="41" font-family="Segoe UI,Arial" font-size="26" font-weight="800" text-anchor="middle" fill="${contrastText(b?.primaryColor || '#4F46E5')}">${text.replace(/[<&>"]/g, '')}</text></svg>`;
  let link = document.querySelector('link[rel="icon"]');
  if (!link) { link = document.createElement('link'); link.rel = 'icon'; document.head.append(link); }
  link.href = `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

export function toggleMode(scope) {
  const next = document.documentElement.dataset.theme === 'dark' ? 'LIGHT' : 'DARK';
  setUserMode(scope, next);
  document.documentElement.dataset.theme = resolveMode(next);
  return next;
}
