// Provisioning: start the job and watch its steps live (master §7.3 durable jobs and steps).
import { h, ic, mount, badge, titleCase, dateTime, busy, toast } from '../../shared/ui.js';

const STEP_TEXT = {
  RESERVE_NAMES: 'Reserve resource names',
  CREATE_REALM: 'Create identity realm',
  CREATE_DATABASE: 'Create database',
  MIGRATE_SCHEMA: 'Migrate schema (Liquibase)',
  CREATE_BUCKET: 'Create storage bucket',
  INVITE_COMPANY_SUPER_ADMIN: 'Invite Company Super Admin',
  ACTIVATE: 'Activate company and modules',
  PUBLISH_CACHES: 'Publish access state',
};

export function jobTimeline(jv) {
  const steps = jv.steps || [];
  const done = steps.filter((s) => ['COMPLETED', 'SKIPPED'].includes(s.status)).length;
  const pct = steps.length ? Math.round((done / steps.length) * 100) : 0;
  return h('div', { class: 'stack' },
    h('div', { class: 'row' }, h('div', { class: 'grow' },
      h('div', { class: 'cell-main' }, `${titleCase(jv.job.jobType)} · job #${jv.job.id}`),
      h('div', { class: 'cell-sub' }, `${titleCase(jv.job.mode)} · requested by ${jv.job.requestedBy || 'system'} · ${dateTime(jv.job.createdAt)}`)),
      badge(jv.job.status)),
    h('div', { class: 'row' }, h('div', { class: 'progress grow' }, h('span', { style: { width: `${pct}%` } })), h('span', { class: 'small muted' }, `${done}/${steps.length}`)),
    jv.job.lastError ? h('div', { class: 'banner bad' }, ic('alert'), h('div', { class: 'small mono' }, jv.job.lastError)) : null,
    h('ol', { class: 'steps' }, steps.map((s) => {
      const tone = { COMPLETED: 'ok', RUNNING: 'run', FAILED: 'bad', SKIPPED: 'skip' }[s.status] || '';
      const dotIcon = { COMPLETED: 'check', FAILED: 'x', RUNNING: 'refresh' }[s.status];
      return h('li', {},
        h('div', { class: `step-dot ${tone}` }, dotIcon ? ic(dotIcon) : String(s.sequenceNo)),
        h('div', { class: 'grow' },
          h('div', { class: 'step-title' }, STEP_TEXT[s.stepCode] || titleCase(s.stepCode)),
          h('div', { class: 'step-meta' }, [s.resourceName, s.status !== 'PENDING' ? titleCase(s.status) : null, s.attempts > 1 ? `${s.attempts} attempts` : null].filter(Boolean).join(' · ')),
          s.lastError ? h('div', { class: 'small mono', style: { color: 'var(--bad)' } }, s.lastError) : null));
    })));
}

/**
 * Shows the latest job and polls while it runs. `onActive` fires once the company is ACTIVE.
 * When `canStart` is set, offers the "Start provisioning" button first.
 */
export function provisionPanel(ctx, companyCode, { canStart = false, onActive } = {}) {
  const root = h('div', { class: 'stack' });
  let timer = null;
  let notified = false;

  async function poll() {
    const [jobs, company] = await Promise.all([
      ctx.api.get(`/api/v1/platform/companies/${companyCode}/provisioning-jobs`),
      ctx.api.get(`/api/v1/platform/companies/${companyCode}`),
    ]);
    const latest = jobs[jobs.length - 1];
    if (!latest) {
      mount(root, canStart ? startCard() : h('div', { class: 'muted' }, 'No provisioning job yet.'));
      return;
    }
    const running = ['QUEUED', 'RUNNING', 'RETRYING'].includes(latest.job.status);
    const failed = latest.job.status === 'FAILED';
    const retry = failed ? h('button', { class: 'btn primary' }, ic('refresh'), 'Resume job') : null;
    retry?.addEventListener('click', () => busy(retry, async () => {
      await ctx.api.post(`/api/v1/platform/provisioning-jobs/${latest.job.id}/retry`); toast('Job resumed', 'Only the unfinished steps run again.'); schedule(0);
    }));
    mount(root, h('div', { class: 'card card-pad' }, jobTimeline(latest)), retry ? h('div', { class: 'row' }, h('div', { class: 'grow' }), retry) : null);
    if (company.status === 'ACTIVE' && !running && !notified) { notified = true; onActive?.(company); }
    if (running || company.status === 'PROVISIONING') schedule(1200);
  }

  function schedule(ms) {
    clearTimeout(timer);
    timer = setTimeout(() => { if (root.isConnected) poll().catch(() => schedule(3000)); }, ms);
  }

  function startCard() {
    const b = h('button', { class: 'btn primary lg' }, ic('server'), 'Start provisioning');
    b.addEventListener('click', () => busy(b, async () => {
      await ctx.api.post(`/api/v1/platform/companies/${companyCode}/provision`);
      toast('Provisioning started', 'Identity realm, databases, schemas and buckets are being created.');
      schedule(300);
    }));
    return h('div', { class: 'card card-pad stack', style: { alignItems: 'center', textAlign: 'center', padding: '32px' } },
      h('div', { class: 'stat-icon', style: { width: '56px', height: '56px' } }, ic('server')),
      h('h3', {}, 'Ready to provision'),
      h('p', { class: 'muted', style: { maxWidth: '440px' } }, 'Creates the company realm and its Company Super Admin, one database per purchased module (with its schema), the storage buckets, and then opens access.'),
      b);
  }

  poll().catch((e) => mount(root, h('div', { class: 'banner bad' }, ic('alert'), e.detail || e.message)));
  return root;
}
