// Small data helpers shared by the console pages (cached per console session).
import { avatar } from '../shared/ui.js';

export async function branding(ctx, code) {
  if (!ctx.cache.branding.has(code)) {
    ctx.cache.branding.set(code, ctx.api.get(`/api/v1/platform/companies/${code}/branding`).catch(() => null));
  }
  return ctx.cache.branding.get(code);
}

export function forgetBranding(ctx, code) { ctx.cache.branding.delete(code); }

export async function catalog(ctx, period = 'MONTHLY') {
  if (!ctx.cache.catalog.has(period)) {
    ctx.cache.catalog.set(period, ctx.api.get(`/api/v1/platform/catalog?period=${period}`));
  }
  return ctx.cache.catalog.get(period);
}

/** Avatar that fills in with the company's branding as soon as it is loaded. */
export function companyAvatar(ctx, company) {
  const el = avatar(company.displayName?.split(/\s+/).map((w) => w[0]).slice(0, 2).join(''), null);
  branding(ctx, company.companyCode).then((b) => {
    if (!b) return;
    const fresh = avatar(b.logoText, b);
    el.replaceWith(fresh);
  });
  return el;
}

export const PAYMENT_MODES = [
  { value: 'UPI', label: 'UPI (instant)', needsRef: false },
  { value: 'CASH', label: 'Cash (instant)', needsRef: false },
  { value: 'CHEQUE', label: 'Cheque (needs clearance)', needsRef: true },
  { value: 'DEMAND_DRAFT', label: 'Demand draft (needs clearance)', needsRef: true },
  { value: 'BANK_TRANSFER', label: 'Bank transfer (needs verification)', needsRef: true },
];

export const STATUS_ACTIONS = {
  ACTIVE: [['SUSPENDED', 'Suspend', 'pause', true], ['DISABLED', 'Disable', 'lock', true], ['ARCHIVED', 'Archive', 'box', true]],
  SUSPENDED: [['ACTIVE', 'Reactivate', 'play', false], ['DISABLED', 'Disable', 'lock', true], ['ARCHIVED', 'Archive', 'box', true]],
  DISABLED: [['ACTIVE', 'Reactivate', 'play', false], ['ARCHIVED', 'Archive', 'box', true]],
  ARCHIVED: [['ACTIVE', 'Restore', 'play', false]],
};
