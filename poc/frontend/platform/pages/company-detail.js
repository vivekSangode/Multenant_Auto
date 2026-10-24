import { h, ic, mount, badge, tabs, table, skeleton, emptyState, copyButton, titleCase, money, date, dateTime, drawer, modal, confirmDialog, busy, toast, num } from '../../shared/ui.js';
import { branding, forgetBranding, STATUS_ACTIONS } from '../lib.js';
import { avatar } from '../../shared/ui.js';
import { orderSummary, paymentPanel, quoteBuilder } from '../components/billing.js';
import { provisionPanel, jobTimeline } from '../components/provisioning.js';
import { accessPanel, portalLink } from '../components/access.js';
import { brandingEditor } from '../components/branding-editor.js';
import { openOnboarding } from '../components/onboarding.js';

const TABS = [
  { id: 'overview', label: 'Overview' }, { id: 'billing', label: 'Orders & payments' }, { id: 'provisioning', label: 'Provisioning' },
  { id: 'resources', label: 'Resources' }, { id: 'access', label: 'Access' }, { id: 'branding', label: 'Branding' }, { id: 'history', label: 'History' },
];

export async function companyDetailPage(ctx, { code }, query) {
  const { shell, api } = ctx;
  shell.setCrumbs({ label: 'Companies', href: '#/companies' }, code);
  mount(shell.content, skeleton(8));
  const [company, overview, brand] = await Promise.all([
    api.get(`/api/v1/platform/companies/${code}`),
    api.get(`/api/v1/platform/companies/${code}/overview`),
    branding(ctx, code),
  ]);
  shell.setCrumbs({ label: 'Companies', href: '#/companies' }, company.displayName);
  let active = query.get('tab') || 'overview';
  const panel = h('div');
  const reload = (tab = active) => ctx.go(`#/companies/${code}?tab=${tab}&r=${Date.now()}`);

  // ------------------------------------------------------------ header
  const realmReady = overview.resources.some((r) => r.type === 'IDP_REALM' && r.status === 'VERIFIED');
  const actions = h('div', { class: 'row wrap' });
  if (company.status === 'DRAFT' || company.status === 'PROVISIONING' || company.status === 'PROVISIONING_FAILED') {
    const cont = h('button', { class: 'btn primary' }, ic('arrowRight'), 'Continue onboarding');
    cont.addEventListener('click', () => busy(cont, async () => {
      const orders = await api.get(`/api/v1/platform/companies/${code}/orders`);
      const open = orders.filter((o) => o.status !== 'CANCELLED').sort((a, b) => b.id - a.id)[0];
      const order = open ? await api.get(`/api/v1/platform/orders/${open.id}`) : null;
      openOnboarding(ctx, { resume: { company, order }, onDone: () => reload() });
    }));
    actions.append(cont);
  }
  if (realmReady) {
    actions.append(h('a', { class: 'btn', href: portalLink(company.loginCode), target: '_blank', rel: 'noopener' }, ic('external'), 'Company portal'));
  }
  for (const [target, label, iconName, danger] of STATUS_ACTIONS[company.status] || []) {
    const b = h('button', { class: `btn ${danger ? '' : 'primary'}` }, ic(iconName), label);
    b.addEventListener('click', async () => {
      const reason = await confirmDialog({
        title: `${label} ${company.displayName}?`, danger, withReason: true, confirmLabel: label,
        message: target === 'SUSPENDED' ? 'Every user of the company is blocked at once in every service. Data is kept.'
          : target === 'ACTIVE' ? 'Users regain access immediately.' : `The company becomes ${titleCase(target)}. Data is kept.`,
      });
      if (reason) busy(b, async () => {
        await api.post(`/api/v1/platform/companies/${code}/status`, { status: target, reason });
        toast(`${company.displayName} is now ${titleCase(target)}`);
        reload();
      });
    });
    actions.append(b);
  }

  const header = h('div', { class: 'card card-pad', style: { marginBottom: '20px' } },
    h('div', { class: 'row wrap', style: { gap: '16px', alignItems: 'flex-start' } },
      avatar(brand?.logoText, brand, ''),
      h('div', { class: 'grow', style: { minWidth: '240px' } },
        h('div', { class: 'row wrap' }, h('h1', {}, company.displayName), badge(company.status)),
        h('div', { class: 'muted', style: { marginTop: '4px' } }, `${company.legalName}${company.statusReason ? ` · ${company.statusReason}` : ''}`),
        h('div', { class: 'row wrap', style: { marginTop: '10px', gap: '6px' } },
          h('span', { class: 'badge plain mono' }, company.companyCode),
          h('span', { class: 'muted small' }, 'Login code'), copyButton(company.loginCode, company.loginCode),
          h('span', { class: 'badge plain mono' }, `realm ${company.realm}`))),
      actions));
  header.querySelector('.avatar').style.cssText += 'width:64px;height:64px;font-size:20px;border-radius:16px';

  if (company.status === 'SUSPENDED') {
    header.append(h('div', { class: 'banner bad mt' }, ic('pause'), h('div', { class: 'small' }, 'Suspended: every request of this company is refused with COMPANY_SUSPENDED until it is reactivated.')));
  }

  const tabBar = h('div');
  const drawTabs = () => mount(tabBar, tabs(TABS, active, (t) => { active = t; drawTabs(); drawPanel(); history.replaceState(null, '', `#/companies/${code}?tab=${t}`); }));
  drawTabs();

  async function drawPanel() {
    mount(panel, skeleton(5));
    const render = { overview: overviewTab, billing: billingTab, provisioning: provisioningTab, resources: resourcesTab, access: accessTab, branding: brandingTab, history: historyTab }[active];
    try { mount(panel, await render()); } catch (e) { mount(panel, h('div', { class: 'banner bad' }, ic('alert'), e.detail || e.message)); }
  }

  // ------------------------------------------------------------ tabs
  async function overviewTab() {
    const ents = overview.entitlements.filter((e) => e.status !== 'ENDED');
    const sub = overview.subscriptions.map((s) => s.subscription).find((s) => s.status === 'ACTIVE') || overview.subscriptions[0]?.subscription;
    const live = overview.runtimeCache || {};
    return h('div', { class: 'grid cols-2', style: { alignItems: 'start' } },
      h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', {}, 'Modules'), h('span', { class: 'muted small' }, `${ents.length} entitlements`)),
        h('div', { class: 'card-body' }, ents.length ? h('div', { class: 'stack' }, ents.map((e) => h('div', { class: 'row' },
          h('div', { class: 'stat-icon', style: { width: '34px', height: '34px' } }, ic('grid')),
          h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, titleCase(e.module)), h('div', { class: 'cell-sub' }, `${titleCase(e.source)}${e.charged ? '' : ' · included'}`)),
          badge(e.status))))
          : emptyState({ icon: 'grid', title: 'No modules yet', text: 'Modules appear once an order is paid.' }))),
      h('div', { class: 'stack' },
        h('div', { class: 'card card-pad' }, h('h2', {}, 'Subscription'), h('div', { class: 'divider' }),
          sub ? h('dl', { class: 'kv' },
            h('dt', {}, 'Status'), h('dd', {}, badge(sub.status)),
            h('dt', {}, 'Billing period'), h('dd', {}, titleCase(sub.billingPeriod)),
            h('dt', {}, 'Valid'), h('dd', {}, `${date(sub.startDate || sub.currentPeriodStart)} → ${date(sub.endDate || sub.currentPeriodEnd)}`),
            ...overview.allowances.flatMap((a) => [h('dt', {}, titleCase(a.allowanceType)), h('dd', {}, num(a.effectiveLimit))]))
            : h('p', { class: 'muted' }, 'No subscription yet.')),
        h('div', { class: 'card card-pad' }, h('div', { class: 'row' }, h('h2', { class: 'grow' }, 'Live access state'), h('span', { class: 'badge info plain' }, 'Redis')),
          h('p', { class: 'muted small', style: { margin: '4px 0 12px' } }, 'What every service checks on each request (fail-closed).'),
          h('dl', { class: 'kv' },
            h('dt', {}, 'Tenant status'), h('dd', {}, live.status ? badge(live.status) : h('span', { class: 'muted' }, 'not published')),
            h('dt', {}, 'Modules'), h('dd', {}, h('div', { class: 'row wrap', style: { gap: '4px' } }, (live.modules || []).map((m) => h('span', { class: 'badge brand plain' }, m))))))));
  }

  async function billingTab() {
    const orders = await api.get(`/api/v1/platform/companies/${code}/orders`);
    const newQuote = h('button', { class: 'btn primary' }, ic('plus'), company.status === 'ACTIVE' ? 'Upgrade: new quote' : 'New quote');
    newQuote.addEventListener('click', () => {
      const m = modal({ title: `New quote · ${company.displayName}`, size: 'xl', icon: 'receipt',
        body: quoteBuilder(ctx, company, { onQuoted: (o) => { m.close(); openOrder(o); } }) });
    });
    const openOrder = async (o) => {
      const view = o.order ? o : await api.get(`/api/v1/platform/orders/${o.id}`);
      const sum = h('div', {}, orderSummary(view));
      drawer({ title: view.order.orderNumber || `Order #${view.order.id}`, subtitle: `${titleCase(view.order.orderKind)} order`,
        body: h('div', { class: 'stack', style: { gap: '20px' } }, sum, h('div', { class: 'divider', style: { margin: 0 } }),
          paymentPanel(ctx, view, { onChange: (v) => { mount(sum, orderSummary(v)); if (v.order.status === 'FULFILLED') toast('Order fulfilled', company.status === 'ACTIVE' ? 'New modules are provisioned automatically (ADD_MODULE job).' : 'You can now provision the company.'); } })) });
    };
    return h('div', { class: 'card' },
      h('div', { class: 'card-head' }, h('h2', {}, 'Orders'), newQuote),
      table({
        rows: [...orders].sort((a, b) => b.id - a.id), onRowClick: openOrder,
        empty: emptyState({ icon: 'receipt', title: 'No orders yet', text: 'Create a quote to sell modules to this company.' }),
        columns: [
          { label: 'Order', render: (o) => h('div', {}, h('div', { class: 'cell-main' }, o.orderNumber || `#${o.id}`), h('div', { class: 'cell-sub' }, `${titleCase(o.orderKind)} · ${titleCase(o.billingPeriod)}`)) },
          { label: 'Status', render: (o) => badge(o.status) },
          { label: 'Payment', render: (o) => badge(o.paymentStatus) },
          { label: 'Created', render: (o) => h('span', { class: 'muted' }, dateTime(o.createdAt)) },
          { label: 'Total', num: true, render: (o) => h('b', {}, money(o.grandTotal)) },
        ],
      }));
  }

  async function provisioningTab() {
    const jobs = await api.get(`/api/v1/platform/companies/${code}/provisioning-jobs`);
    const canStart = company.status === 'DRAFT';
    if (!jobs.length) return provisionPanel(ctx, code, { canStart, onActive: () => reload('provisioning') });
    const older = jobs.slice(0, -1).reverse();
    return h('div', { class: 'stack', style: { gap: '16px' } },
      h('h3', {}, 'Latest job'), provisionPanel(ctx, code, { onActive: () => {} }),
      older.length ? h('h3', {}, 'Earlier jobs') : null,
      older.map((jv) => h('details', { class: 'card card-pad' }, h('summary', { style: { cursor: 'pointer', fontWeight: 600 } },
        `${titleCase(jv.job.jobType)} #${jv.job.id} · ${titleCase(jv.job.status)}`), h('div', { class: 'mt' }, jobTimeline(jv)))));
  }

  async function resourcesTab() {
    const iconFor = { DATABASE: 'server', BUCKET: 'box', IDP_REALM: 'shield' };
    return h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', {}, 'Physical resources'), h('span', { class: 'muted small' }, 'One per company per module: nothing is shared')),
      table({
        rows: overview.resources,
        empty: emptyState({ icon: 'server', title: 'Nothing provisioned yet' }),
        columns: [
          { label: 'Resource', render: (r) => h('div', { class: 'row' }, h('div', { class: 'stat-icon', style: { width: '32px', height: '32px' } }, ic(iconFor[r.type] || 'server')),
            h('div', {}, h('div', { class: 'cell-main mono' }, r.name), h('div', { class: 'cell-sub' }, titleCase(r.type)))) },
          { label: 'Module', render: (r) => r.module ? titleCase(r.module) : h('span', { class: 'muted' }, 'company') },
          { label: 'Schema', render: (r) => r.schemaVersion ? h('span', { class: 'mono small' }, r.schemaVersion) : h('span', { class: 'muted' }, '—') },
          { label: 'Status', render: (r) => badge(r.status) },
        ],
      }));
  }

  async function accessTab() {
    if (!realmReady) return h('div', { class: 'card' }, emptyState({ icon: 'lock', title: 'No realm yet', text: 'The company realm and its Company Super Admin are created during provisioning.' }));
    return accessPanel(ctx, company);
  }

  async function brandingTab() {
    forgetBranding(ctx, code);
    const fresh = await branding(ctx, code);
    return brandingEditor(ctx, company, fresh, { onSaved: () => reload('branding') });
  }

  async function historyTab() {
    const rows = await api.get(`/api/v1/platform/companies/${code}/status-history`);
    return h('div', { class: 'card card-pad' }, h('ol', { class: 'steps' }, [...rows].reverse().map((r) => h('li', {},
      h('div', { class: 'step-dot ok' }, ic('history')),
      h('div', {}, h('div', { class: 'row wrap' }, r.fromStatus ? badge(r.fromStatus) : null, r.fromStatus ? ic('arrowRight') : null, badge(r.toStatus)),
        h('div', { class: 'step-meta', style: { marginTop: '4px' } }, `${r.reason || ''} · by ${r.actor} · ${dateTime(r.changedAt)}`))))));
  }

  mount(shell.content, header, tabBar, panel);
  drawPanel();
}
