import { h, busy, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, loadSession, Page } from '../lib/api';
import { date, dateTime, money, monthLabel } from '../lib/format';
import type { Contract, ContractDetail, Notification, QrInfo } from '../data/types';
import { dl, emptyState, field, formError, openDialog, plate, select, statusBadge, toast } from '../ui/kit';
import { signPad } from './contracts';
import { routeLine } from '../ui/route';

interface MyInvoice { invoiceId: number; invoiceCode: string; issueDate: string; dueDate: string; totalAmount: number; paidAmount: number; remainingAmount: number; paymentStatus: string }

const owed = (i: MyInvoice) => i.paymentStatus !== 'PAID' && i.paymentStatus !== 'CANCELLED';

export async function meHomePage(view: HTMLElement): Promise<void> {
  const s = loadSession()!;
  const [invoices, contracts] = await Promise.all([api.get<MyInvoice[]>('/api/me/debts/invoices'), api.get<Contract[]>('/api/me/contracts')]);
  const due = invoices.filter(owed).sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  const next = due[0];
  const total = due.reduce((a, i) => a + i.remainingAmount, 0);
  const c = contracts.find((x) => x.status === 'ACTIVE' || x.status === 'EXPIRING_SOON') ?? contracts[0];
  const unsigned = contracts.filter((x) => !x.digitalSignatureUrl && (x.status === 'ACTIVE' || x.status === 'EXPIRING_SOON'));

  mount(view,
    h('p', { style: 'font-size:18px;font-weight:650' }, `Chào ${s.name.split(' ').slice(-1)[0]},`),
    next
      ? h('section', { class: 'panel bill-card' },
          h('div', { style: 'display:flex;justify-content:space-between;align-items:center;gap:8px' }, h('span', { style: 'font-weight:650' }, 'Cần trả'), statusBadge(next.paymentStatus)),
          h('div', { class: 'amount' }, money(next.remainingAmount), h('small', null, '₫')),
          h('p', { class: 'muted' }, `Hóa đơn ${next.invoiceCode} · hạn ${date(next.dueDate)}`),
          due.length > 1 ? h('p', { class: 'muted' }, `Tổng ${due.length} hóa đơn chưa trả: ${money(total)} ₫`) : null,
          h('button', { class: 'btn lg block', onclick: (e: Event) => payQr(next, e.currentTarget as HTMLElement) }, icon('qr', 20), 'Trả bằng mã QR'))
      : h('section', { class: 'panel bill-card' }, h('span', { class: 'st ok', style: 'align-self:flex-start' }, icon('check', 14), 'Không còn khoản nợ'), h('div', { class: 'amount' }, '0', h('small', null, '₫')), h('p', { class: 'muted' }, 'Bạn đã trả đủ các hóa đơn. Cảm ơn bạn.')),
    h('section', { class: 'panel', style: 'padding:16px 18px' }, routeLine([
      { name: 'Hóa đơn đã phát hành', sub: invoices.length ? `${invoices.length} hóa đơn` : 'Chưa có', state: invoices.length ? 'done' : 'current' },
      { name: 'Trả tiền', sub: due.length ? `Còn ${due.length} hóa đơn` : 'Đã trả đủ', state: invoices.length && !due.length ? 'done' : due.length ? 'current' : 'todo', stuck: due.length || undefined, stuckLabel: 'hóa đơn' },
      { name: 'Hoàn tất', sub: invoices.length && !due.length ? 'Không còn nợ' : '–', state: invoices.length && !due.length ? 'current' : 'todo' },
    ])),
    unsigned.length ? h('a', { class: 'panel list-row', href: '#/me/contracts', style: 'background:var(--warn-bg);border-color:#e8c46a' }, icon('sign', 22), h('div', { class: 'grow' }, h('b', null, 'Hợp đồng chờ bạn ký'), h('span', null, unsigned[0].contractCode)), icon('right', 18)) : null,
    c ? h('section', { class: 'panel' }, h('div', { class: 'panel-head' }, h('h2', null, 'Phòng của bạn'), statusBadge(c.status)),
      h('div', { class: 'panel-body' }, h('div', { style: 'display:flex;gap:12px;align-items:center;margin-bottom:12px' }, plate(c.roomCode, 'lg'), h('span', { class: 'muted' }, `Hết hạn ${date(c.endDate)}`)),
        dl([['Giá thuê', `${money(c.monthlyRentPrice)} ₫/tháng`], ['Hạn trả mỗi kỳ', `ngày ${c.paymentDueDay}`]]))) : null,
  );
}

async function payQr(inv: MyInvoice, btn: HTMLElement): Promise<void> {
  await busy(btn, async () => {
    try {
      const q = await api.post<QrInfo>('/api/me/payments/qr', { invoiceId: inv.invoiceId, method: 'QR_CODE' });
      const d = openDialog('Quét mã để trả tiền', { width: 420 });
      d.body.append(
        h('div', { class: 'qr-box' }, q.qrImageUrl ? h('img', { src: q.qrImageUrl, alt: 'Mã QR ngân hàng' }) : h('p', { class: 'muted' }, 'Chủ trọ chưa tải ảnh QR. Hãy chuyển khoản theo thông tin bên dưới.')),
        dl([['Số tiền', `${money(q.payment.amountPaid)} ₫`], ['Ngân hàng', q.bankName], ['Số tài khoản', q.accountNumber], ['Chủ tài khoản', q.accountHolder], ['Nội dung', h('span', { class: 'plate' }, q.transferContent)]]),
        h('p', { class: 'muted' }, 'Giữ nguyên nội dung chuyển khoản để chủ trọ đối chiếu. Hóa đơn được cập nhật sau khi chủ trọ xác nhận đã nhận tiền.'),
      );
      d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Đã chuyển xong'));
    } catch (e) { toast(errText(e), 'err'); }
  });
}

export async function meInvoicesPage(view: HTMLElement): Promise<void> {
  const list = await api.get<MyInvoice[]>('/api/me/debts/invoices');
  view.replaceChildren(
    h('h2', null, 'Hóa đơn của tôi'),
    list.length
      ? h('section', { class: 'panel panel-flush' }, ...[...list].sort((a, b) => Number(owed(b)) - Number(owed(a)) || a.dueDate.localeCompare(b.dueDate)).map((i) => h('div', { class: 'inv-row' },
          h('div', { class: 'inv-top' }, h('b', { class: 'money', style: 'font-size:18px' }, `${money(i.totalAmount)} ₫`), statusBadge(i.paymentStatus)),
          h('span', { class: 'muted' }, `${i.invoiceCode} · hạn ${date(i.dueDate)}`),
          owed(i) && i.paidAmount > 0 ? h('span', { class: 'muted' }, `Đã trả ${money(i.paidAmount)} ₫, còn ${money(i.remainingAmount)} ₫`) : null,
          owed(i) ? h('button', { class: 'btn block', onclick: (e: Event) => payQr(i, e.currentTarget as HTMLElement) }, icon('qr', 16), `Trả ${money(i.remainingAmount)} ₫`) : null)))
      : h('section', { class: 'panel' }, emptyState('Chưa có hóa đơn', 'Hóa đơn hàng tháng sẽ hiện ở đây khi chủ trọ phát hành.')),
  );
}

export async function meContractsPage(view: HTMLElement): Promise<void> {
  const list = await api.get<Contract[]>('/api/me/contracts');
  view.replaceChildren(
    h('h2', null, 'Hợp đồng của tôi'),
    list.length ? h('div', { class: 'stack' }, ...list.map((c) => h('section', { class: 'panel' },
      h('div', { class: 'panel-head' }, h('div', { style: 'display:flex;gap:10px;align-items:center' }, plate(c.roomCode), h('b', null, c.contractCode)), statusBadge(c.status)),
      h('div', { class: 'panel-body stack', style: 'gap:14px' },
        dl([['Thời hạn', `${date(c.startDate)} – ${date(c.endDate)}`], ['Giá thuê', `${money(c.monthlyRentPrice)} ₫/tháng`], ['Tiền cọc', `${money(c.depositAmount)} ₫`], ['Hạn trả mỗi kỳ', `ngày ${c.paymentDueDay}`], ['Phạt trễ hạn', `${money(c.lateFeePerDay ?? 0)} ₫/ngày`]]),
        c.digitalSignatureUrl
          ? h('span', { class: 'st ok', style: 'align-self:flex-start' }, icon('check', 14), 'Bạn đã ký hợp đồng này')
          : (c.status === 'ACTIVE' || c.status === 'EXPIRING_SOON') ? h('button', { class: 'btn lg block', onclick: () => signPad(c.contractId, () => void meContractsPage(view), true) }, icon('sign', 20), 'Ký hợp đồng') : null,
        h('button', { class: 'btn secondary block', onclick: async () => {
          const d = await api.get<ContractDetail>(`/api/me/contracts/${c.contractId}`).catch(() => null);
          if (d) toast(`Đã tải ${d.adjustments.length} mục lịch sử`);
        } }, 'Xem lịch sử thay đổi'))))) : h('section', { class: 'panel' }, emptyState('Chưa có hợp đồng', 'Hợp đồng sẽ hiện ở đây sau khi chủ trọ lập cho bạn.')),
  );
}

export async function meNotificationsPage(view: HTMLElement): Promise<void> {
  const r = await api.get<Page<Notification>>('/api/me/notifications?size=30');
  const readAll = h('button', { class: 'btn ghost sm', onclick: async () => { await api.patch('/api/me/notifications/read-all'); await meNotificationsPage(view); } }, 'Đánh dấu đã đọc hết');
  view.replaceChildren(
    h('div', { style: 'display:flex;justify-content:space-between;align-items:center' }, h('h2', null, 'Thông báo'), r.items.some((n) => !n.isRead) ? readAll : null),
    r.items.length
      ? h('section', { class: 'panel panel-flush' }, ...r.items.map((n) => h('div', { class: 'list-row', style: n.isRead ? '' : 'background:var(--primary-faint)' },
          h('div', { class: 'grow' }, h('b', null, n.title), h('span', { style: 'display:block' }, n.content), h('span', null, dateTime(n.createdAt))),
          n.isRead ? null : h('span', { class: 'st info' }, 'Mới'))))
      : h('section', { class: 'panel' }, emptyState('Chưa có thông báo', 'Hóa đơn mới, nhắc hạn và xác nhận thanh toán sẽ hiện ở đây.')),
  );
}

void monthLabel;

// ------------------------------------------------------------------ hỗ trợ: báo sự cố, theo dõi, đánh giá
const SUPPORT_CATEGORIES: Array<[string, string]> = [
  ['ELECTRIC_ISSUE', 'Điện: mất điện, chập, nhảy aptomat'], ['WATER_ISSUE', 'Nước: yếu, rò rỉ, tắc'], ['AIR_CONDITIONER', 'Máy lạnh'],
  ['DOOR_LOCK', 'Cửa, ổ khóa'], ['INTERNET', 'Mạng Internet'], ['METER_CHECK', 'Nghi ngờ công tơ tính sai'], ['OTHER_INCIDENT', 'Sự cố khác'],
];
const TICKET_LABEL: Record<string, [string, string]> = {
  OPEN: ['Đã gửi, chờ tiếp nhận', 'warn'], ASSIGNED: ['Đã phân công', 'info'], IN_PROGRESS: ['Đang xử lý', 'info'], RESOLVED: ['Đã xử lý xong', 'ok'], CLOSED: ['Đã đóng', 'off'],
};

interface MyTicket { requestId: number; roomCode: string; category: string; description: string; priority: string; status: string; createdAt: string; log: { resolutionNotes: string | null; tenantRating: number | null } | null }

export async function meSupportPage(view: HTMLElement): Promise<void> {
  const list = await api.get<MyTicket[]>('/api/me/maintenance-requests');
  const send = h('button', { class: 'btn lg block', onclick: () => newTicket(() => void meSupportPage(view)) }, icon('plus', 20), 'Báo sự cố');
  mount(view,
    h('h2', null, 'Hỗ trợ'),
    send,
    list.length
      ? h('section', { class: 'panel panel-flush' }, ...list.map((t) => h('div', { class: 'inv-row' },
          h('div', { class: 'inv-top' }, h('b', null, SUPPORT_CATEGORIES.find((c) => c[0] === t.category)?.[1] ?? t.category), (() => { const [l, tone] = TICKET_LABEL[t.status] ?? [t.status, 'off']; return h('span', { class: `st ${tone}` }, l); })()),
          h('span', null, t.description),
          h('span', { class: 'muted' }, `Phòng ${t.roomCode} · ${dateTime(t.createdAt)}`),
          t.log?.resolutionNotes ? h('span', { class: 'muted' }, `Kết quả: ${t.log.resolutionNotes}`) : null,
          t.status === 'RESOLVED' || t.status === 'CLOSED'
            ? (t.log?.tenantRating ? h('span', { class: 'st ok', style: 'align-self:flex-start' }, `Bạn đã đánh giá ${t.log.tenantRating}/5 sao`) : h('button', { class: 'btn secondary', onclick: () => rate(t.requestId, () => void meSupportPage(view)) }, 'Đánh giá chất lượng xử lý'))
            : null)))
      : h('section', { class: 'panel' }, emptyState('Chưa có yêu cầu nào', 'Điện nước hư, máy lạnh, ổ khóa... bấm Báo sự cố, chủ trọ sẽ phân công người xử lý.')),
  );
}

function newTicket(done: () => void): void {
  const d = openDialog('Báo sự cố');
  const err = h('div');
  const cat = select(SUPPORT_CATEGORIES);
  const desc = h('textarea', { class: 'textarea', placeholder: 'Mô tả sự cố để chủ trọ xử lý nhanh' }) as HTMLTextAreaElement;
  const urgent = h('input', { type: 'checkbox' }) as HTMLInputElement;
  d.body.append(err, field('Loại sự cố', cat), field('Mô tả', desc), h('label', { style: 'display:flex;gap:8px;align-items:center;font-weight:500' }, urgent, 'Khẩn cấp (nguy hiểm hoặc mất điện nước hoàn toàn)'));
  const ok = h('button', { class: 'btn' }, 'Gửi');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.post('/api/me/maintenance-requests', { category: cat.value, description: desc.value.trim(), priority: urgent.checked ? 'URGENT' : 'NORMAL' }); d.close(); toast('Đã gửi yêu cầu'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function rate(id: number, done: () => void): void {
  const d = openDialog('Đánh giá chất lượng xử lý');
  const stars = select([['5', '5 sao: rất hài lòng'], ['4', '4 sao'], ['3', '3 sao'], ['2', '2 sao'], ['1', '1 sao: không hài lòng']]);
  d.body.append(field('Mức độ hài lòng', stars));
  const ok = h('button', { class: 'btn' }, 'Gửi đánh giá');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.post(`/api/me/maintenance-requests/${id}/rating`, { rating: Number(stars.value) }); d.close(); toast('Cảm ơn bạn đã đánh giá'); done(); } catch (e) { toast(errText(e), 'err'); }
  }));
}
