import { h } from '../lib/dom';
import { icon, IconName } from '../lib/icons';
import { Role, Session, can, errText, loadPrivateImage, logout, loadSession, myAvatarUrl, onMyProfile } from '../lib/api';
import { getMonth, setMonth, onMonth } from '../lib/ctx';
import { PROPERTY_ID, setProperty } from '../lib/api';
import { loadProperties } from '../data/live';
import { monthLabel, shiftMonth, initials } from '../lib/format';
import { notificationBell } from './bell';
import { toast } from './kit';

export function logo(size = 32): HTMLElement {
  // Ngôi nhà vàng, chữ S là một tuyến đi qua hai trạm: dấu hiệu của Stayly (cùng hình với favicon trong index.html)
  const el = h('span', { 'aria-hidden': 'true', style: `display:inline-block;flex:none;width:${size}px;height:${size}px;line-height:0` });
  el.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="100%" height="100%"><path d="M32 5 58 25.5V52a7 7 0 0 1-7 7H13a7 7 0 0 1-7-7V25.5Z" fill="#F4C20D" stroke="#F4C20D" stroke-width="5" stroke-linejoin="round"/><path d="M43 31H29a5.5 5.5 0 0 0 0 11h6a5.5 5.5 0 0 1 0 11H21" fill="none" stroke="#16231D" stroke-width="5" stroke-linecap="round"/><circle cx="43" cy="31" r="4.6" fill="#16231D"/><circle cx="43" cy="31" r="1.9" fill="#F4C20D"/><circle cx="21" cy="53" r="4.6" fill="#16231D"/><circle cx="21" cy="53" r="1.9" fill="#F4C20D"/></svg>';
  return el;
}

interface NavItem { href: string; label: string; icon: IconName; roles?: Role[]; badge?: boolean }
const TOP: NavItem = { href: '#/', label: 'Tổng quan', icon: 'dash', roles: ['LANDLORD', 'MANAGER', 'CASHIER'] };
const GROUPS: Array<{ title: string; open: boolean; items: NavItem[] }> = [
  {
    title: 'Chốt tháng',
    open: true,
    items: [
      { href: '#/meters', label: 'Chỉ số điện nước', icon: 'water', roles: ['LANDLORD', 'MANAGER', 'METER_READER'] },
      { href: '#/billing', label: 'Tính tiền tháng', icon: 'calc', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/invoices', label: 'Hóa đơn', icon: 'receipt', roles: ['LANDLORD', 'MANAGER', 'CASHIER'], badge: true },
      { href: '#/payments', label: 'Thanh toán', icon: 'wallet', roles: ['LANDLORD', 'MANAGER', 'CASHIER'] },
      { href: '#/debts', label: 'Công nợ', icon: 'alert', roles: ['LANDLORD', 'MANAGER', 'CASHIER'] },
    ],
  },
  {
    title: 'Quản lý',
    open: true,
    items: [
      { href: '#/checkin', label: 'Nhận khách', icon: 'key', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/contracts', label: 'Hợp đồng', icon: 'file', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/checkout', label: 'Trả phòng', icon: 'logout', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/rooms', label: 'Phòng', icon: 'bed', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/tenants', label: 'Khách hàng', icon: 'users', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/maintenance', label: 'Sửa chữa', icon: 'wrench', roles: ['LANDLORD', 'MANAGER'] },
    ],
  },
  {
    title: 'Báo cáo',
    open: false,
    items: [
      { href: '#/reports', label: 'Doanh thu và chi phí', icon: 'chart', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/expenses', label: 'Chi phí vận hành', icon: 'banknote', roles: ['LANDLORD', 'MANAGER'] },
    ],
  },
  {
    title: 'Hệ thống',
    open: false,
    items: [
      { href: '#/staff', label: 'Nhân viên', icon: 'user', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/settings', label: 'Cài đặt', icon: 'settings', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/activity', label: 'Nhật ký thao tác', icon: 'history', roles: ['LANDLORD'] },
      { href: '#/status', label: 'Trạng thái server', icon: 'zap', roles: ['LANDLORD'] },
      { href: '#/profile', label: 'Tài khoản của tôi', icon: 'key' },
    ],
  },
];

export const ROLE_NAMES: Record<string, string> = {
  LANDLORD: 'Chủ trọ', MANAGER: 'Quản lý', CASHIER: 'Thu ngân', METER_READER: 'Ghi chỉ số', TENANT: 'Người thuê',
};

/** Ảnh đại diện nếu có, không thì chữ cái đầu của tên. */
function paintAvatar(el: HTMLElement, name: string): void {
  const url = myAvatarUrl();
  el.textContent = initials(name);
  if (url) {
    const img = h('img', { alt: '' });
    img.addEventListener('load', () => {
      if (myAvatarUrl() === url) el.replaceChildren(img);
    }, { once: true });
    loadPrivateImage(img, url);
  }
}

function signOut(): void {
  void logout().catch((e) => { if (loadSession()) toast(errText(e), 'err'); });
}

export interface StaffShell { root: HTMLElement; view: HTMLElement; setActive(path: string): void }

export function buildStaffShell(s: Session): StaffShell {
  const view = h('main', { class: 'content', id: 'view', tabindex: '-1' });
  const links: HTMLAnchorElement[] = [];
  const rail = h('aside', { class: 'rail', 'aria-label': 'Điều hướng chính' },
    h('a', { class: 'brand', href: '#/' }, logo(36), h('div', null, h('b', null, 'Stayly'), h('small', null, 'Quản lý nhà trọ'))),
  );
  const nav = h('nav', { class: 'rail-nav', 'aria-label': 'Menu' });
  const mkLink = (it: NavItem) => {
    const a = h('a', { class: 'nav-link', href: it.href }, icon(it.icon), h('span', null, it.label),
      it.badge ? h('span', { class: 'count', 'data-overdue-count': '', hidden: true, title: 'Hóa đơn quá hạn' }, '0') : null) as HTMLAnchorElement;
    links.push(a);
    return a;
  };
  if (!TOP.roles || can(...TOP.roles)) nav.appendChild(mkLink(TOP));
  for (const g of GROUPS) {
    const items = g.items.filter((i) => !i.roles || can(...i.roles));
    if (!items.length) continue;
    const sec = h('details', { class: 'nav-sec', open: g.open }, h('summary', { class: 'nav-group' }, h('span', null, g.title), icon('right', 14)));
    for (const it of items) sec.appendChild(mkLink(it));
    nav.appendChild(sec);
  }
  rail.appendChild(nav);
  const avatarEl = h('span', { class: 'avatar' });
  const whoName = h('b', null, s.name);
  const paintWho = () => { const n = loadSession()?.name ?? s.name; whoName.textContent = n; paintAvatar(avatarEl, n); };
  paintWho();
  const offProfile = onMyProfile(() => (rail.isConnected ? paintWho() : offProfile()));
  rail.appendChild(h('div', { class: 'rail-foot' },
    h('div', { class: 'rail-user' }, avatarEl, h('div', null, whoName, h('span', null, s.roles.map((r) => ROLE_NAMES[r] ?? r).join(', ')))),
    h('button', { class: 'nav-link', style: 'border:0;background:none;width:100%;text-align:left;font:inherit', onclick: signOut }, icon('logout'), h('span', null, 'Đăng xuất')),
  ));

  const out = h('output', null, monthLabel(getMonth()));
  onMonth(() => (out.textContent = monthLabel(getMonth())));
  const bell = notificationBell();
  const searchBox = h('label', { class: 'search' }, icon('search', 16), h('span', { class: 'sr-only' }, 'Tìm hóa đơn hoặc hợp đồng'),
    h('input', { type: 'search', placeholder: 'Tìm mã hóa đơn, hợp đồng, phòng…', onkeydown: (e: KeyboardEvent) => {
      const v = (e.target as HTMLInputElement).value.trim();
      if (e.key === 'Enter' && v) { location.hash = `#/invoices?q=${encodeURIComponent(v)}`; searchBox.classList.remove('open'); }
      if (e.key === 'Escape') searchBox.classList.remove('open');
    } }));
  const searchBtn = h('button', { class: 'icon-btn search-toggle', 'aria-label': 'Tìm kiếm', onclick: () => { searchBox.classList.toggle('open'); (searchBox.querySelector('input') as HTMLInputElement).focus(); } }, icon('search'));
  const topbar = h('header', { class: 'topbar' },
    h('button', { class: 'icon-btn menu-btn', 'aria-label': 'Mở menu', onclick: () => rail.classList.toggle('open') }, icon('menu')),
    h('div', { class: 'ctx' },
      propertyPicker(),
      h('div', { class: 'month-switch', role: 'group', 'aria-label': 'Chọn tháng', title: 'Tháng làm việc: áp dụng cho Tổng quan, Tính tiền, Hóa đơn, Thanh toán và Báo cáo' },
        h('button', { 'aria-label': 'Tháng trước', onclick: () => setMonth(shiftMonth(getMonth(), -1)) }, icon('left', 16)), out,
        h('button', { 'aria-label': 'Tháng sau', onclick: () => setMonth(shiftMonth(getMonth(), 1)) }, icon('right', 16))),
    ),
    searchBox,
    searchBtn,
    bell,
  );

  const skip = h('a', { class: 'skip-link', href: '#view', onclick: (e: Event) => { e.preventDefault(); document.getElementById('view')?.focus(); } }, 'Bỏ qua điều hướng');
  const root = h('div', { class: 'app' }, skip, rail, h('div', { class: 'main' }, topbar, view));
  rail.addEventListener('click', (e) => {
    if ((e.target as HTMLElement).closest('a')) rail.classList.remove('open');
  });
  return {
    root,
    view,
    setActive(path: string) {
      for (const a of links) {
        const href = a.getAttribute('href')!.slice(1);
        const on = href === '/' ? path === '/' : path === href || path.startsWith(href + '/');
        if (on) {
          a.setAttribute('aria-current', 'page');
          a.closest('details')?.setAttribute('open', '');
        } else a.removeAttribute('aria-current');
      }
    },
  };
}

// ---------- Khung người thuê (điện thoại) ----------
export interface TenantShell { root: HTMLElement; view: HTMLElement; title: HTMLElement; setActive(path: string): void }

export function buildTenantShell(s: Session): TenantShell {
  const view = h('main', { class: 'm-body', id: 'view', tabindex: '-1' });
  const title = h('h1', null, 'Stayly');
  const tabs: Array<[string, string, IconName]> = [
    ['#/me', 'Trang chủ', 'home'],
    ['#/me/invoices', 'Hóa đơn', 'receipt'],
    ['#/me/contracts', 'Hợp đồng', 'file'],
    ['#/me/support', 'Hỗ trợ', 'settings'],
    ['#/me/notifications', 'Thông báo', 'bell'],
  ];
  const links = tabs.map(([href, label, ic]) => h('a', { href }, icon(ic, 22), label) as HTMLAnchorElement);
  const who = h('span', { class: 'm-who' }, s.name);
  const offProfile = onMyProfile(() => (root.isConnected ? (who.textContent = loadSession()?.name ?? s.name) : offProfile()));
  const root = h('div', { class: 'm-app' },
    h('header', { class: 'm-top' }, logo(28), h('b', { class: 'm-brand' }, 'Stayly'), title,
      who,
      h('button', { class: 'icon-btn', 'aria-label': `Đăng xuất ${s.name}`, onclick: signOut }, icon('logout'))),
    view,
    h('nav', { class: 'm-tabs', 'aria-label': 'Điều hướng' }, ...links),
  );
  return {
    root, view, title,
    setActive(path: string) {
      for (const a of links) {
        const href = a.getAttribute('href')!.slice(1);
        const on = href === '/me' ? path === '/me' : path.startsWith(href);
        if (on) a.setAttribute('aria-current', 'page');
        else a.removeAttribute('aria-current');
      }
    },
  };
}

/** Ô chọn khu trọ đang làm việc. Đổi khu thì nhớ lựa chọn rồi tải lại trang để mọi màn lấy dữ liệu của khu mới. */
function propertyPicker(): HTMLElement {
  const sel = h('select', { class: 'ctx-chip prop-select', 'aria-label': 'Chọn khu trọ', title: 'Khu trọ đang quản lý' },
    h('option', { value: String(PROPERTY_ID) }, 'Khu trọ…')) as HTMLSelectElement;
  sel.addEventListener('change', () => {
    setProperty(Number(sel.value));
    location.reload(); // giữ nguyên màn đang xem, chỉ nạp lại dữ liệu của khu mới
  });
  loadProperties().then((list) => {
    if (!list.length) { sel.hidden = true; return; }
    if (!list.some((p) => p.propertyId === PROPERTY_ID)) { // khu đã lưu không còn: về khu đầu tiên
      setProperty(list[0].propertyId);
      location.reload();
      return;
    }
    sel.replaceChildren(...list.map((p) => h('option', { value: String(p.propertyId) }, `${p.propertyName} (${p.roomCount} phòng)`)));
    sel.value = String(PROPERTY_ID);
  }).catch(() => { sel.hidden = true; }); // người thuê không có quyền xem danh sách khu
  return sel;
}
