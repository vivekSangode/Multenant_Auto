// Quote builder, order summary and payment panel (PHASE_01 §6.9.2: quote -> confirm -> full payment -> fulfilled).
import { h, ic, mount, money, num, badge, titleCase, field, input, select, segmented, busy, toast, table, dateTime, confirmDialog } from '../../shared/ui.js';
import { catalog, PAYMENT_MODES } from '../lib.js';

/**
 * Bundles and modules as selectable cards with a live estimate; "Create quote" asks the server for the binding
 * price (mandatory dependencies, redundant bundles, seats and storage are resolved by the pricing engine).
 */
export function quoteBuilder(ctx, company, { onQuoted, submitLabel = 'Create quote' } = {}) {
  const state = { period: 'MONTHLY', bundles: new Set(), modules: new Set(), users: 5, storageGb: 10, discount: null };
  const root = h('div', { class: 'stack', style: { gap: '18px' } });
  const estimateBox = h('div');
  const body = h('div', { class: 'stack', style: { gap: '18px' } });

  async function render() {
    const cat = await catalog(ctx, state.period);
    const modules = Object.values(cat.modules).filter((m) => m.active && m.type !== 'PLATFORM_CORE');
    const bundles = Object.values(cat.bundles).filter((b) => b.active);
    const inBundles = new Set([...state.bundles].flatMap((code) => [...(cat.bundles[code]?.moduleCodes || [])]));
    const deps = (code) => cat.dependencies.filter((d) => d.module === code && d.type === 'MANDATORY').map((d) => d.dependsOn);
    const price = (key) => cat.prices[key]?.unitPrice;

    const bundleCards = bundles.map((b) => {
      const on = state.bundles.has(b.code);
      return h('label', { class: `check-card ${on ? 'on' : ''}` },
        h('input', { type: 'checkbox', checked: on, onChange: (e) => { e.target.checked ? state.bundles.add(b.code) : state.bundles.delete(b.code); render(); } }),
        h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, b.name),
          h('div', { class: 'row wrap', style: { gap: '4px', marginTop: '6px' } }, [...b.moduleCodes].map((m) => h('span', { class: 'badge plain' }, cat.modules[m]?.name || m)))),
        h('div', { class: 'price' }, money(price(`BUNDLE:${b.code}`))));
    });
    const moduleCards = modules.map((m) => {
      const covered = inBundles.has(m.code);
      const on = covered || state.modules.has(m.code);
      const needs = deps(m.code);
      return h('label', { class: `check-card ${on ? 'on' : ''} ${covered ? 'locked' : ''}` },
        h('input', { type: 'checkbox', checked: on, disabled: covered, onChange: (e) => { e.target.checked ? state.modules.add(m.code) : state.modules.delete(m.code); render(); } }),
        h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, m.name),
          h('div', { class: 'cell-sub' }, covered ? 'Included in a selected bundle' : needs.length ? `Requires ${needs.map((d) => cat.modules[d]?.name || d).join(', ')}` : titleCase(m.type))),
        h('div', { class: 'price' }, money(price(`MODULE:${m.code}`))));
    });

    const users = input({ type: 'number', min: 1, value: state.users, onInput: (e) => { state.users = Math.max(1, +e.target.value || 1); renderEstimate(cat); } });
    const storage = input({ type: 'number', min: 0, step: 10, value: state.storageGb, onInput: (e) => { state.storageGb = Math.max(0, +e.target.value || 0); renderEstimate(cat); } });
    const seat = cat.prices.USER_SEAT; const store = cat.prices.STORAGE;

    const discountToggle = h('label', { class: 'row small', style: { cursor: 'pointer' } },
      h('input', { type: 'checkbox', checked: !!state.discount, onChange: (e) => { state.discount = e.target.checked ? { type: 'PERCENT', value: 10, reason: '' } : null; render(); } }),
      'Add a manual discount');
    const discountFields = state.discount ? h('div', { class: 'form-grid' },
      field('Type', select([{ value: 'PERCENT', label: 'Percent' }, { value: 'FIXED', label: 'Fixed amount' }], { value: state.discount.type, onChange: (e) => { state.discount.type = e.target.value; renderEstimate(cat); } })),
      field('Value', input({ type: 'number', min: 0, value: state.discount.value, onInput: (e) => { state.discount.value = +e.target.value || 0; renderEstimate(cat); } })),
      field('Reason (audited)', input({ value: state.discount.reason, placeholder: 'e.g. launch offer', onInput: (e) => { state.discount.reason = e.target.value; } }), null, { class: 'span-2' })) : null;

    mount(body,
      h('div', { class: 'row' }, h('h3', { class: 'grow' }, 'Billing period'),
        segmented([{ value: 'MONTHLY', label: 'Monthly' }, { value: 'YEARLY', label: 'Yearly' }], state.period, (v) => { state.period = v; render(); })),
      bundles.length ? h('div', { class: 'stack' }, h('h3', {}, 'Bundles'), h('div', { class: 'grid cols-2' }, bundleCards)) : null,
      h('div', { class: 'stack' }, h('h3', {}, 'Modules'), h('div', { class: 'grid cols-2' }, moduleCards)),
      h('div', { class: 'form-grid' },
        field('Users', users, seat ? `${seat.includedUsers} included, then ${money(seat.unitPrice)} per user` : null),
        field('Storage (GB)', storage, store ? `${store.includedStorageGb} GB included, then ${money(store.unitPrice)} per ${store.unitQuantity} GB` : null)),
      discountToggle, discountFields);
    renderEstimate(cat);
  }

  function renderEstimate(cat) {
    const selectedModules = new Set([...state.modules, ...[...state.bundles].flatMap((b) => [...(cat.bundles[b]?.moduleCodes || [])])]);
    const pending = [...selectedModules];
    while (pending.length) { // mandatory dependency closure, like the pricing engine
      const m = pending.pop();
      cat.dependencies.filter((d) => d.module === m && d.type === 'MANDATORY' && d.chargeMode !== 'FREE').forEach((d) => {
        if (!selectedModules.has(d.dependsOn)) { selectedModules.add(d.dependsOn); pending.push(d.dependsOn); }
      });
    }
    const bundleModules = new Set([...state.bundles].flatMap((b) => [...(cat.bundles[b]?.moduleCodes || [])]));
    let total = [...state.bundles].reduce((s, b) => s + (cat.prices[`BUNDLE:${b}`]?.unitPrice || 0), 0);
    const extras = [...selectedModules].filter((m) => !bundleModules.has(m));
    total += extras.reduce((s, m) => s + (cat.prices[`MODULE:${m}`]?.unitPrice || 0), 0);
    const seat = cat.prices.USER_SEAT; const store = cat.prices.STORAGE;
    if (seat) total += Math.max(0, state.users - seat.includedUsers) * seat.unitPrice;
    if (store) total += Math.ceil(Math.max(0, state.storageGb - store.includedStorageGb) / store.unitQuantity) * store.unitPrice;
    let disc = 0;
    if (state.discount) disc = state.discount.type === 'PERCENT' ? total * Math.min(100, state.discount.value) / 100 : Math.min(total, state.discount.value);
    const auto = extras.filter((m) => !state.modules.has(m));
    mount(estimateBox, h('div', { class: 'card card-pad', style: { background: 'var(--primary-softer)', borderColor: 'transparent' } },
      h('div', { class: 'row' },
        h('div', { class: 'grow' }, h('div', { class: 'muted small' }, `Estimated ${state.period === 'YEARLY' ? 'yearly' : 'monthly'} total`),
          h('div', { class: 'stat-value' }, money(total - disc)),
          auto.length ? h('div', { class: 'small', style: { color: 'var(--warn)' } }, `Adds required: ${auto.map((m) => cat.modules[m]?.name || m).join(', ')}`) : null),
        h('div', { class: 'small muted', style: { maxWidth: '230px', textAlign: 'right' } }, 'Estimate only. The server computes the binding quote, including tax and rounding.'))));
  }

  const submit = h('button', { class: 'btn primary' }, ic('receipt'), submitLabel);
  submit.addEventListener('click', () => busy(submit, async () => {
    if (!state.bundles.size && !state.modules.size) { toast('Pick at least one bundle or module', '', 'warn'); return; }
    if (state.discount && !state.discount.reason.trim()) { toast('A discount needs a reason', 'It is stored in the audit trail.', 'warn'); return; }
    const order = await ctx.api.post(`/api/v1/platform/companies/${company.companyCode}/orders`, {
      billingPeriod: state.period, bundles: [...state.bundles], modules: [...state.modules],
      users: state.users, storageGb: state.storageGb,
      manualDiscount: state.discount ? { type: state.discount.type, value: state.discount.value, reason: state.discount.reason } : undefined,
    });
    toast('Quote created', `${order.order.orderNumber} · ${money(order.order.grandTotal)}`);
    onQuoted?.(order);
  }));

  mount(root, body, estimateBox, h('div', { class: 'row' }, h('div', { class: 'grow' }), submit));
  render().catch((e) => mount(body, h('div', { class: 'banner bad' }, ic('alert'), e.detail || e.message)));
  return root;
}

/** Lines, discounts, warnings and totals of one order (OrderView of the API). */
export function orderSummary(view) {
  const o = view.order;
  return h('div', { class: 'stack', style: { gap: '16px' } },
    h('div', { class: 'row wrap' },
      h('div', { class: 'grow' }, h('div', { class: 'cell-main' }, o.orderNumber || `Order #${o.id}`),
        h('div', { class: 'cell-sub' }, `${titleCase(o.orderKind)} · ${titleCase(o.billingPeriod)} · ${o.requestedUsers} users · ${o.requestedStorageGb} GB · created ${dateTime(o.createdAt)}`)),
      badge(o.status), badge(o.paymentStatus)),
    (view.warnings || []).map((w) => h('div', { class: 'banner warn' }, ic('alert'), h('div', { class: 'small' }, h('b', {}, titleCase(w.code)), ' ', w.detail))),
    h('div', { class: 'card' }, table({
      columns: [
        { label: 'Item', render: (l) => h('div', {}, h('div', { class: 'cell-main' }, l.description), h('div', { class: 'cell-sub' }, titleCase(l.lineType))) },
        { label: 'Qty', num: true, render: (l) => num(l.quantity) },
        { label: 'Unit', num: true, render: (l) => money(l.unitPrice) },
        { label: 'Total', num: true, render: (l) => money(l.lineTotal) },
      ],
      rows: view.lines || [],
    })),
    h('div', { class: 'totals', style: { maxWidth: '360px', marginLeft: 'auto', width: '100%' } },
      tr('Subtotal', money(o.subtotal)),
      Number(o.discountTotal) ? tr('Discount', `− ${money(o.discountTotal)}`) : null,
      tr('Tax', money(o.taxTotal)),
      Number(o.roundOff) ? tr('Round off', money(o.roundOff)) : null,
      h('div', { class: 'tr grand' }, h('span', {}, 'Total'), h('span', {}, money(o.grandTotal)))),
    view.invoice ? h('div', { class: 'banner info' }, ic('receipt'), h('div', { class: 'small' },
      h('b', {}, `Invoice ${view.invoice.invoiceNumber}`), ` · ${titleCase(view.invoice.status)} · balance ${money(view.invoice.balanceDue)}`)) : null);
}

const tr = (k, v) => h('div', { class: 'tr' }, h('span', { class: 'muted' }, k), h('span', {}, v));

/**
 * Confirm the order, record payments, verify or reject instruments. Calls onChange(view) after every change;
 * the order is FULFILLED (and the subscription ACTIVE) once 100 % is verified (CD-07).
 */
export function paymentPanel(ctx, initialView, { onChange } = {}) {
  const root = h('div', { class: 'stack', style: { gap: '16px' } });
  let view = initialView;

  const reload = async () => { view = await ctx.api.get(`/api/v1/platform/orders/${view.order.id}`); render(); onChange?.(view); };

  function render() {
    const o = view.order;
    const payments = view.payments || [];
    const counted = payments.filter((p) => ['CONFIRMED', 'PENDING_VERIFICATION', 'RECORDED'].includes(p.status)).reduce((s, p) => s + Number(p.amount), 0);
    const balance = Math.max(0, Number(o.grandTotal) - counted);
    const blocks = [];

    if (o.status === 'QUOTED') {
      const confirm = h('button', { class: 'btn primary' }, ic('check'), 'Confirm order');
      confirm.addEventListener('click', () => busy(confirm, async () => {
        view = await ctx.api.post(`/api/v1/platform/orders/${o.id}/confirm`); toast('Order confirmed', 'Amounts are frozen; the subscription waits for payment.'); render(); onChange?.(view);
      }));
      const cancel = h('button', { class: 'btn' }, 'Cancel quote');
      cancel.addEventListener('click', async () => {
        const reason = await confirmDialog({ title: 'Cancel this quote?', message: 'The quote cannot be used afterwards.', confirmLabel: 'Cancel quote', danger: true, withReason: true });
        if (reason) busy(cancel, async () => { view = await ctx.api.post(`/api/v1/platform/orders/${o.id}/cancel`, { reason }); render(); onChange?.(view); });
      });
      blocks.push(h('div', { class: 'banner info' }, ic('info'), h('div', { class: 'grow small' }, 'Review the quote, then confirm it to collect payment.'), cancel, confirm));
    }

    if (payments.length) {
      blocks.push(h('div', { class: 'card' }, table({
        columns: [
          { label: 'Payment', render: (p) => h('div', {}, h('div', { class: 'cell-main' }, p.paymentNumber || `#${p.id}`), h('div', { class: 'cell-sub' }, `${titleCase(p.paymentMode)}${p.instrumentRef ? ` · ${p.instrumentRef}` : ''}${p.bankName ? ` · ${p.bankName}` : ''}`)) },
          { label: 'Status', render: (p) => h('div', {}, badge(p.status), p.rejectionReason ? h('div', { class: 'cell-sub' }, p.rejectionReason) : null) },
          { label: 'Amount', num: true, render: (p) => money(p.amount) },
          { label: '', render: (p) => p.status === 'PENDING_VERIFICATION' ? h('div', { class: 'row', style: { justifyContent: 'flex-end' } },
            actionBtn('Bounced', 'x', '', async () => {
              const reason = await confirmDialog({ title: 'Reject this payment?', message: 'For example a bounced cheque. The amount no longer counts.', confirmLabel: 'Reject payment', danger: true, withReason: true });
              if (reason) { await ctx.api.post(`/api/v1/platform/payments/${p.id}/reject`, { reason }); toast('Payment rejected', '', 'warn'); await reload(); }
            }),
            actionBtn('Cleared', 'check', 'primary', async () => { await ctx.api.post(`/api/v1/platform/payments/${p.id}/verify`); toast('Payment verified'); await reload(); })) : '' },
        ],
        rows: payments,
      })));
    }

    if (['AWAITING_PAYMENT', 'PAID'].includes(o.status) && balance > 0) {
      const mode = select(PAYMENT_MODES.map((m) => ({ value: m.value, label: m.label })));
      const amount = input({ type: 'number', min: 0.01, step: '0.01', value: balance.toFixed(2) });
      const ref = input({ placeholder: 'Cheque / UTR number' });
      const bank = input({ placeholder: 'Bank name' });
      const refFields = h('div', { class: 'form-grid' }, field('Instrument reference', ref), field('Bank', bank));
      const syncRef = () => { refFields.style.display = PAYMENT_MODES.find((m) => m.value === mode.value)?.needsRef ? '' : 'none'; };
      mode.addEventListener('change', syncRef); syncRef();
      const record = h('button', { class: 'btn primary' }, ic('cash'), 'Record payment');
      record.addEventListener('click', () => busy(record, async () => {
        view = await ctx.api.post(`/api/v1/platform/orders/${o.id}/payments`, {
          mode: mode.value, amount: Number(amount.value), instrumentRef: ref.value || undefined, bankName: bank.value || undefined,
        });
        toast('Payment recorded', `${titleCase(mode.value)} · ${money(amount.value)}`);
        render(); onChange?.(view);
      }));
      blocks.push(h('div', { class: 'card card-pad stack' },
        h('div', { class: 'row' }, h('h3', { class: 'grow' }, 'Record a payment'), h('span', { class: 'muted small' }, `Balance ${money(balance)}`)),
        h('div', { class: 'form-grid' }, field('Mode', mode), field('Amount', amount, 'Only full payment activates the subscription (CD-07).')),
        refFields, h('div', { class: 'row' }, h('div', { class: 'grow' }), record)));
    } else if (balance === 0 && o.status !== 'FULFILLED' && payments.some((p) => p.status === 'PENDING_VERIFICATION')) {
      blocks.push(h('div', { class: 'banner warn' }, ic('info'), h('div', { class: 'small' }, 'The full amount is recorded. Verify the pending instruments to fulfil the order.')));
    }

    if (o.status === 'FULFILLED') {
      blocks.push(h('div', { class: 'banner info', style: { background: 'var(--ok-soft)', borderColor: 'transparent' } }, ic('check'),
        h('div', { class: 'small' }, h('b', {}, 'Paid in full. '), 'The invoice is issued and the subscription is active.')));
    }
    mount(root, blocks.length ? blocks : h('div', { class: 'muted' }, `Order is ${titleCase(o.status)}.`));
  }

  function actionBtn(label, iconName, variant, fn) {
    const b = h('button', { class: `btn sm ${variant}` }, ic(iconName), label);
    b.addEventListener('click', (e) => { e.stopPropagation(); busy(b, fn); });
    return b;
  }

  render();
  return root;
}
