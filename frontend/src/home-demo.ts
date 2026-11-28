import { h } from './lib/dom';
import { vnd2 } from './lib/format';

type Month = '10' | '11' | '12';
type View = 'overview' | 'meters' | 'billing' | 'invoices' | 'payments' | 'contracts';
type Room = (typeof rooms)[number];

const rooms = [
  { code: 'P.203', rent: 2300000, utilities: 190000, service: 100000, meter: 328, units: 46, water: 3, start: '01/09/2026' },
  { code: 'P.204', rent: 1800000, utilities: 160000, service: 40000, meter: 142, units: 38, water: 2, start: '12/09/2026' },
  { code: 'P.305', rent: 2400000, utilities: 220000, service: 80000, meter: 508, units: 54, water: 4, start: '01/10/2026' },
];
const months: Record<Month, { step: number; paid: number[] }> = {
  '10': { step: 4, paid: [2590000, 2000000, 2700000] },
  '11': { step: 3, paid: [0, 800000, 2700000] },
  '12': { step: 3, paid: [0, 0, 0] },
};
const stepViews: View[] = ['meters', 'billing', 'billing', 'invoices', 'payments'];
const views: Record<View, { heading: string; list: string; columns: [string, string] }> = {
  overview: { heading: 'Tổng quan', list: 'Hóa đơn cần theo dõi', columns: ['Còn phải thu', 'Trạng thái'] },
  meters: { heading: 'Chỉ số điện nước', list: 'Điện nước theo phòng', columns: ['Điện dùng', 'Nước dùng'] },
  billing: { heading: 'Tính tiền tháng', list: 'Bảng tính tiền', columns: ['Tổng tiền', 'Trạng thái'] },
  invoices: { heading: 'Hóa đơn', list: 'Hóa đơn theo phòng', columns: ['Còn phải thu', 'Trạng thái'] },
  payments: { heading: 'Thanh toán', list: 'Theo dõi tiền thu', columns: ['Đã thu', 'Còn phải thu'] },
  contracts: { heading: 'Hợp đồng', list: 'Hợp đồng đang thuê', columns: ['Ngày bắt đầu', 'Trạng thái'] },
};

const total = (room: Room) => room.rent + room.utilities + room.service;
const plate = (room: Room) => h('span', { class: 'home-plate' }, room.code);
const status = (style: string, label: string) => h('span', { class: `home-status home-status-${style}` }, label);

export function initHomeDemo(root: HTMLElement): void {
  const menu = [...root.querySelectorAll<HTMLButtonElement>('[data-demo-view]')];
  const stations = [...root.querySelectorAll<HTMLButtonElement>('.home-dash-route button')];
  const monthPicker = root.querySelector<HTMLSelectElement>('#home-demo-month')!;
  const heading = root.querySelector<HTMLElement>('#home-demo-heading')!;
  const panel = root.querySelector<HTMLElement>('#home-demo-panel')!;
  const message = root.querySelector<HTMLElement>('#home-demo-message')!;
  const recorded = new Set<string>();

  let view: View = 'overview';
  let month: Month = '11';
  let activeStep = months[month].step;
  let focusedRoom: Room | null = null;
  let animation: Animation | undefined;

  const paymentKey = (room: Room) => `${month}/${room.code}`;
  const paid = (room: Room) => recorded.has(paymentKey(room)) ? total(room) : months[month].paid[rooms.indexOf(room)];
  const balance = (room: Room) => total(room) - paid(room);
  const meterStart = (room: Room) => room.meter + (Number(month) - 11) * room.units;

  function columns(room: Room): [string, string | HTMLElement] {
    switch (view) {
      case 'meters': return [`${room.units} kWh`, `${room.water} m³`];
      case 'billing': return [vnd2(total(room)), activeStep === 1 ? status('pending', 'Chờ duyệt') : status('paid', 'Đã duyệt')];
      case 'payments': return [vnd2(paid(room)), vnd2(balance(room))];
      case 'contracts': return [room.start, status('paid', 'Đang thuê')];
      default: return [vnd2(balance(room)), balance(room) === 0 ? status('paid', 'Đã trả') :
        paid(room) > 0 ? status('partial', 'Trả một phần') : status('pending', 'Chưa trả')];
    }
  }

  function details(room: Room): [string, string][] {
    switch (view) {
      case 'meters': return [['Chỉ số điện đầu kỳ', `${meterStart(room)} kWh`], ['Chỉ số điện cuối kỳ', `${meterStart(room) + room.units} kWh`], ['Điện tiêu thụ', `${room.units} kWh`], ['Nước tiêu thụ', `${room.water} m³`]];
      case 'billing': return [['Tiền phòng', vnd2(room.rent)], ['Điện, nước và dịch vụ', vnd2(room.utilities + room.service)], ['Tổng tiền', vnd2(total(room))]];
      case 'contracts': return [['Mã hợp đồng', `HĐ-${room.code.slice(2)}`], ['Ngày bắt đầu', room.start], ['Tiền phòng / tháng', vnd2(room.rent)]];
      default: return [['Tiền hóa đơn', vnd2(total(room))], ['Đã thanh toán', vnd2(paid(room))], ['Còn phải thu', vnd2(balance(room))]];
    }
  }

  function renderList(): void {
    const screen = views[view];
    const listTitle = view === 'billing' && activeStep === 2 ? 'Rà soát và duyệt kỳ' : screen.list;
    const rows = rooms.map((room) => {
      const [value, state] = columns(room);
      return h('tr', null,
        h('th', { scope: 'row' }, h('button', {
          class: 'home-demo-room', type: 'button', 'data-room': room.code, 'aria-label': `Xem chi tiết phòng ${room.code}`,
          onclick: () => {
            focusedRoom = room;
            paint();
            panel.querySelector<HTMLButtonElement>('.home-demo-back')?.focus();
          },
        }, plate(room))), h('td', null, value), h('td', null, state));
    });
    panel.replaceChildren(h('strong', null, listTitle), h('table', { 'aria-label': listTitle },
      h('thead', null, h('tr', null, ['Phòng', ...screen.columns].map((column) => h('th', { scope: 'col' }, column)))),
      h('tbody', null, rows)));
  }

  function renderDetails(room: Room): void {
    const back = h('button', { type: 'button', class: 'home-demo-back', onclick: () => {
      focusedRoom = null;
      paint();
      panel.querySelector<HTMLButtonElement>(`[data-room="${room.code}"]`)?.focus();
    } }, 'Về danh sách');
    const record = view === 'payments' && balance(room) > 0 ? h('button', {
      type: 'button', class: 'home-demo-record', onclick: () => {
        recorded.add(paymentKey(room));
        paint(`Đã ghi nhận đủ tiền phòng ${room.code} trong dữ liệu mẫu.`);
        panel.querySelector<HTMLButtonElement>('.home-demo-back')?.focus();
      },
    }, 'Ghi nhận đủ (mẫu)') : null;
    const confirmation = view === 'payments' && recorded.has(paymentKey(room)) ? h('span', { class: 'home-demo-confirmation' }, 'Đã ghi nhận · Dữ liệu mẫu') : null;
    panel.replaceChildren(h('div', { class: 'home-demo-detail' },
      h('div', { class: 'home-demo-detail-head' }, plate(room), back),
      h('dl', null, details(room).map(([label, value]) => h('div', null, h('dt', null, label), h('dd', null, value)))),
      record || confirmation));
  }

  function paint(announcement?: string, animate = true): void {
    heading.textContent = views[view].heading;
    menu.forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.demoView === view)));
    stations.forEach((button, index) => {
      button.setAttribute('aria-pressed', String(index === activeStep));
      button.classList.toggle('is-upcoming', index > activeStep);
    });
    if (focusedRoom) renderDetails(focusedRoom);
    else renderList();
    if (announcement) message.textContent = announcement;
    if (!animate) return;
    animation?.cancel();
    animation = panel.animate([{ opacity: .76, transform: 'translateY(4px)' }, { opacity: 1, transform: 'none' }],
      { duration: 190, easing: 'cubic-bezier(.16, 1, .3, 1)' });
  }

  function changeView(next: View, step: number): void {
    view = next;
    activeStep = step;
    focusedRoom = null;
    paint(`${views[view].heading}, tháng ${month}.`);
  }

  [...menu, ...stations, monthPicker].forEach((control) => { control.disabled = false; });
  menu.forEach((button) => button.addEventListener('click', () => {
    const next = button.dataset.demoView as View;
    const step = stepViews.indexOf(next);
    changeView(next, step < 0 ? months[month].step : step);
  }));
  stations.forEach((button, index) => button.addEventListener('click', () => changeView(stepViews[index], index)));
  monthPicker.addEventListener('change', () => {
    month = monthPicker.value as Month;
    changeView(view, view === 'overview' || view === 'contracts' ? months[month].step : activeStep);
  });
  paint(undefined, false);
}
