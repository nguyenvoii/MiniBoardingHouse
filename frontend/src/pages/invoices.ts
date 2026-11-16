import { h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, loadSession } from '../lib/api';
import { getMonth } from '../lib/ctx';
import { date, money, monthLabel } from '../lib/format';
import type { Invoice, InvoiceDetail } from '../data/types';
import { dl, emptyState, input, openDialog, pageHead, plate, select, statusBadge, table, toast } from '../ui/kit';
import { openPaymentDialog } from './payments';
import { PROPERTY_ID } from '../lib/api';

export async function invoicesPage(view: HTMLElement, _p: Record<string, string>, query: URLSearchParams): Promise<void> {
  const month = getMonth();
  const state = { q: query.get('q') ?? '', status: query.get('status') ?? '' };
  const holder = h('div');
  const q = input({ type: 'search', value: state.q, placeholder: 'Mã hóa đơn hoặc mã phòng', 'aria-label': 'Tìm hóa đơn' });
  const status = select([['', 'Mọi trạng thái'], ['UNPAID', 'Chưa trả'], ['PARTIALLY_PAID', 'Trả một phần'], ['OVERDUE', 'Quá hạn'], ['PAID', 'Đã trả']], state.status);
  const total = h('span', { class: 'muted' });

  const load = async () => {
    const qs = new URLSearchParams({ propertyId: String(PROPERTY_ID) });
    if (!state.q && !state.status) qs.set('monthYear', month);
    if (state.q) qs.set('code', state.q);
    if (state.status) qs.set('status', state.status);
    let rows = await api.get<Invoice[]>(`/api/invoices?${qs}`);
    if (state.q && !rows.length) rows = await api.get<Invoice[]>(`/api/invoices?propertyId=${PROPERTY_ID}&roomCode=${encodeURIComponent(state.q)}`);
    total.textContent = `${rows.length} hóa đơn · còn phải thu ${money(rows.reduce((a, r) => a + r.remainingAmount, 0))} ₫`;
    holder.replaceChildren(table<Invoice>([
      { key: 'c', label: 'Mã hóa đơn', render: (i) => h('span', { class: 'cell-main' }, i.invoiceCode), sort: (a, b) => a.invoiceCode.localeCompare(b.invoiceCode) },
      { key: 'r', label: 'Phòng', render: (i) => plate(i.roomCode), sort: (a, b) => a.roomCode.localeCompare(b.roomCode) },
      { key: 't', label: 'Người thuê', render: (i) => i.tenantName },
      { key: 'd', label: 'Hạn trả', render: (i) => date(i.dueDate), sort: (a, b) => a.dueDate.localeCompare(b.dueDate) },
      { key: 'a', label: 'Tổng (₫)', num: true, render: (i) => money(i.totalAmount), sort: (a, b) => a.totalAmount - b.totalAmount },
      { key: 'l', label: 'Còn lại (₫)', num: true, render: (i) => h('b', null, money(i.remainingAmount)), sort: (a, b) => a.remainingAmount - b.remainingAmount },
      { key: 's', label: 'Trạng thái', render: (i) => statusBadge(i.paymentStatus) },
    ], rows, {
      onRow: (i) => openInvoice(i.invoiceId, load),
      empty: emptyState('Không có hóa đơn', state.q ? 'Không hóa đơn nào khớp từ khóa. Thử mã phòng hoặc mã hóa đơn đầy đủ.' : `${monthLabel(month)} chưa phát hành hóa đơn. Duyệt kỳ tính tiền rồi phát hành.`,
        h('a', { class: 'btn secondary', href: '#/billing' }, 'Đi tới Tính tiền')),
    }));
  };

  let timer: number | undefined;
  q.addEventListener('input', () => { clearTimeout(timer); timer = window.setTimeout(() => { state.q = q.value.trim(); void load(); }, 300); });
  status.addEventListener('change', () => { state.status = status.value; void load(); });

  view.replaceChildren(
    pageHead('Hóa đơn', state.q || state.status ? 'Kết quả trong mọi tháng' : monthLabel(month)),
    h('section', { class: 'panel panel-flush' }, h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, q), status, total), holder),
  );
  await load();
}

async function openInvoice(id: number, reload: () => void): Promise<void> {
  const detail = await api.get<InvoiceDetail>(`/api/invoices/${id}/detail`).catch((e) => { toast(errText(e), 'err'); return null; });
  if (!detail) return;
  const inv = detail.invoice;
  const d = openDialog(`Hóa đơn ${inv.invoiceCode}`, { drawer: true });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(inv.roomCode, 'lg'), h('b', { style: 'font-size:16px' }, inv.tenantName), statusBadge(inv.paymentStatus)),
    dl([['Tháng', monthLabel(inv.monthYear)], ['Ngày phát hành', date(inv.issueDate)], ['Hạn trả', date(inv.dueDate)], ['Đã trả', `${money(inv.paidAmount)} ₫`], ['Còn lại', `${money(inv.remainingAmount)} ₫`]]),
    table([
      { key: 'n', label: 'Khoản', render: (i: InvoiceDetail['items'][number]) => i.itemName },
      { key: 'q', label: 'SL', num: true, render: (i) => i.quantity },
      { key: 'u', label: 'Đơn giá', num: true, render: (i) => money(i.unitPrice) },
      { key: 's', label: 'Thành tiền', num: true, render: (i) => money(i.subtotal) },
    ], detail.items, { footer: [h('td', { colspan: 3 }, 'Tổng hóa đơn'), h('td', { class: 'num' }, money(inv.totalAmount))] }),
  );
  const pay = h('button', { class: 'btn', disabled: inv.paymentStatus === 'PAID' || inv.paymentStatus === 'CANCELLED' }, icon('wallet', 16), 'Thu tiền');
  pay.addEventListener('click', () => { d.close(); openPaymentDialog(inv, reload); });
  const print = h('button', { class: 'btn secondary' }, icon('print', 16), 'In');
  print.addEventListener('click', async () => {
    const s = loadSession();
    const res = await fetch(`/api/invoices/${id}/print`, { headers: s ? { Authorization: `Bearer ${s.token}` } : {} });
    window.open(URL.createObjectURL(new Blob([await res.text()], { type: 'text/html' })), '_blank');
  });
  const mail = h('button', { class: 'btn secondary' }, icon('mail', 16), 'Gửi email');
  mail.addEventListener('click', async () => {
    try { await api.post(`/api/invoices/${id}/send-email`); toast('Đã gửi hóa đơn qua email'); } catch (e) { toast(errText(e), 'err'); }
  });
  d.foot.append(print, mail, pay);
}
