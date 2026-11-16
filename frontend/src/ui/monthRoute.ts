import { h } from '../lib/dom';
import { api, PROPERTY_ID } from '../lib/api';
import { monthLabel } from '../lib/format';
import type { Invoice, Period } from '../data/types';
import { routeLine, Stop } from './route';

/** Thanh «Tuyến tháng» thu gọn ở đầu các màn chốt tháng: cho biết tháng đang ở trạm nào và màn hiện tại là trạm nào. */
export async function monthRouteBar(month: string, here: number): Promise<HTMLElement | null> {
  let periods: Period[];
  let invoices: Invoice[];
  try {
    [periods, invoices] = await Promise.all([
      api.get<Period[]>(`/api/billing/periods?propertyId=${PROPERTY_ID}`),
      api.get<Invoice[]>(`/api/invoices?propertyId=${PROPERTY_ID}&monthYear=${month}`),
    ]);
  } catch {
    return null; // vai trò không xem được kỳ tính tiền (ví dụ thu ngân): bỏ qua thanh này
  }
  const period = periods.find((p) => p.monthYear === month) ?? null;
  const issued = invoices.length;
  const paid = invoices.filter((i) => i.paymentStatus === 'PAID').length;
  const flags = [!!period, !!period && period.roomCount > 0, !!period && period.status !== 'DRAFT', issued > 0, issued > 0 && paid === issued];
  const cur = flags.findIndex((f) => !f);
  const st = (i: number): Stop['state'] => (cur === -1 || i < cur ? 'done' : i === cur ? 'current' : 'todo');
  const bill = period ? `#/billing/${period.billingPeriodId}` : '#/billing';
  const stops: Stop[] = [
    { name: 'Chỉ số và kỳ', sub: period ? 'Đã tạo kỳ' : 'Chưa tạo kỳ', state: st(0), href: '#/meters' },
    { name: 'Tính tiền', sub: period && period.roomCount ? `${period.roomCount} phòng` : 'Chưa tính', state: st(1), href: '#/billing' },
    { name: 'Duyệt kỳ', sub: period && period.status !== 'DRAFT' ? 'Đã duyệt' : 'Chờ duyệt', state: st(2), href: bill },
    { name: 'Phát hành', sub: issued ? `${issued} hóa đơn` : 'Chưa phát hành', state: st(3), href: '#/invoices' },
    { name: 'Thu tiền', sub: issued ? `${paid}/${issued} đã trả` : '–', state: st(4), href: '#/payments' },
  ];
  const line = routeLine(stops);
  line.querySelectorAll('.stop')[here]?.classList.add('here');
  return h('section', { class: 'panel route compact', 'aria-label': `Tuyến ${monthLabel(month).toLowerCase()}` }, line);
}
