import { h, ic, mount, money, titleCase, table, segmented, skeleton } from '../../shared/ui.js';
import { catalog } from '../lib.js';

export async function catalogPage(ctx) {
  const { shell } = ctx;
  shell.setCrumbs('Catalog & pricing');
  let period = 'MONTHLY';
  const body = h('div', { class: 'stack', style: { gap: '20px' } });

  async function draw() {
    mount(body, skeleton(6));
    const cat = await catalog(ctx, period);
    const price = (k) => cat.prices[k]?.unitPrice;
    const modules = Object.values(cat.modules).filter((m) => m.active);
    const deps = (code) => cat.dependencies.filter((d) => d.module === code);
    const seat = cat.prices.USER_SEAT; const store = cat.prices.STORAGE;
    const per = period === 'YEARLY' ? '/ year' : '/ month';

    mount(body,
      h('div', { class: 'grid cols-3' }, Object.values(cat.bundles).filter((b) => b.active).map((b) => {
        const sum = [...b.moduleCodes].reduce((s, m) => s + (price(`MODULE:${m}`) || 0), 0);
        const p = price(`BUNDLE:${b.code}`);
        return h('div', { class: 'card card-pad stack' },
          h('div', { class: 'row' }, h('div', { class: 'stat-icon accent', style: { width: '36px', height: '36px' } }, ic('layers')), h('h3', { class: 'grow' }, b.name),
            sum > p ? h('span', { class: 'badge ok plain' }, `save ${money(sum - p)}`) : null),
          h('div', { class: 'row wrap', style: { gap: '4px' } }, [...b.moduleCodes].map((m) => h('span', { class: 'badge plain' }, cat.modules[m]?.name || m))),
          h('div', {}, h('span', { class: 'price-tag' }, money(p)), h('span', { class: 'muted small' }, ` ${per}`)));
      })),
      h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', {}, 'Modules'),
        h('span', { class: 'muted small' }, `Users: ${seat?.includedUsers} included, then ${money(seat?.unitPrice)} each · Storage: ${store?.includedStorageGb} GB included, then ${money(store?.unitPrice)} per ${store?.unitQuantity} GB`)),
        table({
          rows: modules.sort((a, b) => a.type.localeCompare(b.type) || a.name.localeCompare(b.name)),
          columns: [
            { label: 'Module', render: (m) => h('div', {}, h('div', { class: 'cell-main' }, m.name), h('div', { class: 'cell-sub mono' }, m.code)) },
            { label: 'Type', render: (m) => h('span', { class: `badge plain ${m.type === 'PLATFORM_CORE' ? 'info' : m.type === 'ADDON' ? 'warn' : 'brand'}` }, titleCase(m.type)) },
            { label: 'Requires', render: (m) => deps(m.code).length ? h('div', { class: 'stack', style: { gap: '2px' } }, deps(m.code).map((d) =>
              h('span', { class: 'small' }, `${cat.modules[d.dependsOn]?.name || d.dependsOn} `, h('span', { class: 'muted' }, `(${titleCase(d.type)})`)))) : h('span', { class: 'muted' }, '—') },
            { label: 'Database', render: (m) => m.dbToken ? h('span', { class: 'mono small' }, `cmp_<n>_${m.dbToken}_db`) : h('span', { class: 'muted' }, 'none') },
            { label: `Price ${per}`, num: true, render: (m) => m.type === 'PLATFORM_CORE' ? h('span', { class: 'muted' }, 'included') : h('b', {}, money(price(`MODULE:${m.code}`))) },
          ],
        })));
  }

  mount(shell.content,
    h('div', { class: 'page-head' }, h('div', {}, h('h1', {}, 'Catalog & pricing'), h('p', {}, 'Illustrative prices (master §12.3). Quotes always use the server-side pricing engine.')),
      segmented([{ value: 'MONTHLY', label: 'Monthly' }, { value: 'YEARLY', label: 'Yearly' }], period, (v) => { period = v; draw(); })),
    body);
  await draw();
}
