import { h, ic, mount, table, badge, ago, skeleton, emptyState, copyButton, titleCase } from '../../shared/ui.js';
import { companyAvatar } from '../lib.js';
import { openOnboarding } from '../components/onboarding.js';

const FILTERS = ['ALL', 'ACTIVE', 'DRAFT', 'PROVISIONING', 'SUSPENDED', 'DISABLED', 'ARCHIVED'];

export async function companiesPage(ctx, _params, query) {
  const { shell, api } = ctx;
  shell.setCrumbs('Companies');
  mount(shell.content, skeleton(8));
  const companies = await api.get('/api/v1/platform/companies');
  let filter = query.get('status') || 'ALL';
  let text = '';

  const onboard = h('button', { class: 'btn primary' }, ic('plus'), 'Onboard company');
  onboard.addEventListener('click', () => openOnboarding(ctx, { onDone: () => companiesPage(ctx, {}, query) }));
  const search = h('div', { class: 'input-icon', style: { width: '280px' } }, ic('search'),
    h('input', { class: 'input', placeholder: 'Search name, code or login code', 'aria-label': 'Search companies', onInput: (e) => { text = e.target.value.toLowerCase(); draw(); } }));
  const chips = h('div', { class: 'row wrap' });
  const list = h('div', { class: 'card' });

  function draw() {
    mount(chips, FILTERS.map((f) => {
      const n = f === 'ALL' ? companies.length : companies.filter((c) => c.status === f).length;
      return h('button', { class: `chip ${filter === f ? 'on' : ''}`, onClick: () => { filter = f; draw(); } }, titleCase(f), h('span', { class: 'count' }, n));
    }));
    const rows = companies
      .filter((c) => filter === 'ALL' || c.status === filter)
      .filter((c) => !text || [c.displayName, c.legalName, c.companyCode, c.loginCode].some((v) => (v || '').toLowerCase().includes(text)))
      .sort((a, b) => b.id - a.id);
    mount(list, table({
      rows,
      onRowClick: (c) => ctx.go(`#/companies/${c.companyCode}`),
      empty: emptyState({ icon: 'search', title: companies.length ? 'No company matches' : 'No companies yet', text: companies.length ? 'Try another filter or search.' : 'Onboard the first company to get started.' }),
      columns: [
        { label: 'Company', render: (c) => h('div', { class: 'row' }, companyAvatar(ctx, c), h('div', {}, h('div', { class: 'cell-main' }, c.displayName), h('div', { class: 'cell-sub' }, c.superAdminEmail))) },
        { label: 'Code', render: (c) => h('span', { class: 'mono' }, c.companyCode) },
        { label: 'Login code', render: (c) => copyButton(c.loginCode, c.loginCode) },
        { label: 'Realm', render: (c) => h('span', { class: 'mono muted' }, c.realm) },
        { label: 'Status', render: (c) => badge(c.status) },
        { label: 'Activated', render: (c) => h('span', { class: 'muted' }, c.activatedAt ? ago(c.activatedAt) : '—') },
      ],
    }));
  }
  draw();

  mount(shell.content,
    h('div', { class: 'page-head' }, h('div', {}, h('h1', {}, 'Companies'), h('p', {}, 'Every company is a tenant with its own realm, databases and buckets.')), onboard),
    h('div', { class: 'row wrap', style: { marginBottom: '14px', justifyContent: 'space-between' } }, chips, search),
    list);
}
