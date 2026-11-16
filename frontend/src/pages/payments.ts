import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, Page } from '../lib/api';
import { dateTime, money, monthLabel } from '../lib/format';
import { getMonth } from '../lib/ctx';
import type { Invoice, Payment, QrInfo } from '../data/types';
import { confirmDialog, emptyState, field, formError, input, openDialog, pageHead, panel, plate, select, statusBadge, table, toast } from '../ui/kit';

const METHODS: Record<string, string> = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', QR_CODE: 'Mã QR', GATEWAY: 'Cổng thanh toán' };

/** Hộp thoại ghi nhận thanh toán cho một hóa đơn (dùng ở màn Hóa đơn và Thanh toán). */
export function openPaymentDialog(inv: Invoice, done: () => void): void {
  const d = openDialog(`Thu tiền hóa đơn ${inv.invoiceCode}`);
  const err = h('div');
  const method = select([['CASH', 'Tiền mặt'], ['BANK_TRANSFER', 'Chuyển khoản'], ['QR', 'Tạo mã QR cho khách quét']], 'CASH');
  const amount = input({ type: 'number', min: '1', step: '1000', value: String(inv.remainingAmount), class: 'input num-in' });
  const code = input({ type: 'text', placeholder: 'Mã giao dịch ngân hàng (nếu có)' });
  const codeField = field('Mã giao dịch', code);
  d.body.append(err,
    h('div', { class: 'form-grid' },
      h('div', { class: 'field full' }, h('span', { class: 'label' }, 'Hóa đơn'), h('div', { style: 'display:flex;gap:10px;align-items:center' }, plate(inv.roomCode), h('b', null, inv.tenantName), statusBadge(inv.paymentStatus))),
      field('Còn phải trả (₫)', input({ value: money(inv.remainingAmount), disabled: true, class: 'input num-in' })),
      field('Phương thức', method),
      field('Số tiền nhận (₫)', amount, 'Không được vượt số còn phải trả'), codeField));
  method.addEventListener('change', () => (codeField.hidden = method.value !== 'BANK_TRANSFER'));
  codeField.hidden = true;
  const ok = h('button', { class: 'btn' }, 'Ghi nhận');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    const amt = Number(amount.value);
    if (!(amt > 0)) { err.replaceChildren(formError('Nhập số tiền lớn hơn 0.')); return; }
    try {
      if (method.value === 'QR') {
        const q = await api.post<QrInfo>('/api/payments/qr', { invoiceId: inv.invoiceId, method: 'QR_CODE', amount: amt });
        d.close();
        showQr(q, done);
        return;
      }
      await api.post<Payment>('/api/payments', { invoiceId: inv.invoiceId, method: method.value, amount: amt, transactionCode: code.value.trim() || null });
      d.close();
      toast(`Đã ghi nhận ${money(amt)} ₫`);
      done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function showQr(q: QrInfo, done: () => void): void {
  const d = openDialog('Khách quét mã để trả tiền');
  d.body.append(
    h('div', { class: 'qr-box' }, q.qrImageUrl ? h('img', { src: q.qrImageUrl, alt: 'Mã QR ngân hàng' }) : h('p', { class: 'muted' }, 'Tài khoản chưa có ảnh QR. Gửi thông tin chuyển khoản bên dưới.')),
    h('dl', { class: 'dl' }, h('dt', null, 'Ngân hàng'), h('dd', null, q.bankName), h('dt', null, 'Số tài khoản'), h('dd', null, q.accountNumber), h('dt', null, 'Chủ tài khoản'), h('dd', null, q.accountHolder),
      h('dt', null, 'Số tiền'), h('dd', null, `${money(q.payment.amountPaid)} ₫`), h('dt', null, 'Nội dung chuyển khoản'), h('dd', null, h('span', { class: 'plate' }, q.transferContent))),
    h('p', { class: 'muted' }, 'Giao dịch đang chờ. Khi tiền về tài khoản, mở Thanh toán và bấm Xác nhận.'),
  );
  d.foot.append(h('button', { class: 'btn', onclick: () => { d.close(); done(); } }, 'Xong'));
}

export async function paymentsPage(view: HTMLElement): Promise<void> {
  const month = getMonth();
  const state = { status: '', keyword: '', all: false };
  const holder = h('div');
  const status = select([['', 'Mọi trạng thái'], ['FAILED', 'Chờ xác nhận'], ['SUCCESS', 'Thành công'], ['REVERSED', 'Đã hủy giao dịch']], '', { 'aria-label': 'Lọc theo trạng thái giao dịch' });
  const kw = input({ type: 'search', placeholder: 'Mã giao dịch hoặc mã hóa đơn', 'aria-label': 'Tìm giao dịch' });
  const scope = select([['month', monthLabel(month)], ['all', 'Mọi tháng']], 'month', { 'aria-label': 'Chọn tháng của giao dịch' });
  scope.addEventListener('change', () => { state.all = scope.value === 'all'; void load(); });
  const load = async () => {
    const r = await api.get<Page<Payment>>(`/api/payments?status=${state.status}&keyword=${encodeURIComponent(state.keyword)}&size=200`);
    if (!state.all) r.items = r.items.filter((x) => x.paymentDate.startsWith(month));
    holder.replaceChildren(table<Payment>([
      { key: 'd', label: 'Thời điểm', render: (p) => dateTime(p.paymentDate) },
      { key: 'i', label: 'Hóa đơn', render: (p) => h('span', { class: 'plate ghost' }, p.invoiceCode) },
      { key: 'm', label: 'Phương thức', render: (p) => METHODS[p.method] ?? p.method },
      { key: 'c', label: 'Mã giao dịch', render: (p) => p.transactionCode ?? '–' },
      { key: 'a', label: 'Số tiền (₫)', num: true, render: (p) => h('b', null, money(p.amountPaid)) },
      { key: 'u', label: 'Người nhận', render: (p) => p.receivedByName ?? '–' },
      { key: 's', label: 'Trạng thái', render: (p) => statusBadge(p.status) },
      { key: 'x', label: '', render: (p) => p.status === 'FAILED' ? confirmBtn(p) : '' },
    ], r.items, { onRow: (p) => openPayment(p, load), empty: emptyState(state.all ? 'Chưa có giao dịch' : `Không có giao dịch trong ${monthLabel(month).toLowerCase()}`, state.all ? 'Khi ghi nhận thanh toán hoặc tạo mã QR, giao dịch hiện ở đây.' : 'Đổi tháng ở thanh trên, hoặc chọn «Mọi tháng» ở bộ lọc.') }));
  };
  const confirmBtn = (p: Payment) => {
    const b = h('button', { class: 'btn sm' }, icon('check', 14), 'Xác nhận');
    b.addEventListener('click', (e) => { e.stopPropagation(); confirmPayment(p, load); });
    return b;
  };
  status.addEventListener('change', () => { state.status = status.value; void load(); });
  let timer: number | undefined;
  kw.addEventListener('input', () => { clearTimeout(timer); timer = window.setTimeout(() => { state.keyword = kw.value.trim(); void load(); }, 300); });

  view.replaceChildren(
    pageHead('Thanh toán', 'Đối chiếu tiền vào tài khoản với hóa đơn. Giao dịch chờ xác nhận chưa được cộng vào hóa đơn.', [
      h('button', { class: 'btn', onclick: () => matchDialog(load) }, icon('search', 16), 'Đối chiếu chuyển khoản'),
    ]),
    h('section', { class: 'panel panel-flush' }, h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, kw), scope, status), holder),
  );
  await load();
}

function confirmPayment(p: Payment, done: () => void): void {
  const d = openDialog(`Xác nhận giao dịch ${p.transactionCode ?? p.transactionId}`);
  const err = h('div');
  const amt = input({ type: 'number', value: String(p.amountPaid), class: 'input num-in' });
  d.body.append(err,
    h('p', null, `Hóa đơn ${p.invoiceCode}, số tiền giao dịch ${money(p.amountPaid)} ₫. Nhập số tiền thực nhận trên sao kê ngân hàng.`),
    field('Số tiền thực nhận (₫)', amt, 'Khớp đúng thì tiền được cộng vào hóa đơn; lệch thì giao dịch giữ nguyên chờ xử lý.'));
  const ok = h('button', { class: 'btn' }, 'Xác nhận');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      const r = await api.post<{ confirmed: boolean; message: string }>(`/api/payments/${p.transactionId}/confirm`, { amountReceived: Number(amt.value) });
      if (!r.confirmed) { err.replaceChildren(formError(r.message)); return; }
      d.close();
      toast(r.message);
      done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function matchDialog(done: () => void): void {
  const d = openDialog('Đối chiếu nội dung chuyển khoản');
  const content = h('textarea', { class: 'textarea', placeholder: 'Dán nội dung chuyển khoản trên sao kê, ví dụ: NGUYEN VAN A CK INV-202611-P101-3' }) as HTMLTextAreaElement;
  const out = h('div');
  d.body.append(field('Nội dung chuyển khoản', content, 'Hệ thống tìm hóa đơn chưa trả đủ có mã nằm trong nội dung.'), out);
  const go = h('button', { class: 'btn' }, icon('search', 16), 'Tìm hóa đơn');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'), go);
  go.addEventListener('click', () => busy(go, async () => {
    try {
      const r = await api.post<{ matches: Array<{ invoiceId: number; invoiceCode: string; tenantName: string; remaining: number; paymentStatus: string }> }>('/api/payments/match', { content: content.value });
      if (!r.matches.length) { out.replaceChildren(emptyState('Không tìm thấy hóa đơn', 'Nội dung không chứa mã hóa đơn nào còn nợ. Kiểm tra lại mã hoặc ghi nhận thủ công.')); return; }
      out.replaceChildren(h('div', null, ...r.matches.map((m) => h('div', { class: 'list-row' },
        h('span', { class: 'plate ghost' }, m.invoiceCode), h('div', { class: 'grow' }, h('b', null, m.tenantName), h('span', null, `còn ${money(m.remaining)} ₫`)),
        h('button', { class: 'btn sm', onclick: async () => {
          const inv = await api.get<Invoice>(`/api/invoices/${m.invoiceId}`);
          d.close();
          openPaymentDialog(inv, done);
        } }, 'Ghi nhận')))));
    } catch (e) { out.replaceChildren(formError(errText(e))); }
  }));
}

void panel;

/** Chi tiết giao dịch. «Hủy giao dịch» (làm giảm số đã trả của hóa đơn) nằm riêng ở cuối, không đặt trên từng hàng để khỏi bấm nhầm. */
function openPayment(p: Payment, reload: () => void): void {
  const d = openDialog(`Giao dịch ${p.transactionCode ?? p.transactionId}`);
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, h('span', { class: 'plate ghost' }, p.invoiceCode), statusBadge(p.status)),
    h('dl', { class: 'dl' }, h('dt', null, 'Số tiền'), h('dd', null, `${money(p.amountPaid)} ₫`), h('dt', null, 'Phương thức'), h('dd', null, METHODS[p.method] ?? p.method),
      h('dt', null, 'Thời điểm'), h('dd', null, dateTime(p.paymentDate)), h('dt', null, 'Người nhận'), h('dd', null, p.receivedByName ?? '–')),
  );
  if (p.status === 'SUCCESS') {
    const btn = h('button', { class: 'btn danger' }, 'Hủy giao dịch');
    btn.addEventListener('click', async () => {
      if (!(await confirmDialog('Hủy giao dịch thanh toán', `Hủy giao dịch ${money(p.amountPaid)} ₫ của hóa đơn ${p.invoiceCode}? Số tiền sẽ bị trừ khỏi hóa đơn. Chỉ dùng khi ghi nhầm.`, 'Hủy giao dịch', true))) return;
      try { await api.post(`/api/payment-transactions/${p.transactionId}/reverse`); d.close(); toast('Đã hủy giao dịch'); reload(); } catch (x) { toast(errText(x), 'err'); }
    });
    d.body.append(h('div', { class: 'danger-zone' }, h('p', null, 'Ghi nhầm số tiền hoặc hóa đơn? Hủy giao dịch để trừ lại khỏi hóa đơn.'), btn));
  }
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'));
}
