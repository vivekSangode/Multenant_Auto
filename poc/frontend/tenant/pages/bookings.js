import { h, ic, mount, money, titleCase, skeleton, emptyState, modal, field, input, select, toast, table, badge, dateTime, confirmDialog, busy, drawer } from '../../shared/ui.js';
import { carArt } from '../../shared/icons.js';
import { ifCan, colorDot } from '../lib.js';

const STATUSES = ['ALL', 'BOOKED', 'DELIVERED', 'CANCELLED'];

export async function bookingsPage(ctx, _p, query) {
  const { shell, svc } = ctx;
  shell.setCrumbs('Bookings');
  mount(shell.content, skeleton(8));
  const [bookings, models] = await Promise.all([
    svc.booking.get('/api/v1/booking/bookings'),
    ifCan(ctx, 'vehicle', () => svc.vehicle.get('/api/v1/vehicle/models'), []),
  ]);
  let status = query.get('status') || 'ALL';
  let text = '';
  const name = (code) => models.find((m) => m.modelCode === code)?.name || code;
  const reload = () => bookingsPage(ctx, _p, query);
  const chips = h('div', { class: 'row wrap' });
  const list = h('div', { class: 'card' });

  const act = (b, kind) => {
    const deliver = kind === 'deliver';
    const btn = h('button', { class: `btn sm ${deliver ? 'primary' : ''}` }, ic(deliver ? 'truck' : 'x'), deliver ? 'Deliver' : 'Cancel');
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const ok = await confirmDialog({
        title: deliver ? `Deliver to ${b.customerName}?` : `Cancel ${b.bookingNo}?`, danger: !deliver, confirmLabel: deliver ? 'Mark delivered' : 'Cancel booking',
        message: deliver ? `Car ${b.vin} is marked SOLD in inventory.` : `Car ${b.vin} goes back to AVAILABLE stock.`,
      });
      if (ok) busy(btn, async () => {
        await svc.booking.post(`/api/v1/booking/bookings/${b.bookingNo}/${kind}`);
        toast(deliver ? 'Booking delivered' : 'Booking cancelled', b.bookingNo);
        reload();
      });
    });
    return btn;
  };

  function draw() {
    mount(chips, STATUSES.map((s) => h('button', { class: `chip ${status === s ? 'on' : ''}`, onClick: () => { status = s; draw(); } }, titleCase(s),
      h('span', { class: 'count' }, s === 'ALL' ? bookings.length : bookings.filter((b) => b.status === s).length))));
    const rows = bookings.filter((b) => status === 'ALL' || b.status === status)
      .filter((b) => !text || `${b.customerName} ${b.bookingNo} ${b.customerPhone || ''} ${b.vin}`.toLowerCase().includes(text))
      .sort((a, b) => b.id - a.id);
    mount(list, table({
      rows,
      onRowClick: (b) => openBooking(ctx, b, name, act),
      empty: emptyState({ icon: 'calendar', title: bookings.length ? 'No booking matches' : 'No bookings yet', text: bookings.length ? 'Try another filter.' : 'Create the first booking for a customer.' }),
      columns: [
        { label: 'Booking', render: (b) => h('div', {}, h('div', { class: 'cell-main mono' }, b.bookingNo), h('div', { class: 'cell-sub' }, dateTime(b.createdAt))) },
        { label: 'Customer', render: (b) => h('div', {}, h('div', { class: 'cell-main' }, b.customerName), h('div', { class: 'cell-sub' }, b.customerPhone || '—')) },
        { label: 'Car', render: (b) => h('div', {}, h('div', {}, name(b.modelCode)), h('div', { class: 'cell-sub' }, colorDot(b.color))) },
        { label: 'VIN', render: (b) => h('span', { class: 'mono small' }, b.vin) },
        { label: 'Status', render: (b) => badge(b.status) },
        { label: 'Price', num: true, render: (b) => h('b', {}, money(b.price)) },
        { label: '', render: (b) => b.status === 'BOOKED' ? h('div', { class: 'row', style: { justifyContent: 'flex-end' } }, act(b, 'cancel'), act(b, 'deliver')) : '' },
      ],
    }));
  }
  draw();

  const create = h('button', { class: 'btn primary' }, ic('plus'), 'New booking');
  create.addEventListener('click', () => openNewBooking(ctx, reload));
  const search = h('div', { class: 'input-icon', style: { width: '260px' } }, ic('search'),
    h('input', { class: 'input', placeholder: 'Customer, phone, booking no, VIN', 'aria-label': 'Search bookings', onInput: (e) => { text = e.target.value.toLowerCase(); draw(); } }));
  mount(shell.content,
    h('div', { class: 'page-head' }, h('div', {}, h('h1', {}, 'Bookings'), h('p', {}, 'A booking takes the price from the catalog and reserves a real car by VIN.')), create),
    h('div', { class: 'row wrap', style: { justifyContent: 'space-between', marginBottom: '16px' } }, chips, search),
    list);
}

function openBooking(ctx, b, name, act) {
  drawer({
    title: b.customerName, subtitle: b.bookingNo,
    body: h('div', { class: 'stack', style: { gap: '18px' } },
      h('div', { class: 'row' }, badge(b.status), h('span', { class: 'muted small' }, dateTime(b.createdAt))),
      h('dl', { class: 'kv' },
        h('dt', {}, 'Customer'), h('dd', {}, b.customerName),
        h('dt', {}, 'Phone'), h('dd', {}, b.customerPhone || '—'),
        h('dt', {}, 'Car'), h('dd', {}, `${name(b.modelCode)} (${b.modelCode})`),
        h('dt', {}, 'Colour'), h('dd', {}, colorDot(b.color)),
        h('dt', {}, 'VIN'), h('dd', { class: 'mono' }, b.vin),
        h('dt', {}, 'Price'), h('dd', {}, h('span', { class: 'price-tag' }, money(b.price))),
        h('dt', {}, 'Booked by'), h('dd', {}, b.createdBy || '—')),
      b.status === 'BOOKED' ? h('div', { class: 'row' }, h('div', { class: 'grow' }), act(b, 'cancel'), act(b, 'deliver')) : null),
  });
}

/** Model -> available colours (live from inventory) -> customer -> book. */
export async function openNewBooking(ctx, onDone) {
  const models = await ifCan(ctx, 'vehicle', () => ctx.svc.vehicle.get('/api/v1/vehicle/models'), []);
  const units = await ifCan(ctx, 'inventory', () => ctx.svc.inventory.get('/api/v1/inventory/units?status=AVAILABLE'), []);
  const availability = (code) => units.filter((u) => u.modelCode === code);
  const customer = input({ placeholder: 'Full name', maxLength: 150 });
  const phone = input({ placeholder: 'Mobile number', maxLength: 30, type: 'tel' });
  const model = select(models.map((m) => ({ value: m.modelCode, label: `${m.name} · ${availability(m.modelCode).length} available`, disabled: !availability(m.modelCode).length })));
  const firstInStock = models.find((m) => availability(m.modelCode).length);
  if (firstInStock) model.value = firstInStock.modelCode;
  const color = select([]);
  const summary = h('div');

  function sync() {
    const m = models.find((x) => x.modelCode === model.value);
    const byColor = availability(model.value).reduce((acc, u) => ({ ...acc, [u.color]: (acc[u.color] || 0) + 1 }), {});
    mount(color, Object.entries(byColor).map(([c, n]) => h('option', { value: c }, `${titleCase(c)} · ${n} available`)));
    mount(summary, m ? h('div', { class: 'card card-pad row', style: { background: 'var(--primary-softer)', borderColor: 'transparent' } },
      h('div', { class: 'model-visual', style: { width: '110px', height: '64px' } }, carArt(m.bodyType)),
      h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, m.name), h('div', { class: 'cell-sub' }, `${titleCase(m.bodyType)} · ${titleCase(m.fuelType)}`)),
      h('div', { style: { textAlign: 'right' } }, h('div', { class: 'muted small' }, 'Price'), h('div', { class: 'price-tag' }, money(m.basePrice)))) : null);
  }
  model.addEventListener('change', sync);
  sync();

  const noStock = !firstInStock;
  modal({
    title: 'New booking', icon: 'calendar', size: 'lg',
    body: noStock ? emptyState({ icon: 'box', title: 'No car is available to book', text: 'Receive cars into inventory first.', action: h('a', { class: 'btn', href: '#/inventory' }, 'Go to inventory') })
      : h('div', { class: 'stack', style: { gap: '16px' } }, summary,
        h('div', { class: 'form-grid' }, field('Model', model), field('Colour', color), field('Customer name', customer), field('Phone', phone)),
        h('p', { class: 'muted small' }, 'The booking service asks the vehicle service for today\'s price and reserves one available car in this colour.')),
    actions: noStock ? [{ label: 'Close' }] : [{ label: 'Cancel' }, { label: 'Book car', variant: 'primary', icon: 'check', onClick: async () => {
      if (!customer.value.trim()) { customer.classList.add('invalid'); return true; }
      const b = await ctx.svc.booking.post('/api/v1/booking/bookings', {
        customerName: customer.value.trim(), customerPhone: phone.value.trim() || undefined, modelCode: model.value, color: color.value,
      });
      toast('Car booked', `${b.bookingNo} · VIN ${b.vin} · ${money(b.price)}`);
      onDone?.();
    } }],
  });
}
