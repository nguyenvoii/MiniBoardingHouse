import { h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, PROPERTY_ID, can, Page } from '../lib/api';
import { liveContracts, loadLive } from '../data/live';
import { getMonth, setOverdue } from '../lib/ctx';
import { date, money, monthLabel, isoDay } from '../lib/format';
import type { Contract, Dashboard, Invoice, MonthSummary, Period, RevenueReport } from '../data/types';
import { emptyState, pageHead, panel, plate, statusBadge, table } from '../ui/kit';
import { routeLine, Stop } from '../ui/route';

export async function dashboardPage(view: HTMLElement): Promise<void> {
  const month = getMonth();
  const staffOnly = can('LANDLORD', 'MANAGER');
  const [dash, periods, invoices, summary, expiring, revenue] = await Promise.all([
    can('LANDLORD', 'MANAGER') ? api.get<Dashboard>(`/api/dashboard?propertyId=${PROPERTY_ID}`) : Promise.resolve(null),
    staffOnly ? api.get<Period[]>(`/api/billing/periods?propertyId=${PROPERTY_ID}`) : Promise.resolve([] as Period[]),
    api.get<Invoice[]>(`/api/invoices?propertyId=${PROPERTY_ID}&monthYear=${month}`),
    api.get<MonthSummary>(`/api/invoices/summary/${month}?propertyId=${PROPERTY_ID}`).catch(() => null),
    staffOnly ? liveContracts().catch(() => [] as Contract[]) : Promise.resolve(null),
    // cùng nguồn số với màn Báo cáo, để «đã thu» hai màn không lệch nhau
    staffOnly ? api.get<RevenueReport>(`/api/reports/revenue?propertyId=${PROPERTY_ID}&year=${month.slice(0, 4)}`).catch(() => null) : Promise.resolve(null),
  ]);
  const period = periods.find((p) => p.monthYear === month) ?? null;
  const overdueAll = await api.get<Invoice[]>(`/api/invoices?propertyId=${PROPERTY_ID}&status=OVERDUE`).catch(() => [] as Invoice[]);
  const life = staffOnly ? await loadLifecycle() : null;
  setOverdue(overdueAll.length);
  const flow = stage(month, period, invoices);

  view.replaceChildren(
    pageHead('Tổng quan', `${monthLabel(month)} · Khu trọ chính`, [
      staffOnly ? h('a', { class: 'btn', href: '#/checkin' }, icon('key', 16), 'Nhận khách thuê phòng') : null,
      staffOnly && flow.next ? h('a', { class: 'btn secondary', href: flow.next.href }, flow.next.label, icon('arrowRight', 16)) : null,
      flow.cur !== 4 ? h('a', { class: 'btn ghost', href: '#/payments' }, icon('wallet', 16), 'Ghi nhận thanh toán') : null,
    ].filter(Boolean) as Node[]),
    h('div', { class: 'stack' },
      life ? lifecyclePanel(life, overdueAll.length) : null,
      staffOnly ? routePanel(month, period, invoices, flow) : null,
      figures(summary, dash, overdueAll, revenue?.months.find((m) => m.month === Number(month.slice(5)))?.revenue ?? null),
      h('div', { class: 'grid-2' }, attentionPanel(invoices, overdueAll), staffOnly ? expiringPanel(endingSoon(expiring ?? []), dash) : h('div')),
    ),
  );
}

interface Flow { cur: number; stuck: number; next: { label: string; text: string; href: string } | null }

/** Chặng hiện tại của tháng = chặng đầu tiên chưa xong. */
function stage(month: string, period: Period | null, all: Invoice[]): Flow {
  const invoices = all.filter((i) => i.paymentStatus !== 'CANCELLED'); // hóa đơn đã hủy không tính vào chặng Thu tiền
  const issued = invoices.length;
  const paid = invoices.filter((i) => i.paymentStatus === 'PAID').length;
  const hasFees = !!period && period.roomCount > 0;
  const approved = !!period && period.status !== 'DRAFT';
  const flags = [!!period, hasFees, approved, issued > 0, issued > 0 && paid === issued];
  const cur = flags.findIndex((f) => !f);
  const bill = period ? `#/billing/${period.billingPeriodId}` : '#/billing';
  const rooms = period?.roomCount ?? 0;
  const table: Array<{ label: string; text: string; href: string; stuck: number }> = [
    { label: 'Tạo kỳ tính tiền', text: `Tháng ${month.split('-')[1]} chưa có kỳ tính tiền. Tạo kỳ rồi kiểm tra chỉ số điện nước.`, href: '#/billing', stuck: 0 },
    { label: 'Tính tiền', text: 'Kỳ đã tạo nhưng chưa tính. Tính tiền để xem chi tiết từng phòng.', href: bill, stuck: 0 },
    { label: 'Duyệt kỳ', text: 'Kiểm tra các phòng bất thường rồi duyệt để phát hành hóa đơn.', href: bill, stuck: rooms },
    { label: 'Phát hành hóa đơn', text: 'Kỳ đã duyệt. Phát hành hóa đơn cho cả kỳ để người thuê thấy và trả tiền.', href: bill, stuck: Math.max(rooms - issued, 0) },
    { label: 'Ghi nhận thanh toán', text: `Còn ${issued - paid} hóa đơn chưa trả đủ. Ghi nhận thanh toán khi nhận tiền.`, href: '#/payments', stuck: issued - paid },
  ];
  return cur === -1 ? { cur, stuck: 0, next: null } : { cur, stuck: table[cur].stuck, next: table[cur] };
}

function routePanel(month: string, period: Period | null, all: Invoice[], flow: Flow): HTMLElement {
  const invoices = all.filter((i) => i.paymentStatus !== 'CANCELLED');
  const issued = invoices.length;
  const paid = invoices.filter((i) => i.paymentStatus === 'PAID').length;
  const hasFees = !!period && period.roomCount > 0;
  const approved = !!period && period.status !== 'DRAFT';
  const { cur } = flow;
  const state = (i: number): Stop['state'] => (cur === -1 || i < cur ? 'done' : i === cur ? 'current' : 'todo');
  const bill = period ? `#/billing/${period.billingPeriodId}` : '#/billing';
  const stops: Stop[] = [
    { name: 'Chỉ số và kỳ', sub: period ? 'Đã tạo kỳ' : 'Chưa tạo kỳ', state: state(0), href: '#/meters' },
    { name: 'Tính tiền', sub: hasFees ? `${period!.roomCount} phòng · ${money(period!.totalAmount)} ₫` : 'Chưa tính', state: state(1), href: bill },
    { name: 'Duyệt kỳ', sub: approved ? 'Đã duyệt' : period ? 'Chờ duyệt' : '–', state: state(2), href: bill },
    { name: 'Phát hành hóa đơn', sub: issued ? `${issued} hóa đơn` : 'Chưa phát hành', state: state(3), href: '#/invoices' },
    { name: 'Thu tiền', sub: issued ? `${paid}/${issued} đã trả` : '–', state: state(4), href: '#/payments' },
  ];
  stops.forEach((s, i) => { if (i === cur) s.stuck = flow.stuck; });
  return h('section', { class: 'panel route' },
    h('div', { class: 'route-head' },
      h('h2', null, `Chốt tiền ${monthLabel(month).toLowerCase()}`, period ? statusBadge(period.status) : null),
      h('span', { class: 'muted' }, cur === -1 ? 'Đã xong tất cả các bước' : `Còn ${5 - cur} bước`)),
    routeLine(stops),
    flow.next
      ? h('div', { class: 'route-next' }, icon('route', 20), h('p', null, h('b', null, `Việc kế tiếp: ${flow.next.label}. `), flow.next.text))
      : h('div', { class: 'route-next' }, icon('check', 20), h('p', null, h('b', null, 'Tháng này đã thu đủ. '), 'Mọi hóa đơn đã trả, không còn việc tồn.')),
  );
}

function figures(summary: MonthSummary | null, dash: Dashboard | null, overdue: Invoice[], revenue: number | null): HTMLElement {
  const cell = (label: string, value: string, unit: string, note: string, alert = false) =>
    h('div', { class: `figure ${alert ? 'alert' : ''}` }, h('span', { class: 'label' }, label),
      h('span', { class: 'value' }, value, unit ? h('small', null, unit) : null), h('span', { class: 'note' }, note));
  const total = summary?.totalAmount ?? 0;
  const paid = summary?.paidAmount ?? 0;
  return h('div', { class: 'figures' },
    cell('Đã thu tháng này', money(revenue ?? paid), '₫', total ? `trên ${money(total)} ₫ đã lập hóa đơn${revenue !== null ? ', khớp màn Báo cáo' : ''}` : 'Chưa có hóa đơn'),
    cell('Còn phải thu tháng này', money(summary?.remainingAmount ?? 0), '₫', 'trên các hóa đơn của tháng đang xem'),
    linkCell(cell('Hóa đơn quá hạn', String(overdue.length), 'hóa đơn', overdue.length ? `${money(overdue.reduce((a, i) => a + i.remainingAmount, 0))} ₫ chưa trả, mọi tháng. Bấm để xem` : 'Không có hóa đơn quá hạn', overdue.length > 0), '#/invoices?status=OVERDUE'),
    cell('Phòng đang thuê', dash ? `${dash.rentedRooms}/${dash.totalRooms}` : '–', '', dash ? `lấp đầy ${dash.occupancyPercent}%` : 'chỉ chủ trọ và quản lý xem'),
  );
}

function linkCell(cell: HTMLElement, href: string): HTMLElement {
  if (!cell.classList.contains('alert')) return cell;
  const a = h('a', { href, style: 'display:contents;color:inherit;text-decoration:none' }, ...Array.from(cell.childNodes));
  cell.replaceChildren(a);
  return cell;
}

function attentionPanel(invoices: Invoice[], overdueAll: Invoice[]): HTMLElement {
  const byId = new Map<number, Invoice>();
  for (const i of [...overdueAll, ...invoices]) if (i.paymentStatus !== 'PAID' && i.paymentStatus !== 'CANCELLED') byId.set(i.invoiceId, i);
  const rows = [...byId.values()].sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  return panel('Hóa đơn cần theo dõi', table<Invoice>([
    { key: 'room', label: 'Phòng', render: (i) => plate(i.roomCode), sort: (a, b) => a.roomCode.localeCompare(b.roomCode) },
    { key: 'tenant', label: 'Người thuê', render: (i) => i.tenantName },
    { key: 'm', label: 'Tháng', render: (i) => monthLabel(i.monthYear).replace('Tháng ', 'T') },
    { key: 'due', label: 'Hạn', render: (i) => date(i.dueDate), sort: (a, b) => a.dueDate.localeCompare(b.dueDate) },
    { key: 'left', label: 'Còn lại (₫)', num: true, render: (i) => money(i.remainingAmount), sort: (a, b) => a.remainingAmount - b.remainingAmount },
    { key: 'st', label: 'Trạng thái', render: (i) => statusBadge(i.paymentStatus) },
  ], rows, {
    onRow: (i) => (location.hash = `#/invoices?q=${encodeURIComponent(i.invoiceCode)}`),
    empty: emptyState('Không có hóa đơn tồn', 'Tháng này chưa phát hành hóa đơn hoặc đã thu đủ.'),
  }), { sub: 'Chưa trả đủ, kể cả các tháng trước, hạn gần nhất ở trên', flush: true, actions: [h('a', { class: 'btn ghost sm', href: '#/invoices' }, 'Xem tất cả')] });
}

/** Hợp đồng còn hiệu lực và hết hạn trong 30 ngày tới, gần nhất lên trước (cùng quy tắc với con số trên Tổng quan). */
function endingSoon(all: Contract[]): Contract[] {
  const today = isoDay();
  const limit = isoDay(30);
  // gồm cả hợp đồng đã quá hạn mà khách còn ở (EXPIRED chưa thanh lý): endDate âm ngày vẫn là việc cần làm
  return all.filter((c) => (c.endDate >= today && c.endDate <= limit && c.status !== 'EXPIRED') || c.status === 'EXPIRED').sort((a, b) => a.endDate.localeCompare(b.endDate));
}

function daysLeft(c: Contract): number {
  const t = new Date(); t.setHours(0, 0, 0, 0);
  return Math.round((new Date(`${c.endDate}T00:00:00`).getTime() - t.getTime()) / 86400000);
}
function overdueText(c: Contract): string {
  const n = daysLeft(c);
  return n < 0 ? `quá hạn ${-n} ngày (hết hạn ${date(c.endDate)}), khách còn ở` : `hết hạn ${date(c.endDate)}`;
}

function expiringPanel(list: Contract[], _dash: Dashboard | null): HTMLElement {
  const body = list.length
    ? h('div', null, ...list.map((c) => h('a', { class: 'list-row', href: `#/contracts?open=${c.contractId}` },
        plate(c.roomCode), h('div', { class: 'grow' }, h('b', null, c.tenantName), h('span', null, overdueText(c))), icon('right', 16))))
    : emptyState('Không có hợp đồng sắp hết hạn', 'Các hợp đồng còn xa ngày hết hạn, không có hợp đồng quá hạn.');
  return panel('Hợp đồng sắp hết hạn', body, {
    sub: `${list.length} hợp đồng hết hạn trong 30 ngày tới hoặc đã quá hạn chưa thanh lý`, flush: true,
    actions: [h('a', { class: 'btn ghost sm', href: '#/contracts' }, 'Tất cả hợp đồng')],
  });
}

// ------------------------------------------------------------------ Vòng đời khách: tuyến 4 trạm
interface Lifecycle {
  prospects: number; vacant: number; unsigned: number; renting: number; totalRooms: number;
  openTickets: number; awaitingClose: number; checkoutOpen: number; endingSoon: number;
  unhanded: number; firstUnhanded: number | null;
}

async function loadLifecycle(): Promise<Lifecycle> {
  const [live, prospectList, inspections, tickets, checkouts] = await Promise.all([
    loadLive().catch(() => null),
    api.get<unknown[]>('/api/chi/tenants?available=true&limit=50').catch(() => null), // chỉ cần đếm, tối đa 50
    // biên bản nhận phòng: hợp đồng mới (30 ngày) chưa có biên bản là việc còn dang dở
    api.get<Array<{ contractId: number | null }>>('/api/chi/checkin-inspections').catch(() => null),
    api.get<Page<{ status: string }>>('/api/maintenance-requests?size=200').catch(() => null),
    api.get<Page<{ status: string }>>('/api/checkout-requests?size=200').catch(() => null),
  ]);
  const rooms = live?.rooms ?? [];
  const contracts = live?.contracts ?? [];
  const t = tickets?.items ?? [];
  const handed = new Set((inspections ?? []).map((x) => x.contractId));
  const since = isoDay(-30);
  const unhandedList = inspections ? contracts.filter((c) => c.startDate >= since && !handed.has(c.contractId)) : [];
  return {
    unhanded: unhandedList.length, firstUnhanded: unhandedList[0]?.contractId ?? null,
    prospects: prospectList?.length ?? 0,
    vacant: rooms.filter((r) => r.status === 'VACANT').length,
    unsigned: contracts.filter((c) => !c.digitalSignatureUrl).length,
    renting: rooms.filter((r) => r.status === 'RENTED').length,
    totalRooms: rooms.length,
    openTickets: t.filter((x) => x.status === 'OPEN' || x.status === 'ASSIGNED' || x.status === 'IN_PROGRESS').length,
    awaitingClose: t.filter((x) => x.status === 'RESOLVED').length,
    checkoutOpen: (checkouts?.items ?? []).filter((x) => x.status === 'PENDING' || x.status === 'INSPECTING').length,
    endingSoon: endingSoon(contracts).length,
  };
}

interface LifeRow { label: string; n: number; href: string; hot?: boolean }
interface LifeCol { name: string; sub: string; rows: LifeRow[]; action: { label: string; href: string } }

function lifecyclePanel(l: Lifecycle, overdue: number): HTMLElement {
  const cols: LifeCol[] = [
    { name: 'Tiếp nhận', sub: `${l.prospects >= 50 ? '50+' : l.prospects} khách chưa thuê`, action: { label: 'Thêm khách', href: '#/tenants' },
      rows: [{ label: 'Khách chưa thuê phòng', n: l.prospects, href: '#/tenants' }, { label: 'Phòng đang trống', n: l.vacant, href: '#/rooms' }] },
    { name: 'Nhận phòng', sub: l.unhanded ? `${l.unhanded} chưa bàn giao phòng` : l.unsigned ? `${l.unsigned} hợp đồng chưa ký` : 'Không việc tồn', action: { label: 'Nhận khách', href: '#/checkin' },
      rows: [{ label: 'Hợp đồng mới chưa bàn giao phòng', n: l.unhanded, href: l.firstUnhanded ? `#/checkin?contract=${l.firstUnhanded}` : '#/contracts', hot: l.unhanded > 0 },
        { label: 'Hợp đồng chưa ký', n: l.unsigned, href: '#/contracts', hot: l.unsigned > 0 }, { label: 'Phòng sẵn sàng nhận khách', n: l.vacant, href: '#/checkin' }] },
    { name: 'Đang ở', sub: `${l.renting}/${l.totalRooms} phòng có khách`, action: { label: 'Xem công nợ', href: '#/debts' },
      rows: [{ label: 'Hóa đơn quá hạn', n: overdue, href: '#/invoices?status=OVERDUE', hot: overdue > 0 },
        { label: 'Sự cố đang xử lý', n: l.openTickets, href: '#/maintenance', hot: l.openTickets > 0 },
        { label: 'Sự cố chờ xác nhận xong', n: l.awaitingClose, href: '#/maintenance' }] },
    { name: 'Trả phòng', sub: l.checkoutOpen ? `${l.checkoutOpen} đang làm thủ tục` : 'Không có yêu cầu', action: { label: 'Mở Trả phòng', href: '#/checkout' },
      rows: [{ label: 'Yêu cầu trả phòng đang làm', n: l.checkoutOpen, href: '#/checkout', hot: l.checkoutOpen > 0 },
        { label: 'Hợp đồng hết hạn trong 30 ngày', n: l.endingSoon, href: '#/contracts', hot: l.endingSoon > 0 }] },
  ];
  const work = (c: LifeCol) => c.rows.filter((r) => r.hot).reduce((a, r) => a + r.n, 0);
  const stops: Stop[] = cols.map((c) => ({
    name: c.name, sub: c.sub, state: work(c) > 0 ? 'current' : 'done', href: c.rows[0].href, stuck: work(c), stuckLabel: 'việc cần làm',
  }));
  return h('section', { class: 'panel route life' },
    h('div', { class: 'route-head' },
      h('h2', null, 'Khách thuê: từ nhận phòng đến trả phòng'),
      h('span', { class: 'muted' }, 'Bốn bước của một khách thuê. Bước nào đang có việc cần làm sẽ được đánh dấu vàng.')),
    routeLine(stops),
    h('div', { class: 'life-cols' }, ...cols.map((c) => h('div', { class: 'life-col' },
      ...c.rows.map((r) => h('a', { class: `life-row${r.hot ? ' hot' : ''}`, href: r.href }, h('span', null, r.label), h('b', null, String(r.n)))),
      h('a', { class: 'btn secondary sm', href: c.action.href }, c.action.label, icon('arrowRight', 14))))),
  );
}
