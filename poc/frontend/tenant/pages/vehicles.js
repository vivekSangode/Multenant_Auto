import { h, ic, mount, money, titleCase, skeleton, emptyState, modal, field, input, select, toast, drawer, table, badge, date } from '../../shared/ui.js';
import { carArt } from '../../shared/icons.js';
import { ifCan, colorDot } from '../lib.js';

const BODY = ['HATCHBACK', 'SEDAN', 'SUV', 'MUV', 'PICKUP'];
const FUEL = ['PETROL', 'DIESEL', 'CNG', 'HYBRID', 'ELECTRIC'];

export async function vehiclesPage(ctx) {
  const { shell, svc } = ctx;
  shell.setCrumbs('Vehicles');
  mount(shell.content, skeleton(6));
  const [models, units] = await Promise.all([
    svc.vehicle.get('/api/v1/vehicle/models'),
    ifCan(ctx, 'inventory', () => svc.inventory.get('/api/v1/inventory/units'), null),
  ]);
  let body = 'ALL';
  let text = '';
  const grid = h('div');
  const chips = h('div', { class: 'row wrap' });
  const available = (code) => (units || []).filter((u) => u.modelCode === code && u.status === 'AVAILABLE').length;

  function draw() {
    mount(chips, ['ALL', ...BODY].filter((b) => b === 'ALL' || models.some((m) => m.bodyType === b)).map((b) =>
      h('button', { class: `chip ${body === b ? 'on' : ''}`, onClick: () => { body = b; draw(); } }, titleCase(b),
        h('span', { class: 'count' }, b === 'ALL' ? models.length : models.filter((m) => m.bodyType === b).length))));
    const rows = models.filter((m) => body === 'ALL' || m.bodyType === body)
      .filter((m) => !text || `${m.name} ${m.modelCode}`.toLowerCase().includes(text));
    mount(grid, rows.length ? h('div', { class: 'grid auto' }, rows.map((m) => {
      const n = available(m.modelCode);
      return h('div', { class: 'card hover model-card', tabIndex: 0, onClick: () => openModel(ctx, m, units), onKeydown: (e) => { if (e.key === 'Enter') openModel(ctx, m, units); } },
        h('div', { class: 'model-visual' }, carArt(m.bodyType)),
        h('div', { class: 'row' }, h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, m.name), h('div', { class: 'cell-sub mono' }, m.modelCode)),
          units ? h('span', { class: `badge ${n ? 'ok' : 'neutral'}` }, n ? `${n} in stock` : 'Out of stock') : null),
        h('div', { class: 'row wrap', style: { gap: '6px' } }, h('span', { class: 'badge plain brand' }, titleCase(m.bodyType)), h('span', { class: 'badge plain' }, titleCase(m.fuelType))),
        h('div', { class: 'price-tag' }, money(m.basePrice)));
    })) : h('div', { class: 'card' }, emptyState({ icon: 'car', title: models.length ? 'No model matches' : 'Your catalog is empty',
      text: models.length ? 'Try another filter.' : 'Add the first car model your dealership sells.',
      action: models.length ? null : addBtn() })));
  }

  const addBtn = () => {
    const b = h('button', { class: 'btn primary' }, ic('plus'), 'Add model');
    b.addEventListener('click', () => openAddModel(ctx, () => vehiclesPage(ctx)));
    return b;
  };
  const search = h('div', { class: 'input-icon', style: { width: '260px' } }, ic('search'),
    h('input', { class: 'input', placeholder: 'Search models', 'aria-label': 'Search models', onInput: (e) => { text = e.target.value.toLowerCase(); draw(); } }));
  draw();
  mount(shell.content,
    h('div', { class: 'page-head' }, h('div', {}, h('h1', {}, 'Vehicles'), h('p', {}, `The models ${ctx.me.displayName} sells. Prices here are used for every booking.`)), addBtn()),
    h('div', { class: 'row wrap', style: { justifyContent: 'space-between', marginBottom: '16px' } }, chips, search),
    grid);
}

function openModel(ctx, m, units) {
  const stock = (units || []).filter((u) => u.modelCode === m.modelCode);
  drawer({
    title: m.name, subtitle: m.modelCode,
    body: h('div', { class: 'stack', style: { gap: '18px' } },
      h('div', { class: 'model-visual', style: { height: '150px' } }, carArt(m.bodyType)),
      h('dl', { class: 'kv' },
        h('dt', {}, 'Base price'), h('dd', {}, h('span', { class: 'price-tag' }, money(m.basePrice))),
        h('dt', {}, 'Body'), h('dd', {}, titleCase(m.bodyType)),
        h('dt', {}, 'Fuel'), h('dd', {}, titleCase(m.fuelType)),
        h('dt', {}, 'Added'), h('dd', {}, `${date(m.createdAt)} by ${m.createdBy || '—'}`)),
      units ? h('div', { class: 'stack' }, h('h3', {}, `Stock (${stock.length})`), h('div', { class: 'card' }, table({
        rows: stock,
        empty: emptyState({ icon: 'box', title: 'No cars of this model in stock' }),
        columns: [
          { label: 'VIN', render: (u) => h('span', { class: 'mono' }, u.vin) },
          { label: 'Colour', render: (u) => colorDot(u.color) },
          { label: 'Status', render: (u) => badge(u.status) },
        ],
      }))) : null),
  });
}

export function openAddModel(ctx, onDone) {
  const code = input({ placeholder: 'e.g. VX-SUV', maxLength: 40, style: { textTransform: 'uppercase' } });
  const name = input({ placeholder: 'e.g. Velocity VX SUV', maxLength: 150 });
  const body = select(BODY);
  const fuel = select(FUEL);
  const price = input({ type: 'number', min: 1, step: 1000, placeholder: '1450000' });
  const preview = h('div', { class: 'model-visual', style: { height: '90px' } }, carArt(body.value));
  body.addEventListener('change', () => mount(preview, carArt(body.value)));
  modal({
    title: 'Add a car model', icon: 'car',
    body: h('div', { class: 'stack' }, preview, h('div', { class: 'form-grid' },
      field('Model code', code, 'Letters, digits and dashes'), field('Name', name), field('Body type', body), field('Fuel', fuel),
      field('Base price (INR)', price, null, { class: 'span-2' }))),
    actions: [{ label: 'Cancel' }, { label: 'Add model', variant: 'primary', icon: 'plus', onClick: async () => {
      const bad = [[code, /^[A-Za-z0-9-]{2,40}$/.test(code.value.trim())], [name, !!name.value.trim()], [price, Number(price.value) >= 1]];
      bad.forEach(([el, ok]) => el.classList.toggle('invalid', !ok));
      if (bad.some(([, ok]) => !ok)) return true;
      const m = await ctx.svc.vehicle.post('/api/v1/vehicle/models', {
        modelCode: code.value.trim(), name: name.value.trim(), bodyType: body.value, fuelType: fuel.value, basePrice: Number(price.value),
      });
      toast('Model added', `${m.name} · ${money(m.basePrice)}`);
      onDone?.();
    } }],
  });
}
