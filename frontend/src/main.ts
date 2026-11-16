import './styles/tokens.css';
import './styles/base.css';
import './styles/layout.css';
import './styles/components.css';
import './styles/motion.css';

import { h } from './lib/dom';
import { loadSession, isTenant, can, errText } from './lib/api';
import { route, match, current, navigate } from './lib/router';
import { onMonth } from './lib/ctx';
import { enterView, initMotionPref } from './lib/motion';
import { buildStaffShell, buildTenantShell, StaffShell, TenantShell } from './ui/shell';
import { errorState, skeletonRows } from './ui/kit';
import { renderLogin } from './pages/login';

import { dashboardPage } from './pages/dashboard';
import { billingPage, billingDetailPage } from './pages/billing';
import { invoicesPage } from './pages/invoices';
import { paymentsPage } from './pages/payments';
import { contractsPage } from './pages/contracts';
import { roomsPage } from './pages/rooms';
import { tenantsPage } from './pages/tenants';
import { reportsPage } from './pages/reports';
import { staffPage } from './pages/staff';
import { activityPage } from './pages/activity';
import { profilePage } from './pages/profile';
import { meHomePage, meInvoicesPage, meContractsPage, meNotificationsPage } from './pages/me';

const STAFF_ROLES = ['LANDLORD', 'MANAGER', 'CASHIER', 'METER_READER'] as const;

route('/', 'staff', dashboardPage);
route('/billing', 'staff', billingPage);
route('/billing/:id', 'staff', billingDetailPage);
route('/invoices', 'staff', invoicesPage);
route('/payments', 'staff', paymentsPage);
route('/contracts', 'staff', contractsPage);
route('/rooms', 'staff', roomsPage);
route('/tenants', 'staff', tenantsPage);
route('/reports', 'staff', reportsPage);
route('/staff', 'staff', staffPage);
route('/activity', 'staff', activityPage);
route('/profile', 'staff', profilePage);
route('/me', 'tenant', meHomePage);
route('/me/invoices', 'tenant', meInvoicesPage);
route('/me/contracts', 'tenant', meContractsPage);
route('/me/notifications', 'tenant', meNotificationsPage);

initMotionPref();
const app = document.getElementById('app')!;
let shell: (StaffShell | TenantShell) & { kind?: string } | null = null;
let shellKind: 'staff' | 'tenant' | null = null;
let offMonth: (() => void) | null = null;

async function render(): Promise<void> {
  const s = loadSession();
  const { path } = current();
  if (!s) {
    shell = null;
    shellKind = null;
    if (path !== '/login') history.replaceState(null, '', '#/login');
    renderLogin(app, () => {
      history.replaceState(null, '', isTenant(loadSession()) ? '#/me' : '#/');
      void render();
    });
    return;
  }
  const tenant = isTenant(s);
  if (path === '/login' || (tenant && !path.startsWith('/me')) || (!tenant && path.startsWith('/me'))) {
    navigate(tenant ? '/me' : '/');
    return;
  }
  const kind = tenant ? 'tenant' : 'staff';
  if (shellKind !== kind || !shell) {
    shell = tenant ? buildTenantShell(s) : buildStaffShell(s);
    shellKind = kind;
    app.replaceChildren(shell.root);
  }
  const m = match(path);
  const view = shell.view;
  shell.setActive(path);
  if (offMonth) {
    offMonth();
    offMonth = null;
  }
  if (!m || m.route.area !== kind) {
    view.replaceChildren(h('div', { class: 'empty' }, h('h3', null, 'Không tìm thấy trang'), h('a', { class: 'btn secondary', href: tenant ? '#/me' : '#/' }, 'Về trang chủ')));
    return;
  }
  if (!tenant && !STAFF_ROLES.some((r) => can(r))) return;
  const run = async () => {
    view.replaceChildren(skeletonRows(6));
    try {
      await m.route.handler(view, m.params, current().query);
      enterView(view);
    } catch (e) {
      view.replaceChildren(errorState(errText(e), () => void run()));
    }
    if (document.activeElement === document.body) view.focus({ preventScroll: true });
  };
  if (kind === 'staff') offMonth = onMonth(() => void run());
  window.scrollTo({ top: 0 });
  await run();
}

window.addEventListener('hashchange', () => void render());
void render();
