import { h, ic, mount, titleCase, skeleton, emptyState, modal, field, input, select, toast, table, badge, date } from '../../shared/ui.js';
import { ifCan, colorDot, COLOR_SUGGESTIONS } from '../lib.js';

const STATUSES = ['ALL', 'AVAILABLE', 'RESERVED', 'SOLD'];

export async function inventoryPage(ctx, _p, query) {
  const { shell, svc } = ctx;
  shell.setCrumbs('Inventory');
  mount(shell.content, skeleton(8));
  const [units, models] = await Promise.all([
    svc.inventory.get('/api/v1/inventory/units'),
    ifCan(ctx, 'vehicle', () => svc.vehicle.get('/api/v1/vehicle/models'), []),
  ]);
  let status = query.get('status') || 'ALL';
  let model = 'ALL';
  let text = '';
  const name = (code) => models.find((m) => m.modelCode === code)?.name;
  const chips = h('div', { class: 'row wrap' });
  const list = h('div', { class: 'card' });
  const modelFilter = select([{ value: 'ALL', label: 'All models' }, ...[...new Set(units.map((u) => u.modelCode))].sort().map((c) => ({ value: c, label: name(c) || c }))],
    { style: { width: '200px' }, 'aria-label': 'Filter by model', onChange: (e) => { model = e.target.value; draw(); } });

  function draw() {
    mount(chips, STATUSES.map((s) => h('button', { class: `chip ${status === s ? 'on' : ''}`, onClick: () => { status = s; draw(); } }, titleCase(s),
      h('span', { class: 'count' }, s === 'ALL' ? units.length : units.filter((u) => u.status === s).length))));
    const rows = units.filter((u) => status === 'ALL' || u.status === status)
      .filter((u) => model === 'ALL' || u.modelCode === model)
      .filter((u) => !text || u.vin.toLowerCase().includes(text))
      .sort((a, b) => b.id - a.id);
    mount(list, table({
      rows,
      empty: emptyState({ icon: 'box', title: units.length ? 'No car matches' : 'No cars in stock', text: units.length ? 'Try another filter.' : 'Receive the first car into stock.' }),
      columns: [
        { label: 'VIN', render: (u) => h('span', { class: 'mono cell-main' }, u.vin) },
        { label: 'Model', render: (u) => h('div', {}, h('div', { class: 'cell-main' }, name(u.modelCode) || u.modelCode), h('div', { class: 'cell-sub mono' }, u.modelCode)) },
        { label: 'Colour', render: (u) => colorDot(u.color) },
        { label: 'Status', render: (u) => badge(u.status) },
        { label: 'Booking', render: (u) => u.reservedFor ? h('span', { class: 'mono small' }, u.reservedFor) : h('span', { class: 'muted' }, '—') },
        { label: 'Received', render: (u) => h('span', { class: 'muted' }, date(u.createdAt)) },
      ],
    }));
  }
  draw();

  const receive = h('button', { class: 'btn primary' }, ic('plus'), 'Receive car');
  receive.addEventListener('click', () => openReceiveCar(ctx, () => inventoryPage(ctx, _p, query), models));
  const search = h('div', { class: 'input-icon', style: { width: '220px' } }, ic('search'),
    h('input', { class: 'input', placeholder: 'Search VIN', 'aria-label': 'Search VIN', onInput: (e) => { text = e.target.value.toLowerCase(); draw(); } }));
  mount(shell.content,
    h('div', { class: 'page-head' }, h('div', {}, h('h1', {}, 'Inventory'), h('p', {}, 'Every physical car by VIN. Bookings reserve cars here automatically.')), receive),
    h('div', { class: 'row wrap', style: { justifyContent: 'space-between', marginBottom: '16px' } }, chips, h('div', { class: 'row' }, modelFilter, search)),
    list);
}

export async function openReceiveCar(ctx, onDone, knownModels) {
  const models = knownModels || await ifCan(ctx, 'vehicle', () => ctx.svc.vehicle.get('/api/v1/vehicle/models'), []);
  const vin = input({ placeholder: '17 characters, no I, O or Q', maxLength: 17, class: 'input mono', style: { textTransform: 'uppercase', letterSpacing: '.06em' } });
  const counter = h('span', { class: 'hint' }, '0 / 17');
  vin.addEventListener('input', () => { counter.textContent = `${vin.value.length} / 17`; vin.classList.toggle('invalid', vin.value.length === 17 && !validVin(vin.value)); });
  const model = models.length
    ? select(models.map((m) => ({ value: m.modelCode, label: `${m.name} (${m.modelCode})` })))
    : input({ placeholder: 'Model code' });
  const list = h('datalist', { id: 'color-suggestions' }, COLOR_SUGGESTIONS.map((c) => h('option', { value: titleCase(c) })));
  const color = input({ placeholder: 'e.g. Red', list: 'color-suggestions', maxLength: 40 });
  color.setAttribute('list', 'color-suggestions');
  modal({
    title: 'Receive a car into stock', icon: 'box',
    body: h('div', { class: 'stack' }, list,
      h('div', { class: 'field' }, h('label', { for: 'vin-in' }, 'VIN'), Object.assign(vin, { id: 'vin-in' }), counter),
      h('div', { class: 'form-grid' }, field('Model', model), field('Colour', color)),
      h('p', { class: 'muted small' }, 'The model is checked against your vehicle catalog by the inventory service.')),
    actions: [{ label: 'Cancel' }, { label: 'Receive car', variant: 'primary', icon: 'check', onClick: async () => {
      const okVin = validVin(vin.value); const okColor = !!color.value.trim();
      vin.classList.toggle('invalid', !okVin); color.classList.toggle('invalid', !okColor);
      if (!okVin || !okColor || !model.value) return true;
      const u = await ctx.svc.inventory.post('/api/v1/inventory/units', { vin: vin.value.trim().toUpperCase(), modelCode: model.value, color: color.value.trim() });
      toast('Car received', `${u.vin} · ${u.modelCode} · ${titleCase(u.color)}`);
      onDone?.();
    } }],
  });
}

const validVin = (v) => /^[A-HJ-NPR-Z0-9]{17}$/i.test((v || '').trim());
