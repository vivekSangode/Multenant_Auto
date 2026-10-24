import { h, ic, mount, stat, table, badge, ago, skeleton, emptyState } from '../../shared/ui.js';
import { companyAvatar } from '../lib.js';
import { openOnboarding } from '../components/onboarding.js';

export async function dashboardPage(ctx) {
  const { shell, api } = ctx;
  shell.setCrumbs('Dashboard');
  mount(shell.content, skeleton(6));
  const companies = await api.get('/api/v1/platform/companies');
  const count = (s) => companies.filter((c) => c.status === s).length;
  const name = ctx.user?.name || '';
  const onboard = h('button', { class: 'btn', style: { background: 'rgba(255,255,255,.95)', color: '#111827', borderColor: 'transparent' } }, ic('plus'), 'Onboard a company');
  onboard.addEventListener('click', () => openOnboarding(ctx, { onDone: () => dashboardPage(ctx) }));
  const recent = [...companies].sort((a, b) => b.id - a.id).slice(0, 6);

  mount(shell.content,
    h('div', { class: 'hero', style: { marginBottom: '22px' } },
      h('div', { class: 'row wrap' }, h('div', { class: 'grow' },
        h('h1', {}, `Welcome back${name ? `, ${name.split(' ')[0]}` : ''}`),
        h('p', {}, `${companies.length} companies on the platform. ${count('ACTIVE')} are live.`)), onboard)),
    h('div', { class: 'grid cols-4' },
      stat({ icon: 'building', label: 'Companies', value: companies.length }),
      stat({ icon: 'check', label: 'Active', value: count('ACTIVE'), tone: 'ok' }),
      stat({ icon: 'server', label: 'Draft or provisioning', value: count('DRAFT') + count('PROVISIONING') + count('PROVISIONING_FAILED'), tone: 'accent' }),
      stat({ icon: 'pause', label: 'Suspended or disabled', value: count('SUSPENDED') + count('DISABLED'), tone: 'bad' })),
    h('div', { class: 'card mt' },
      h('div', { class: 'card-head' }, h('h2', {}, 'Recently onboarded'), h('a', { href: '#/companies', class: 'btn sm ghost' }, 'All companies', ic('arrowRight'))),
      table({
        rows: recent,
        onRowClick: (c) => ctx.go(`#/companies/${c.companyCode}`),
        empty: emptyState({ icon: 'building', title: 'No companies yet', text: 'Onboard the first company: it takes a minute.', action: h('button', { class: 'btn primary', onClick: () => onboard.click() }, ic('plus'), 'Onboard a company') }),
        columns: [
          { label: 'Company', render: (c) => h('div', { class: 'row' }, companyAvatar(ctx, c), h('div', {}, h('div', { class: 'cell-main' }, c.displayName), h('div', { class: 'cell-sub' }, c.legalName))) },
          { label: 'Code', render: (c) => h('span', { class: 'mono' }, c.companyCode) },
          { label: 'Status', render: (c) => badge(c.status) },
          { label: 'Activated', render: (c) => h('span', { class: 'muted' }, c.activatedAt ? ago(c.activatedAt) : '—') },
        ],
      })));
}
