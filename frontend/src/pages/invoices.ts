import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, can, errText, loadSession, Page } from '../lib/api';
import { getMonth } from '../lib/ctx';
import { date, dateTime, money, monthLabel } from '../lib/format';
import type { Invoice, InvoiceDetail, Payment } from '../data/types';
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
    if (!res.ok) { toast(`Không mở được bản in (lỗi ${res.status}).`, 'err'); return; }
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
  const app = h('button', { class: 'btn secondary' }, icon('send', 16), 'Gửi vào ứng dụng');
  app.addEventListener('click', async () => {
    try { const r = await api.post<{ status: string }>(`/api/invoices/${id}/send-app`); toast(r?.status === 'SENT' ? 'Đã gửi thông báo hóa đơn vào ứng dụng của khách' : 'Chưa gửi được: khách chưa có tài khoản đăng nhập', r?.status === 'SENT' ? 'ok' : 'err'); void paintNotifs(); }
    catch (e) { toast(errText(e), 'err'); }
  });
  const share = h('button', { class: 'btn secondary', disabled: inv.paymentStatus === 'CANCELLED' }, icon('route', 16), 'Liên kết chia sẻ');
  share.addEventListener('click', () => shareDialog(id));
  d.foot.style.flexWrap = 'wrap'; // nhiều nút: xuống dòng thay vì tràn khỏi ngăn
  d.foot.append(print, mail, app, share, pay);

  const notifs = h('div', null, 'Đang tải…');
  const paintNotifs = async () => {
    try {
      const list = await api.get<Array<{ notificationId: number; channel: string; sentAt: string; status: string }>>(`/api/invoices/${id}/notifications`);
      notifs.replaceChildren(list.length ? h('ul', { style: 'margin:0;padding-left:18px' }, ...list.map((n) => h('li', null, `${n.channel === 'EMAIL' ? 'Email' : n.channel === 'SYSTEM_APP' ? 'Ứng dụng' : n.channel} · ${n.status === 'SENT' ? 'đã gửi' : n.status === 'FAILED' ? 'gửi lỗi' : 'chờ gửi'} · ${dateTime(n.sentAt)}`))) : h('p', { class: 'muted', style: 'margin:0' }, 'Chưa gửi hóa đơn này cho khách.'));
    } catch (e) { notifs.replaceChildren(formError(errText(e))); }
  };
  d.body.append(h('section', null, h('h3', { style: 'margin-bottom:8px' }, 'Lịch sử gửi cho khách'), notifs));
  void paintNotifs();

  // UC91: các lần thanh toán (mỗi giao dịch là một lần, theo thứ tự thời gian)
  const inst = h('div', null);
  d.body.append(h('section', null, h('h3', { style: 'margin-bottom:8px' }, 'Các lần thanh toán'), inst));
  const METHOD: Record<string, string> = { CASH: 'tiền mặt', BANK_TRANSFER: 'chuyển khoản', QR_CODE: 'mã QR', GATEWAY: 'cổng thanh toán' };
  const STATE: Record<string, string> = { SUCCESS: '', FAILED: ' · chờ xác nhận', CANCELLED: ' · đã hủy', REVERSED: ' · đã hủy' };
  void api.get<Page<Payment>>(`/api/payments?invoiceId=${id}&size=50`).then((r) => {
    const rows = [...r.items].sort((a, b) => a.paymentDate.localeCompare(b.paymentDate));
    inst.replaceChildren(rows.length ? h('ul', { style: 'margin:0;padding-left:18px' }, ...rows.map((x, n) => h('li', null,
      `Lần ${n + 1}: ${money(x.amountPaid)} ₫ · ${METHOD[x.method] ?? x.method} · ${dateTime(x.paymentDate)}${x.receivedByName ? ` · ${x.receivedByName} nhận` : ''}${STATE[x.status] ?? ` · ${x.status}`}`)))
      : h('p', { class: 'muted', style: 'margin:0' }, 'Chưa có khoản thanh toán nào.'));
  }).catch((e) => inst.replaceChildren(formError(errText(e))));

  // UC92: khách trả dư thì chuyển phần dư thành số dư trả trước
  if (inv.paidAmount > inv.totalAmount) {
    const toBal = h('button', { class: 'btn secondary sm' }, icon('wallet', 14), 'Chuyển phần trả dư vào số dư');
    toBal.addEventListener('click', async () => {
      try { await api.post(`/api/invoices/${id}/overpayment/to-balance`); d.close(); toast('Đã chuyển phần trả dư vào số dư trả trước của khách'); reload(); }
      catch (e) { toast(errText(e), 'err'); }
    });
    d.body.append(h('div', { class: 'danger-zone' }, h('p', null, `Khách đã trả dư ${money(inv.paidAmount - inv.totalAmount)} ₫.`), toBal));
  }

  if (inv.paymentStatus !== 'CANCELLED' && can('LANDLORD', 'MANAGER')) {
    const adj = h('button', { class: 'btn secondary sm' }, icon('edit', 14), 'Điều chỉnh tổng tiền');
    adj.addEventListener('click', () => adjustDialog(inv, () => { d.close(); reload(); }));
    d.body.append(h('div', null, adj));
  }

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

function shareDialog(id: number): void {
  const d = openDialog('Liên kết chia sẻ hóa đơn');
  const box = h('div', { class: 'stack' }, 'Đang tạo liên kết…');
  d.body.append(box);
  d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Xong'));
  void (async () => {
    try {
      const r = await api.post<{ shareLink: string }>(`/api/invoices/${id}/share-link`);
      const url = `${location.origin}${location.pathname}#/share/${r.shareLink.split('/').pop()}`;
      const copy = h('button', { class: 'btn secondary' }, icon('check', 16), 'Chép liên kết');
      copy.addEventListener('click', async () => { try { await navigator.clipboard.writeText(url); toast('Đã chép liên kết'); } catch { toast('Không chép được, hãy chép tay', 'err'); } });
      const revoke = h('button', { class: 'btn danger' }, 'Thu hồi liên kết');
      revoke.addEventListener('click', async () => { try { await api.del(`/api/invoices/${id}/share-link`); d.close(); toast('Đã thu hồi liên kết'); } catch (e) { toast(errText(e), 'err'); } });
      box.replaceChildren(h('p', { class: 'muted' }, 'Ai có liên kết xem được tổng tiền và hạn trả của hóa đơn này, không cần đăng nhập. Thu hồi nếu gửi nhầm.'),
        h('input', { class: 'input', readonly: true, value: url, 'aria-label': 'Liên kết chia sẻ' }), h('div', { style: 'display:flex;gap:8px' }, copy, revoke));
    } catch (e) { box.replaceChildren(formError(errText(e))); }
  })();
}

function adjustDialog(inv: InvoiceDetail['invoice'], done: () => void): void {
  const d = openDialog(`Điều chỉnh hóa đơn ${inv.invoiceCode}`);
  const err = h('div');
  const total = input({ type: 'number', min: '0', step: '1000', value: String(inv.totalAmount), class: 'input num-in', 'aria-label': 'Tổng tiền mới' });
  const reason = h('textarea', { class: 'textarea', maxlength: '500', placeholder: 'Lý do điều chỉnh (bắt buộc)', 'aria-label': 'Lý do điều chỉnh' }) as HTMLTextAreaElement;
  d.body.append(err, h('p', { class: 'muted' }, `Tổng hiện tại ${money(inv.totalAmount)} ₫, đã trả ${money(inv.paidAmount)} ₫. Khách được báo trong ứng dụng khi hóa đơn thay đổi. Nếu tổng mới thấp hơn số đã trả, phần dư chuyển thành số dư trả trước của khách.`), field('Tổng tiền mới (₫)', total), field('Lý do', reason));
  const ok = h('button', { class: 'btn' }, 'Lưu điều chỉnh');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!reason.value.trim()) { err.replaceChildren(formError('Nhập lý do điều chỉnh.')); return; }
    try { await api.put(`/api/invoices/${inv.invoiceId}/total`, { newTotalAmount: Number(total.value), reason: reason.value.trim() }); d.close(); toast('Đã điều chỉnh hóa đơn và báo cho khách'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
