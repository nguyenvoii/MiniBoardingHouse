import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, ApiError, can, errText, loadSession, Page } from '../lib/api';
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
    if (method.value !== 'QR' && amt !== inv.remainingAmount && !(await confirmDialog('Số tiền khác số còn lại', `Còn phải trả ${money(inv.remainingAmount)} ₫ nhưng bạn ghi nhận ${money(amt)} ₫ (${amt < inv.remainingAmount ? 'trả một phần' : 'vượt số còn lại'}). Khoản ghi nhận không tự gỡ được, chỉ chủ trọ hoặc quản lý hủy. Đúng số tiền thực nhận?`, 'Đúng, ghi nhận'))) return;
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
    } catch (e) {
      // mất mạng sau khi gửi: tiền có thể đã được ghi, bấm lại sẽ ghi trùng
      const lost = e instanceof ApiError && e.status === 0;
      err.replaceChildren(formError(lost ? `${errText(e)} Giao dịch có thể đã được ghi: mở danh sách Thanh toán kiểm tra trước khi bấm lại, kẻo ghi trùng.` : errText(e)));
    }
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
  const state = { status: '', keyword: '', all: false, page: 0 };
  const PAGE = 100;
  let loaded: Payment[] = [];
  const holder = h('div');
  const status = select([['', 'Mọi trạng thái'], ['FAILED', 'Chờ xác nhận'], ['SUCCESS', 'Thành công'], ['REVERSED', 'Đã hủy giao dịch']], '', { 'aria-label': 'Lọc theo trạng thái giao dịch' });
  const kw = input({ type: 'search', placeholder: 'Mã giao dịch hoặc mã hóa đơn', 'aria-label': 'Tìm giao dịch' });
  const scope = select([['month', monthLabel(month)], ['all', 'Mọi tháng']], 'month', { 'aria-label': 'Chọn tháng của giao dịch' });
  scope.addEventListener('change', () => { state.all = scope.value === 'all'; void load(); });
  const more = h('button', { class: 'btn secondary', style: 'margin:12px' }, 'Tải thêm giao dịch cũ hơn');
  more.addEventListener('click', () => busy(more, async () => { state.page++; await load(true); }));
  const load = async (append = false) => {
    if (!append) { state.page = 0; loaded = []; }
    const r = await api.get<Page<Payment>>(`/api/payments?status=${state.status}&keyword=${encodeURIComponent(state.keyword)}&page=${state.page}&size=${PAGE}`);
    loaded = [...loaded, ...r.items];
    const hasMore = r.page + 1 < r.totalPages;
    const items = state.all ? loaded : loaded.filter((x) => x.paymentDate.startsWith(month));
    holder.replaceChildren(h('p', { class: 'muted', style: 'margin:12px 16px 0' }, `Đang hiển thị ${items.length} giao dịch${state.all ? '' : ` của ${monthLabel(month).toLowerCase()}`}, đã tải ${loaded.length}/${r.totalElements} giao dịch mới nhất trước.`), table<Payment>([
      { key: 'd', label: 'Thời điểm', render: (p) => dateTime(p.paymentDate) },
      { key: 'i', label: 'Hóa đơn', render: (p) => h('span', { class: 'plate ghost' }, p.invoiceCode) },
      { key: 'm', label: 'Phương thức', render: (p) => METHODS[p.method] ?? p.method },
      { key: 'c', label: 'Mã giao dịch', render: (p) => p.transactionCode ?? '–' },
      { key: 'a', label: 'Số tiền (₫)', num: true, render: (p) => h('b', null, money(p.amountPaid)) },
      { key: 'u', label: 'Người nhận', render: (p) => p.receivedByName ?? '–' },
      { key: 's', label: 'Trạng thái', render: (p) => statusBadge(p.status) },
      { key: 'x', label: '', render: (p) => p.status === 'FAILED' ? confirmBtn(p) : '' },
    ], items, { onRow: (p) => openPayment(p, () => void load()), empty: emptyState(state.all ? 'Chưa có giao dịch' : `Không có giao dịch trong ${monthLabel(month).toLowerCase()}`, state.all ? 'Khi ghi nhận thanh toán hoặc tạo mã QR, giao dịch hiện ở đây.' : 'Đổi tháng ở thanh trên, hoặc chọn «Mọi tháng» ở bộ lọc.') }), ...(hasMore ? [more] : []));
  };
  const confirmBtn = (p: Payment) => {
    const b = h('button', { class: 'btn sm' }, icon('check', 14), 'Xác nhận');
    b.addEventListener('click', (e) => { e.stopPropagation(); confirmPayment(p, () => void load()); });
    return b;
  };
  status.addEventListener('change', () => { state.status = status.value; void load(); });
  let timer: number | undefined;
  kw.addEventListener('input', () => { clearTimeout(timer); timer = window.setTimeout(() => { state.keyword = kw.value.trim(); void load(); }, 300); });

  view.replaceChildren(
    pageHead('Thanh toán', 'Đối chiếu tiền vào tài khoản với hóa đơn. Giao dịch chờ xác nhận chưa được cộng vào hóa đơn.', [
      h('button', { class: 'btn secondary', onclick: () => void receiptsDrawer() }, icon('receipt', 16), 'Phiếu thu'),
      h('button', { class: 'btn', onclick: () => matchDialog(() => void load()) }, icon('search', 16), 'Đối chiếu chuyển khoản'),
    ]),
    h('section', { class: 'panel panel-flush' }, h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, kw), scope, status), holder),
  );
  await load();
}

interface Receipt { voucherId: number; voucherCode: string; transactionId: number | null; voucherType: string; amount: number; note: string | null; createdAt: string }
const VOUCHER: Record<string, string> = { MONTHLY_RENT: 'Tiền phòng tháng', DEPOSIT: 'Tiền cọc', EXTRA_SERVICE: 'Dịch vụ thêm' };

/** Danh sách phiếu thu và lập phiếu mới. Tiền phòng phải gắn giao dịch đã thành công; phiếu cọc chỉ lập ở mục Thu cọc của hợp đồng. */
async function receiptsDrawer(): Promise<void> {
  const d = openDialog('Phiếu thu', { drawer: true });
  const paint = async () => {
    d.body.replaceChildren(h('p', { class: 'muted' }, 'Đang tải...'));
    let list: Receipt[];
    try { list = await api.get<Receipt[]>('/api/receipts'); } catch (e) { d.body.replaceChildren(formError(errText(e))); return; }
    d.body.replaceChildren(
      h('p', { class: 'muted' }, 'Phiếu thu là chứng từ đã nhận tiền. Phiếu tiền cọc chỉ được lập ở mục Thu cọc của hợp đồng, không lập tay ở đây.'),
      list.length ? table<Receipt>([
        { key: 'c', label: 'Mã phiếu', render: (r) => h('span', { class: 'plate ghost' }, r.voucherCode) },
        { key: 't', label: 'Loại', render: (r) => VOUCHER[r.voucherType] ?? r.voucherType },
        { key: 'a', label: 'Số tiền (₫)', num: true, render: (r) => h('b', null, money(r.amount)) },
        { key: 'd', label: 'Lập lúc', render: (r) => dateTime(r.createdAt) },
        { key: 'p', label: '', render: (r) => h('button', { class: 'btn secondary sm', onclick: (e: Event) => { e.stopPropagation(); void printReceipt(r.voucherId); } }, icon('print', 14), 'In') },
      ], list) : emptyState('Chưa có phiếu thu', 'Lập phiếu cho một giao dịch đã thu thành công.'));
  };
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'), h('button', { class: 'btn', onclick: () => newReceipt(() => void paint()) }, icon('plus', 16), 'Lập phiếu thu'));
  await paint();
}

async function printReceipt(id: number): Promise<void> {
  try {
    const s = loadSession();
    const res = await fetch(`/api/receipts/${id}/print`, { headers: s ? { Authorization: `Bearer ${s.token}` } : {} });
    if (!res.ok) throw new Error(`Lỗi ${res.status}`);
    window.open(URL.createObjectURL(new Blob([await res.text()], { type: 'text/html' })), '_blank');
  } catch (e) { toast(errText(e), 'err'); }
}

async function newReceipt(done: () => void): Promise<void> {
  const d = openDialog('Lập phiếu thu', { width: 520 });
  const err = h('div');
  const ok = h('button', { class: 'btn' }, 'Lập phiếu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  const paid = await api.get<Page<Payment>>('/api/payments?status=SUCCESS&page=0&size=100').then((r) => r.items).catch(() => [] as Payment[]);
  const type = select([['MONTHLY_RENT', 'Tiền phòng tháng (gắn giao dịch)'], ['EXTRA_SERVICE', 'Dịch vụ thêm']], 'MONTHLY_RENT');
  const tx = select([['', '— Chọn giao dịch đã thu —'], ...paid.map((p) => [String(p.transactionId), `${p.invoiceCode} · ${money(p.amountPaid)} ₫ · ${dateTime(p.paymentDate)}`] as [string, string])], '');
  const amount = input({ type: 'number', min: '1', step: '1000', class: 'input num-in' });
  const note = input({ placeholder: 'Ghi chú (không bắt buộc)', maxlength: '200' });
  tx.addEventListener('change', () => { const p = paid.find((x) => String(x.transactionId) === tx.value); if (p) amount.value = String(p.amountPaid); });
  d.body.append(err, field('Loại phiếu', type), field('Giao dịch', tx, 'Tiền phòng bắt buộc chọn giao dịch đã thành công; số tiền phiếu không vượt số tiền giao dịch'),
    field('Số tiền (₫)', amount), field('Ghi chú', note));
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!(Number(amount.value) > 0)) { err.replaceChildren(formError('Nhập số tiền lớn hơn 0.')); return; }
    if (type.value === 'MONTHLY_RENT' && !tx.value) { err.replaceChildren(formError('Phiếu thu tiền phòng phải gắn với một giao dịch đã thu thành công.')); return; }
    try {
      await api.post('/api/receipts', { transactionId: tx.value ? Number(tx.value) : null, voucherType: type.value, amount: Number(amount.value), note: note.value.trim() || null });
      d.close(); toast('Đã lập phiếu thu'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
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
          // ưu tiên xác nhận giao dịch QR đang chờ của hóa đơn này, tránh để lại giao dịch chờ mồ côi
          const pend = await api.get<Page<Payment>>(`/api/payments?invoiceId=${m.invoiceId}&status=FAILED&size=20`).then((r) => r.items).catch(() => [] as Payment[]);
          if (pend.length) { d.close(); confirmPayment(pend[0], done); return; }
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
  if (p.status === 'FAILED') {
    const close = h('button', { class: 'btn danger' }, 'Đóng giao dịch chờ');
    close.addEventListener('click', async () => {
      if (!(await confirmDialog('Đóng giao dịch chờ', `Giao dịch chờ ${money(p.amountPaid)} ₫ của hóa đơn ${p.invoiceCode} sẽ bị đóng, không cộng vào hóa đơn. Dùng khi khách chuyển sai số tiền hoặc bỏ giữa chừng. Tiếp tục?`, 'Đóng giao dịch', true))) return;
      try { await api.post(`/api/payments/${p.transactionId}/cancel`); d.close(); toast('Đã đóng giao dịch chờ'); reload(); } catch (x) { toast(errText(x), 'err'); }
    });
    d.body.append(h('div', { class: 'danger-zone' }, h('p', null, 'Khách chuyển sai số tiền hoặc không trả nữa? Đóng giao dịch chờ này rồi tạo giao dịch mới.'), close));
  }
  if (p.status === 'SUCCESS' && can('LANDLORD', 'MANAGER')) {
    const btn = h('button', { class: 'btn danger' }, 'Hủy giao dịch');
    btn.addEventListener('click', async () => {
      if (!(await confirmDialog('Hủy giao dịch thanh toán', `Hủy giao dịch ${money(p.amountPaid)} ₫ của hóa đơn ${p.invoiceCode}? Số tiền sẽ bị trừ khỏi hóa đơn. Chỉ dùng khi ghi nhầm.`, 'Hủy giao dịch', true))) return;
      try { await api.post(`/api/payment-transactions/${p.transactionId}/reverse`); d.close(); toast('Đã hủy giao dịch'); reload(); } catch (x) { toast(errText(x), 'err'); }
    });
    d.body.append(h('div', { class: 'danger-zone' }, h('p', null, 'Ghi nhầm số tiền hoặc hóa đơn? Hủy giao dịch để trừ lại khỏi hóa đơn.'), btn));
  } else if (p.status === 'SUCCESS') {
    d.body.append(h('p', { class: 'muted' }, 'Ghi nhầm giao dịch này? Chỉ chủ trọ hoặc quản lý hủy được, hãy báo họ.'));
  }
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'));
}
