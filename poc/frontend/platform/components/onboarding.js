// Guided onboarding: company details -> plan & quote -> payment -> provisioning -> access. Can resume a DRAFT company.
import { h, ic, mount, modal, field, input, select, busy, toast } from '../../shared/ui.js';
import { quoteBuilder, orderSummary, paymentPanel } from './billing.js';
import { provisionPanel } from './provisioning.js';
import { accessPanel } from './access.js';

const STEPS = ['Company', 'Plan', 'Payment', 'Provision', 'Access'];

/** @param resume { company, order } to continue an existing company at the right step */
export function openOnboarding(ctx, { resume, onDone } = {}) {
  let company = resume?.company || null;
  let order = resume?.order || null;
  let step = !company ? 0 : !order ? 1 : order.order.status !== 'FULFILLED' ? 2 : company.status === 'ACTIVE' ? 4 : 3;
  const stepper = h('div', { class: 'wizard-steps' });
  const body = h('div');
  const m = modal({ title: resume ? `Continue onboarding · ${company.displayName}` : 'Onboard a company', body: h('div', {}, stepper, body), size: 'xl', icon: 'building', onClose: () => onDone?.(company) });

  function render() {
    mount(stepper, STEPS.map((s, i) => h('div', { class: `ws ${i === step ? 'on' : i < step ? 'done' : ''}` },
      h('span', { class: 'n' }, i < step ? ic('check') : String(i + 1)), s)));
    [detailsStep, planStep, paymentStep, provisionStep, accessStep][step]();
  }
  const next = () => { step += 1; render(); };

  function detailsStep() {
    const legal = input({ placeholder: 'e.g. Velocity Motors Pvt Ltd', required: true });
    const display = input({ placeholder: 'Shown in the portal (optional)' });
    const email = input({ type: 'email', placeholder: 'owner@company.com', required: true });
    const type = select([{ value: 'NEW', label: 'New company (start empty, seeded)' }, { value: 'IMPORT_EXISTING_DATA', label: 'Import existing data (empty databases)' }]);
    const go = h('button', { class: 'btn primary' }, 'Create company', ic('arrowRight'));
    go.addEventListener('click', () => {
      [legal, email].forEach((i) => i.classList.toggle('invalid', !i.value.trim()));
      if (!legal.value.trim() || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.value)) { email.classList.toggle('invalid', true); return; }
      busy(go, async () => {
        company = await ctx.api.post('/api/v1/platform/companies', {
          legalName: legal.value.trim(), displayName: display.value.trim() || undefined, superAdminEmail: email.value.trim(), onboardingType: type.value,
        });
        toast('Company created', `${company.companyCode} · login code ${company.loginCode}`);
        next();
      });
    });
    mount(body, h('div', { class: 'stack', style: { gap: '16px' } },
      h('p', { class: 'muted' }, 'The company starts as DRAFT. Nothing is created in Keycloak, MySQL or MinIO until it is paid and provisioned.'),
      h('div', { class: 'form-grid' },
        field('Legal name', legal), field('Display name', display),
        field('Company Super Admin e-mail', email, 'The first user of the company, invited into its own realm.'), field('Onboarding type', type)),
      h('div', { class: 'row' }, h('div', { class: 'grow' }), go)));
  }

  function planStep() {
    mount(body, h('div', { class: 'stack' },
      h('p', { class: 'muted' }, `Choose what ${company.displayName} buys. Required modules are added automatically.`),
      quoteBuilder(ctx, company, { onQuoted: (o) => { order = o; next(); } })));
  }

  function paymentStep() {
    const summary = h('div');
    const draw = (view) => mount(summary, orderSummary(view));
    draw(order);
    const nextBtn = h('button', { class: 'btn primary', disabled: order.order.status !== 'FULFILLED' }, 'Continue to provisioning', ic('arrowRight'));
    nextBtn.addEventListener('click', next);
    mount(body, h('div', { class: 'grid cols-2', style: { alignItems: 'start' } },
      h('div', { class: 'stack' }, h('h3', {}, 'Quote'), summary),
      h('div', { class: 'stack' }, h('h3', {}, 'Confirm and collect payment'),
        paymentPanel(ctx, order, { onChange: (v) => { order = v; draw(v); nextBtn.disabled = v.order.status !== 'FULFILLED'; } }),
        h('div', { class: 'row' }, h('div', { class: 'grow' }), nextBtn))));
  }

  function provisionStep() {
    const nextBtn = h('button', { class: 'btn primary', disabled: true }, 'Continue', ic('arrowRight'));
    nextBtn.addEventListener('click', next);
    mount(body, h('div', { class: 'stack' },
      provisionPanel(ctx, company.companyCode, {
        canStart: true,
        onActive: (c) => { company = { ...company, ...c }; nextBtn.disabled = false; toast('Company is live', `${company.displayName} is ACTIVE.`); },
      }),
      h('div', { class: 'row' }, h('div', { class: 'grow' }), nextBtn)));
  }

  function accessStep() {
    const done = h('button', { class: 'btn primary' }, ic('check'), 'Finish');
    done.addEventListener('click', () => { m.close(); ctx.go(`#/companies/${company.companyCode}`); });
    mount(body, h('div', { class: 'stack', style: { gap: '16px' } },
      h('div', { class: 'hero' }, h('h2', { style: { color: 'inherit' } }, `${company.displayName} is ready`),
        h('p', {}, 'Share the login code and a temporary password with the Company Super Admin. Next, you can set the company\'s branding.')),
      accessPanel(ctx, company),
      h('div', { class: 'row' }, h('div', { class: 'grow' }), done)));
  }

  render();
  return m;
}
