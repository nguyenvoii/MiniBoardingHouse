import { h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, can, errText, loadSession } from '../lib/api';
import { getMonth } from '../lib/ctx';
import { date, money, monthLabel } from '../lib/format';
import type { Invoice, InvoiceDetail } from '../data/types';
import { confirmDialog, dl, emptyState, field, formError, input, openDialog, pageHead, plate, select, statusBadge, table, toast } from '../ui/kit';
import { openPaymentDialog } from './payments';
import { PROPERTY_ID } from '../lib/api';

export async function invoicesPage(view: HTMLElement, _p: Record<string, string>, query: URLSearchParams): Promise<void> {
  const month = getMonth();
  const state = { q: query.get('q') ?? '', status: query.get('status') ?? '' };
  const holder = h('div');
  const q = input({ type: 'search', value: state.q, placeholder: 'Mã hóa đơn hoặc mã phòng', 'aria-label': 'Tìm hóa đơn' });
  const status = select([['', 'Mọi trạng thái'], ['UNPAID', 'Chưa trả'], ['PARTIALLY_PAID', 'Trả một phần'], ['OVERDUE', 'Quá hạn'], ['PAID', 'Đã trả'], ['CANCELLED', 'Đã hủy']], state.status, { 'aria-label': 'Lọc theo trạng thái hóa đơn' });
  const total = h('span', { class: 'muted' });

  const load = async () => {
    const qs = new URLSearchParams({ propertyId: String(PROPERTY_ID) });
    if (!state.q && !state.status) qs.set('monthYear', month);
    if (state.q) qs.set('code', state.q);
    if (state.status) qs.set('status', state.status);
    let rows = await api.get<Invoice[]>(`/api/invoices?${qs}`);
    if (state.q && !rows.length) rows = await api.get<Invoice[]>(`/api/invoices?propertyId=${PROPERTY_ID}&roomCode=${encodeURIComponent(state.q)}`);
    total.textContent = `${rows.length} hóa đơn · còn phải thu ${money(rows.filter((r) => r.paymentStatus !== 'CANCELLED').reduce((a, r) => a + r.remainingAmount, 0))} ₫`;
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
    try {
      const r = await api.post<{ status: string }>(`/api/invoices/${id}/send-email`);
      if (r?.status === 'SENT') toast('Đã gửi hóa đơn qua email');
      else if (r?.status === 'FAILED') toast('Gửi email thất bại. Kiểm tra cấu hình thư và địa chỉ email của khách.', 'err');
      else toast('Chưa gửi được: máy chủ chưa cấu hình gửi thư, hóa đơn mới chỉ được ghi vào hàng chờ.', 'err');
    } catch (e) { toast(errText(e), 'err'); }
  });
  d.foot.append(print, mail, pay);

  if (inv.paymentStatus === 'CANCELLED' && can('LANDLORD', 'MANAGER')) {
    const re = h('button', { class: 'btn secondary' }, icon('send', 16), 'Phát hành lại');
    re.addEventListener('click', async () => {
      try { await api.post('/api/invoices', { calculatedFeeId: inv.calculatedFeeId }); d.close(); toast('Đã phát hành lại hóa đơn'); reload(); }
      catch (e) { toast(errText(e), 'err'); }
    });
    d.body.append(h('div', { class: 'danger-zone' }, h('p', null, 'Hóa đơn này đã hủy. Nếu kỳ tính tiền còn đúng, phát hành lại để người thuê thấy hóa đơn mới.'), re));
  } else if (inv.paymentStatus !== 'CANCELLED') {
    const reason = h('textarea', { class: 'textarea', maxlength: '500', placeholder: 'Lý do hủy (bắt buộc), ví dụ: phát hành nhầm kỳ, sai chỉ số' }) as HTMLTextAreaElement;
    const err = h('div');
    const cancel = h('button', { class: 'btn danger' }, 'Hủy hóa đơn');
    cancel.addEventListener('click', async () => {
      err.replaceChildren();
      if (!reason.value.trim()) { err.replaceChildren(formError('Nhập lý do hủy hóa đơn.')); reason.focus(); return; }
      if (!(await confirmDialog('Hủy hóa đơn', `Hủy hóa đơn ${inv.invoiceCode}? Người thuê không còn thấy hóa đơn này. Chỉ hủy được khi chưa thu tiền. Sau đó bạn mở lại kỳ để sửa hoặc phát hành lại.`, 'Hủy hóa đơn', true))) return;
      try {
        await api.patch(`/api/invoices/${id}/cancel`, { reason: reason.value.trim() });
        d.close(); toast('Đã hủy hóa đơn'); reload();
      } catch (e) { err.replaceChildren(formError(errText(e))); }
    });
    d.body.append(h('details', { class: 'danger-zone' }, h('summary', { style: 'cursor:pointer;font-weight:600' }, 'Hủy hóa đơn (phát hành nhầm)'),
      h('p', null, 'Chỉ dùng khi phát hành nhầm hoặc sai số liệu và chưa thu tiền.'), err, field('Lý do hủy', reason), cancel));
  }
}
