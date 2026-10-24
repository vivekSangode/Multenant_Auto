import { h, ic, mount, stat, table, badge, money, ago, skeleton, emptyState, titleCase } from '../../shared/ui.js';
import { ifCan, MODULE_UI, colorDot } from '../lib.js';
import { openNewBooking } from './bookings.js';
import { openReceiveCar } from './inventory.js';

/** For users who can see the catalog but not stock or bookings. */
function catalogCard(models) {
  return h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', {}, 'Vehicle catalog'), h('a', { class: 'btn sm ghost', href: '#/vehicles' }, 'Browse', ic('arrowRight'))),
    table({
      rows: [...models].sort((a, b) => a.basePrice - b.basePrice),
      empty: emptyState({ icon: 'car', title: 'No models yet' }),
      columns: [
        { label: 'Model', render: (m) => h('div', {}, h('div', { class: 'cell-main' }, m.name), h('div', { class: 'cell-sub mono' }, m.modelCode)) },
        { label: 'Type', render: (m) => `${titleCase(m.bodyType)} · ${titleCase(m.fuelType)}` },
        { label: 'Price', num: true, render: (m) => h('b', {}, money(m.basePrice)) },
      ],
    }));
}

export async function dashboardPage(ctx) {
  const { shell, svc, me } = ctx;
  shell.setCrumbs('Dashboard');
  mount(shell.content, skeleton(6));
  const [models, units, bookings] = await Promise.all([
    ifCan(ctx, 'vehicle', () => svc.vehicle.get('/api/v1/vehicle/models'), null),
    ifCan(ctx, 'inventory', () => svc.inventory.get('/api/v1/inventory/units'), null),
    ifCan(ctx, 'booking', () => svc.booking.get('/api/v1/booking/bookings'), null),
  ]);
  const by = (list, s) => (list || []).filter((x) => x.status === s).length;
  const hour = new Date().getHours();
  const hello = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const first = (me.user.name || me.user.username || '').split(/[\s@]/)[0];

  const quick = h('div', { class: 'row wrap' },
    ctx.can('booking') ? h('button', { class: 'btn', style: { background: 'rgba(255,255,255,.95)', color: '#111827', borderColor: 'transparent' }, onClick: () => openNewBooking(ctx, () => dashboardPage(ctx)) }, ic('plus'), 'New booking') : null,
    ctx.can('inventory') ? h('button', { class: 'btn', style: { background: 'rgba(255,255,255,.18)', color: 'inherit', borderColor: 'rgba(255,255,255,.35)' }, onClick: () => openReceiveCar(ctx, () => dashboardPage(ctx)) }, ic('box'), 'Receive a car') : null);

  const kpis = [];
  if (models) kpis.push(stat({ icon: 'car', label: 'Models in catalog', value: models.length }));
  if (units) kpis.push(stat({ icon: 'box', label: 'Cars available', value: by(units, 'AVAILABLE'), tone: 'ok' }), stat({ icon: 'lock', label: 'Reserved', value: by(units, 'RESERVED'), tone: 'warn' }));
  if (bookings) kpis.push(stat({ icon: 'calendar', label: 'Open bookings', value: by(bookings, 'BOOKED'), tone: 'accent' }), stat({ icon: 'check', label: 'Delivered', value: by(bookings, 'DELIVERED'), tone: 'ok' }));

  // stock by model: stacked bar of available / reserved / sold
  const stock = units ? Object.values(units.reduce((acc, u) => {
    const r = acc[u.modelCode] ||= { model: u.modelCode, AVAILABLE: 0, RESERVED: 0, SOLD: 0, total: 0 };
    r[u.status] += 1; r.total += 1; return acc;
  }, {})).sort((a, b) => b.total - a.total) : null;
  const maxTotal = Math.max(1, ...(stock || []).map((s) => s.total));
  const modelName = (code) => models?.find((m) => m.modelCode === code)?.name || code;
  const stockCard = stock ? h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', {}, 'Stock by model'),
    h('div', { class: 'legend' }, h('span', {}, h('i', { style: { background: 'var(--ok)' } }), 'Available'), h('span', {}, h('i', { style: { background: 'var(--warn)' } }), 'Reserved'), h('span', {}, h('i', { style: { background: 'var(--neutral)' } }), 'Sold'))),
    h('div', { class: 'card-body' }, stock.length ? stock.map((s) => h('div', { class: 'bar-row' },
      h('div', { class: 'small', style: { fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }, title: modelName(s.model) }, modelName(s.model)),
      h('div', { class: 'bar-track', style: { width: `${(s.total / maxTotal) * 100}%` } },
        h('span', { style: { width: `${(s.AVAILABLE / s.total) * 100}%`, background: 'var(--ok)' } }),
        h('span', { style: { width: `${(s.RESERVED / s.total) * 100}%`, background: 'var(--warn)' } }),
        h('span', { style: { width: `${(s.SOLD / s.total) * 100}%`, background: 'var(--neutral)' } })),
      h('div', { class: 'small muted num' }, s.total)))
      : emptyState({ icon: 'box', title: 'No cars in stock yet', text: 'Receive your first car to see stock here.' }))) : null;

  const recent = bookings ? h('div', { class: 'card' }, h('div', { class: 'card-head' }, h('h2', {}, 'Recent bookings'), h('a', { class: 'btn sm ghost', href: '#/bookings' }, 'All bookings', ic('arrowRight'))),
    table({
      rows: [...bookings].sort((a, b) => b.id - a.id).slice(0, 5),
      empty: emptyState({ icon: 'calendar', title: 'No bookings yet', text: 'Bookings you create appear here.' }),
      columns: [
        { label: 'Customer', render: (b) => h('div', {}, h('div', { class: 'cell-main' }, b.customerName), h('div', { class: 'cell-sub mono' }, b.bookingNo)) },
        { label: 'Car', render: (b) => h('div', {}, h('div', {}, modelName(b.modelCode)), h('div', { class: 'cell-sub' }, colorDot(b.color))) },
        { label: 'Status', render: (b) => badge(b.status) },
        { label: 'Price', num: true, render: (b) => money(b.price) },
        { label: '', render: (b) => h('span', { class: 'muted small' }, ago(b.createdAt)) },
      ],
    })) : null;

  const plan = h('div', { class: 'card card-pad stack' }, h('h2', {}, 'Your plan'),
    h('div', { class: 'stack', style: { gap: '8px' } }, me.modules.map((m) => h('div', { class: 'row' },
      h('div', { class: `stat-icon ${m.accessible ? '' : 'warn'}`, style: { width: '32px', height: '32px' } }, ic(MODULE_UI[m.code]?.icon || 'grid')),
      h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, m.name), h('div', { class: 'cell-sub' }, m.category)),
      m.accessible ? (MODULE_UI[m.code] ? h('a', { class: 'btn sm ghost', href: `#${MODULE_UI[m.code].route}` }, 'Open') : h('span', { class: 'badge plain' }, 'No screen in this demo'))
        : h('span', { class: 'badge warn plain', title: `No ${m.gatewayToken} role` }, ic('lock'), 'No access')))),
    h('div', { class: 'divider', style: { margin: '4px 0' } }),
    h('div', { class: 'small muted' }, 'Your roles'),
    h('div', { class: 'row wrap', style: { gap: '6px' } }, me.user.roles.map((r) => h('span', { class: 'badge brand plain' }, titleCase(r)))));

  mount(shell.content,
    h('div', { class: 'hero', style: { marginBottom: '22px' } }, h('div', { class: 'row wrap', style: { position: 'relative', zIndex: 1 } },
      h('div', { class: 'grow' }, h('h1', {}, `${hello}${first ? `, ${first}` : ''}`), h('p', {}, me.branding.tagline || `Here is what is happening at ${me.displayName} today.`)), quick)),
    kpis.length ? h('div', { class: 'grid kpis', style: { marginBottom: '16px' } }, kpis) : null,
    h('div', { class: 'grid split' },
      h('div', { class: 'stack', style: { gap: '16px' } }, stockCard, recent,
        !stockCard && !recent && models ? catalogCard(models) : null,
        !stockCard && !recent && !models ? h('div', { class: 'card' }, emptyState({ icon: 'lock', title: 'Nothing to show yet', text: 'Your roles do not include any dealership module. Ask your company administrator for access.' })) : null),
      plan));
}
