import { h } from '../lib/dom';
import { icon, IconName } from '../lib/icons';
import { Role, Session, can, logout } from '../lib/api';
import { getMonth, setMonth, onMonth } from '../lib/ctx';
import { monthLabel, shiftMonth, initials } from '../lib/format';
import { notificationBell } from './bell';

export function logo(size = 32): HTMLElement {
  // Biển vàng bo góc: dấu hiệu của Stayly
  const el = h('span', { 'aria-hidden': 'true', style: `display:inline-grid;place-items:center;width:${size}px;height:${size}px;border-radius:${size * 0.24}px;background:var(--plate);border:2px solid var(--plate-ink);` });
  el.appendChild(h('span', { style: `width:${size * 0.34}px;height:${size * 0.34}px;border-radius:50%;background:var(--rail);border:${Math.max(2, size * 0.09)}px solid var(--plate-ink);box-sizing:border-box` }));
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
      { href: '#/contracts', label: 'Hợp đồng', icon: 'file', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/rooms', label: 'Phòng', icon: 'bed', roles: ['LANDLORD', 'MANAGER'] },
      { href: '#/tenants', label: 'Khách thuê', icon: 'users', roles: ['LANDLORD', 'MANAGER'] },
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
      { href: '#/profile', label: 'Tài khoản của tôi', icon: 'key' },
    ],
  },
];

export const ROLE_NAMES: Record<string, string> = {
  LANDLORD: 'Chủ trọ', MANAGER: 'Quản lý', CASHIER: 'Thu ngân', METER_READER: 'Ghi chỉ số', TENANT: 'Người thuê',
};

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
  rail.appendChild(h('div', { class: 'rail-foot' },
    h('div', { class: 'rail-user' }, h('span', { class: 'avatar' }, initials(s.name)), h('div', null, h('b', null, s.name), h('span', null, s.roles.map((r) => ROLE_NAMES[r] ?? r).join(', ')))),
    h('button', { class: 'nav-link', style: 'border:0;background:none;width:100%;text-align:left;font:inherit', onclick: logout }, icon('logout'), h('span', null, 'Đăng xuất')),
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
      h('span', { class: 'ctx-chip', title: 'Khu trọ đang quản lý' }, icon('building'), 'Khu trọ chính'),
      h('div', { class: 'month-switch', role: 'group', 'aria-label': 'Chọn tháng', title: 'Tháng làm việc: áp dụng cho Tổng quan, Tính tiền, Hóa đơn, Thanh toán và Báo cáo' },
        h('button', { 'aria-label': 'Tháng trước', onclick: () => setMonth(shiftMonth(getMonth(), -1)) }, icon('left', 16)), out,
        h('button', { 'aria-label': 'Tháng sau', onclick: () => setMonth(shiftMonth(getMonth(), 1)) }, icon('right', 16))),
    ),
    searchBox,
    searchBtn,
    bell,
  );

  const skip = h('a', { class: 'skip-link', href: '#view', onclick: (e: Event) => { e.preventDefault(); view.focus(); } }, 'Bỏ qua điều hướng');
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
  const root = h('div', { class: 'm-app' },
    h('header', { class: 'm-top' }, logo(28), title,
      h('button', { class: 'icon-btn', 'aria-label': `Đăng xuất ${s.name}`, onclick: logout }, icon('logout'))),
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
