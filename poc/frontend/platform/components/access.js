// How the company gets in: login code, portal link, and a one-time temporary password for the Company Super Admin.
import { h, ic, mount, busy, copyButton, toast } from '../../shared/ui.js';

export function portalLink(loginCode) {
  return `${window.location.origin}${window.location.pathname.replace(/platform\/?$/, '')}tenant/#/?code=${encodeURIComponent(loginCode)}`;
}

export function accessPanel(ctx, company) {
  const secret = h('div');
  const issue = h('button', { class: 'btn primary' }, ic('key'), 'Issue temporary password');
  issue.addEventListener('click', () => busy(issue, async () => {
    const r = await ctx.api.post(`/api/v1/platform/companies/${company.companyCode}/super-admin/temporary-password`);
    toast('Temporary password issued', 'Shown once. The user must change it at first sign-in.');
    mount(secret, h('div', { class: 'banner warn' }, ic('key'), h('div', { class: 'grow stack', style: { gap: '6px' } },
      h('div', { class: 'small' }, h('b', {}, 'Shown only once. '), 'Share it securely. Keycloak asks for a new password at the first sign-in.'),
      h('div', { class: 'row wrap' }, h('span', { class: 'muted small' }, 'Username'), copyButton(r.username, r.username)),
      h('div', { class: 'row wrap' }, h('span', { class: 'muted small' }, 'Password'), copyButton(r.temporaryPassword, r.temporaryPassword)))));
  }));
  const link = portalLink(company.loginCode);
  return h('div', { class: 'stack', style: { gap: '16px' } },
    h('div', { class: 'grid cols-2' },
      h('div', { class: 'card card-pad stack', style: { gap: '6px' } },
        h('div', { class: 'muted small' }, 'Company login code'),
        h('div', { class: 'row' }, h('div', { class: 'stat-value mono', style: { letterSpacing: '.12em' } }, company.loginCode), copyButton(company.loginCode)),
        h('div', { class: 'muted small' }, 'Users type this on the company portal to reach their own sign-in page.')),
      h('div', { class: 'card card-pad stack', style: { gap: '6px' } },
        h('div', { class: 'muted small' }, 'Company Super Admin'),
        h('div', { class: 'cell-main', style: { overflowWrap: 'anywhere' } }, company.superAdminEmail),
        h('div', { class: 'muted small' }, `Invited into realm ${company.realm}. Without SMTP in the POC, issue a temporary password instead of the e-mail.`))),
    h('div', { class: 'row wrap' }, issue,
      h('a', { class: 'btn', href: link, target: '_blank', rel: 'noopener' }, ic('external'), 'Open company portal'),
      copyButton(link, 'Copy portal link')),
    secret);
}
