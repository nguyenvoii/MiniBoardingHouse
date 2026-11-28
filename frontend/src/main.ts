import './styles/layout.css';
import './styles/components.css';
import './styles/motion.css';

import { h } from './lib/dom';
import { loadSession, isTenant, isMeterReaderOnly, mustChangePassword, can, errText, refreshMyProfile } from './lib/api';
import { route, match, current, navigate } from './lib/router';
import { onMonth, getMonth } from './lib/ctx';
import { api, PROPERTY_ID } from './lib/api';
import type { Period } from './data/types';
import { enterView, initMotionPref } from './lib/motion';
import { monthRouteBar } from './ui/monthRoute';
import { buildStaffShell, buildTenantShell, StaffShell, TenantShell } from './ui/shell';
import { closeAppDialogs, errorState, skeletonRows } from './ui/kit';
import { renderLogin, forcePasswordChange } from './pages/login';

import { dashboardPage } from './pages/dashboard';
import { billingPage, billingDetailPage } from './pages/billing';
import { invoicesPage } from './pages/invoices';
import { paymentsPage } from './pages/payments';
import { contractsPage } from './pages/contracts';
import { checkoutPage } from './pages/checkout';
import { checkinPage } from './pages/checkin';
import { roomsPage } from './pages/rooms';
import { tenantsPage } from './pages/tenants';
import { reportsPage } from './pages/reports';
import { staffPage } from './pages/staff';
import { activityPage } from './pages/activity';
import { statusPage } from './pages/status';
import { profilePage } from './pages/profile';
import { meHomePage, meInvoicesPage, meContractsPage, meNotificationsPage, meSupportPage } from './pages/me';
import { metersPage } from './pages/meters';
import { settingsPage } from './pages/settings';
import { sharedInvoicePage } from './pages/shared';
import { expensesPage } from './pages/expenses';
import { debtsPage } from './pages/debts';
import { maintenancePage } from './pages/maintenance';

const TITLES: Record<string, string> = {
  '/': 'Tổng quan', '/meters': 'Chỉ số điện nước', '/billing': 'Tính tiền tháng', '/invoices': 'Hóa đơn', '/payments': 'Thanh toán', '/debts': 'Công nợ',
  '/maintenance': 'Sửa chữa', '/contracts': 'Hợp đồng', '/checkin': 'Nhận khách thuê phòng', '/checkout': 'Trả phòng', '/rooms': 'Phòng', '/tenants': 'Khách hàng', '/reports': 'Doanh thu và chi phí', '/expenses': 'Chi phí vận hành',
  '/staff': 'Nhân viên', '/settings': 'Cài đặt', '/activity': 'Nhật ký thao tác', '/status': 'Trạng thái server', '/profile': 'Tài khoản của tôi', '/login': 'Đăng nhập',
  '/me': 'Trang chủ', '/me/invoices': 'Hóa đơn của tôi', '/me/contracts': 'Hợp đồng của tôi', '/me/support': 'Hỗ trợ', '/me/notifications': 'Thông báo',
};
const ROUTE_PAGES: Record<string, number> = { '/meters': 0, '/billing': 1, '/invoices': 3, '/payments': 4, '/debts': 4 };
const STAFF_ROLES = ['LANDLORD', 'MANAGER', 'CASHIER', 'METER_READER'] as const;
/** Vai trò được mở từng màn (khớp menu và backend). Màn không liệt kê thì mọi nhân viên mở được. */
const PATH_ROLES: Record<string, Array<(typeof STAFF_ROLES)[number]>> = {
  '/': ['LANDLORD', 'MANAGER', 'CASHIER'], '/meters': ['LANDLORD', 'MANAGER', 'METER_READER'], '/billing': ['LANDLORD', 'MANAGER'],
  '/invoices': ['LANDLORD', 'MANAGER', 'CASHIER'], '/payments': ['LANDLORD', 'MANAGER', 'CASHIER'], '/debts': ['LANDLORD', 'MANAGER', 'CASHIER'],
  '/checkin': ['LANDLORD', 'MANAGER'], '/contracts': ['LANDLORD', 'MANAGER'], '/checkout': ['LANDLORD', 'MANAGER'], '/rooms': ['LANDLORD', 'MANAGER'],
  '/tenants': ['LANDLORD', 'MANAGER'], '/maintenance': ['LANDLORD', 'MANAGER'], '/reports': ['LANDLORD', 'MANAGER'], '/expenses': ['LANDLORD', 'MANAGER'],
  '/staff': ['LANDLORD', 'MANAGER'], '/settings': ['LANDLORD', 'MANAGER'], '/activity': ['LANDLORD'], '/status': ['LANDLORD'],
};

route('/', 'staff', dashboardPage);
route('/billing', 'staff', billingPage);
route('/billing/:id', 'staff', billingDetailPage);
route('/invoices', 'staff', invoicesPage);
route('/payments', 'staff', paymentsPage);
route('/contracts', 'staff', contractsPage);
route('/checkin', 'staff', checkinPage);
route('/checkout', 'staff', checkoutPage);
route('/rooms', 'staff', roomsPage);
route('/tenants', 'staff', tenantsPage);
route('/reports', 'staff', reportsPage);
route('/staff', 'staff', staffPage);
route('/activity', 'staff', activityPage);
route('/status', 'staff', statusPage);
route('/profile', 'staff', profilePage);
route('/meters', 'staff', metersPage);
route('/settings', 'staff', settingsPage);
route('/expenses', 'staff', expensesPage);
route('/debts', 'staff', debtsPage);
route('/maintenance', 'staff', maintenancePage);
route('/me', 'tenant', meHomePage);
route('/me/support', 'tenant', meSupportPage);
route('/me/invoices', 'tenant', meInvoicesPage);
route('/me/contracts', 'tenant', meContractsPage);
route('/me/notifications', 'tenant', meNotificationsPage);

initMotionPref();
const app = document.getElementById('app')!;
let shell: (StaffShell | TenantShell) & { kind?: string } | null = null;
let shellKind: 'staff' | 'tenant' | null = null;
let offMonth: (() => void) | null = null;
let routeObserver: MutationObserver | null = null;

function workspacePath(session: ReturnType<typeof loadSession>): string {
  return isTenant(session) ? '/me' : isMeterReaderOnly(session) ? '/meters' : '/';
}

/** Thanh «Tuyến tháng» thu gọn ở đầu các màn chốt tháng; chèn lại nếu trang tự vẽ lại sau một thao tác. */
function keepMonthRoute(view: HTMLElement, path: string): void {
  routeObserver?.disconnect();
  routeObserver = null;
  const here = ROUTE_PAGES[path];
  if (here === undefined || isTenant(loadSession())) return;
  let busy = false;
  const ensure = async () => {
    if (busy || view.querySelector('.route.compact')) return;
    const head = view.querySelector('.page-head');
    if (!head) return;
    busy = true;
    const bar = await monthRouteBar(getMonth(), here);
    if (bar && head.isConnected && !view.querySelector('.route.compact') && current().path === path) head.after(bar);
    busy = false;
  };
  routeObserver = new MutationObserver(() => void ensure());
  routeObserver.observe(view, { childList: true });
  void ensure();
}

async function render(): Promise<void> {
  if (!location.hash.startsWith('#/')) {
    closeAppDialogs();
    offMonth?.();
    offMonth = null;
    routeObserver?.disconnect();
    routeObserver = null;
    return;
  }
  const s = loadSession();
  const { path } = current();
  const shareMatch = /^\/share\/([\w-]+)$/.exec(path);
  if (shareMatch) { shell = null; shellKind = null; await sharedInvoicePage(app, shareMatch[1]); return; } // xem hóa đơn qua liên kết, không cần đăng nhập
  if (!s) {
    shell = null;
    shellKind = null;
    document.title = 'Đăng nhập · Stayly';
    if (path !== '/login') history.replaceState(null, '', '#/login');
    renderLogin(app, () => {
      if (!location.hash.startsWith('#/')) return;
      history.replaceState(null, '', `#${workspacePath(loadSession())}`);
      void render();
    });
    return;
  }
  const tenant = isTenant(s);
  const inTenantArea = path === '/me' || path.startsWith('/me/');
  if (path === '/login') {
    history.replaceState(null, '', `#${workspacePath(s)}`);
    return render();
  }
  if ((tenant && !inTenantArea) || (!tenant && inTenantArea)) {
    navigate(tenant ? '/me' : '/');
    return;
  }
  if (path === '/' && isMeterReaderOnly(s)) {
    navigate('/meters'); // người ghi chỉ số không có Tổng quan: vào thẳng màn họ dùng được
    return;
  }
  const kind = tenant ? 'tenant' : 'staff';
  if (shellKind !== kind || !shell) {
    shell = tenant ? buildTenantShell(s) : buildStaffShell(s);
    shellKind = kind;
    app.replaceChildren(shell.root);
    void refreshMyProfile();
  }
  if (mustChangePassword()) forcePasswordChange();
  const m = match(path);
  document.title = `${TITLES[path] ?? (path.startsWith('/billing/') ? 'Kỳ tính tiền' : 'Stayly')} · Stayly`.replace('Stayly · Stayly', 'Stayly');
  // Trang cũ có thể vẫn đang tải khi người dùng chuyển màn.
  let view = shell.view.cloneNode(false) as HTMLElement;
  shell.view.replaceWith(view);
  shell.view = view;
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
  const base = '/' + (path.split('/')[1] ?? '');
  const allowed = PATH_ROLES[path === '/' ? '/' : base];
  if (!tenant && allowed && !allowed.some((r) => can(r))) {
    view.replaceChildren(h('div', { class: 'empty' }, h('h2', { class: 'empty-title' }, 'Bạn không có quyền mở màn này'), h('p', null, 'Màn này dành cho chủ trọ hoặc quản lý. Hãy dùng các mục có trong menu bên trái.'), h('a', { class: 'btn secondary', href: isMeterReaderOnly(s) ? '#/meters' : '#/' }, 'Về màn của bạn')));
    return;
  }
  const run = async () => {
    if (!view.isConnected) return;
    // Mỗi lần tải dùng một khung mới: đổi tháng liên tiếp thì lần tải cũ ghi vào khung đã bỏ, không đè kết quả mới.
    const v = view.cloneNode(false) as HTMLElement;
    view.replaceWith(v);
    view = v;
    if (shell) shell.view = v;
    routeObserver?.disconnect(); // ngắt theo dõi của trang trước, kẻo nó chèn thanh tuyến vào trang mới
    routeObserver = null;
    v.replaceChildren(skeletonRows(6));
    try {
      await m.route.handler(v, m.params, current().query);
      if (!v.isConnected || current().path !== path) return;
      enterView(v);
      keepMonthRoute(v, path);
    } catch (e) {
      if (!v.isConnected) return;
      v.replaceChildren(errorState(errText(e), () => void run()));
    }
    if (v.isConnected && document.activeElement === document.body) v.focus({ preventScroll: true });
  };
  // Chọn tháng chỉ có nghĩa ở các màn tính theo tháng; màn khác thì ẩn đi để khỏi gây hiểu nhầm.
  const monthPages = ['/', '/billing', '/meters', '/invoices', '/payments', '/expenses', '/reports'];
  const aware = kind === 'staff' && monthPages.some((p) => (p === '/' ? path === '/' : path === p || path.startsWith(p + '/')));
  document.body.dataset.monthAware = String(aware);
  if (kind === 'staff' && path.startsWith('/billing/')) {
    // đang mở một kỳ: đổi tháng thì chuyển tới kỳ của tháng đó, chưa có kỳ thì về danh sách
    offMonth = onMonth(async () => {
      const ps = await api.get<Period[]>(`/api/billing/periods?propertyId=${PROPERTY_ID}`).catch(() => [] as Period[]);
      const p = ps.find((x) => x.monthYear === getMonth());
      navigate(p ? `/billing/${p.billingPeriodId}` : '/billing');
    });
  } else if (kind === 'staff') offMonth = onMonth(() => void run());
  window.scrollTo({ top: 0 });
  await run();
}

window.addEventListener('hashchange', () => void render());
void render();
