import { requestBooking } from './bookings';
import { h, busy, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, privateImage, errText, loadSession, Page } from '../lib/api';
import { date, dateTime, money, monthLabel, isoDay } from '../lib/format';
import type { Contract, ContractDetail, Notification, QrInfo } from '../data/types';
import { dl, emptyState, field, formError, openDialog, plate, select, statusBadge, toast } from '../ui/kit';
import { signPad, ADJ, fmtVal, isNum } from './contracts';
import { input } from '../ui/kit';
import type { InvoiceDetail } from '../data/types';
import { routeLine } from '../ui/route';

interface MyInvoice { invoiceId: number; invoiceCode: string; issueDate: string; dueDate: string; totalAmount: number; paidAmount: number; remainingAmount: number; paymentStatus: string }

const owed = (i: MyInvoice) => i.paymentStatus !== 'PAID' && i.paymentStatus !== 'CANCELLED';

interface MyCheckout { checkoutId: number; status: string; expectedCheckoutDate: string; actualCheckoutDate: string | null }

const CHECKOUT_TEXT: Record<string, string> = {
  PENDING: 'Đã gửi, chờ chủ trọ hẹn kiểm tra phòng',
  INSPECTING: 'Chủ trọ đang kiểm tra phòng và quyết toán cọc',
  FINALIZED: 'Đã thanh lý hợp đồng',
  CANCELLED: 'Yêu cầu đã hủy',
};

function greeting(name: string): string {
  const hour = new Date().getHours();
  const when = hour < 11 ? 'Chào buổi sáng' : hour < 14 ? 'Chào buổi trưa' : hour < 18 ? 'Chào buổi chiều' : 'Chào buổi tối';
  return `${when}, ${name.split(' ').slice(-1)[0]}`;
}

/** Còn bao nhiêu ngày tới hạn; âm là đã quá hạn. */
function daysTo(iso: string): number {
  const d = new Date(iso); d.setHours(0, 0, 0, 0);
  const t = new Date(); t.setHours(0, 0, 0, 0);
  return Math.round((d.getTime() - t.getTime()) / 86400000);
}

export async function meHomePage(view: HTMLElement): Promise<void> {
  const s = loadSession()!;
  const [invoices, contracts, checkouts] = await Promise.all([
    api.get<MyInvoice[]>('/api/me/debts/invoices'),
    api.get<Contract[]>('/api/me/contracts'),
    api.get<MyCheckout[]>('/api/me/checkout-requests').catch(() => [] as MyCheckout[]),
  ]);
  const due = invoices.filter(owed).sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  const next = due[0];
  const total = due.reduce((a, i) => a + i.remainingAmount, 0);
  const live = contracts.find((x) => x.status === 'ACTIVE' || x.status === 'EXPIRING_SOON');
  const c = live ?? contracts[0];
  const unsigned = contracts.filter((x) => !x.digitalSignatureUrl && (x.status === 'ACTIVE' || x.status === 'EXPIRING_SOON'));
  const openCheckout = checkouts.find((x) => x.status === 'PENDING' || x.status === 'INSPECTING');
  const doneCheckout = checkouts.find((x) => x.status === 'FINALIZED');

  // Câu mở đầu nói thẳng việc cần làm, không bắt khách đọc bảng
  const headline = next
    ? daysTo(next.dueDate) < 0 ? `Bạn có hóa đơn quá hạn ${-daysTo(next.dueDate)} ngày.`
      : daysTo(next.dueDate) === 0 ? 'Hóa đơn của bạn đến hạn hôm nay.'
      : `Hóa đơn tiếp theo đến hạn sau ${daysTo(next.dueDate)} ngày.`
    : 'Bạn đã trả đủ tiền phòng. Không có việc gì cần làm.';

  const bill = next
    ? h('section', { class: 'panel bill-card' },
        h('div', { style: 'display:flex;justify-content:space-between;align-items:center;gap:8px' }, h('span', { style: 'font-weight:650' }, 'Tiền phòng cần trả'), statusBadge(next.paymentStatus)),
        h('div', { class: 'amount' }, money(next.remainingAmount), h('small', null, '₫')),
        h('p', { class: 'muted' }, `Hóa đơn ${next.invoiceCode} · hạn ${date(next.dueDate)}`),
        due.length > 1 ? h('p', { class: 'muted' }, `Còn ${due.length - 1} hóa đơn khác chưa trả, tổng cộng ${money(total)} ₫`) : null,
        h('button', { class: 'btn lg block', onclick: (e: Event) => payQr(next, e.currentTarget as HTMLElement) }, icon('qr', 20), 'Trả bằng mã QR'),
        h('p', { class: 'muted', style: 'font-size:13px' }, 'Mở ứng dụng ngân hàng, quét mã, giữ nguyên nội dung chuyển khoản. Hóa đơn tự cập nhật sau khi chủ trọ nhận được tiền.'))
    : h('section', { class: 'panel bill-card' }, h('span', { class: 'st ok', style: 'align-self:flex-start' }, icon('check', 14), 'Không còn khoản nợ'),
        h('div', { class: 'amount' }, '0', h('small', null, '₫')), h('p', { class: 'muted' }, 'Hóa đơn tháng sau sẽ hiện ở đây khi chủ trọ phát hành.'));

  // Hành trình của khách trong nhà trọ: tới đâu rồi, bước tiếp theo là gì
  const stage = doneCheckout ? 3 : openCheckout ? 2 : 1;
  const journey = h('section', { class: 'panel', style: 'padding:18px 20px' },
    h('h2', { style: 'font-size:16px;margin-bottom:16px' }, 'Hành trình ở trọ của bạn'),
    routeLine([
      { name: 'Nhận phòng', sub: c ? date(c.startDate) : '–', state: c ? 'done' : 'current' },
      { name: 'Đang ở', sub: c ? `đến ${date(c.endDate)}` : '–', state: stage === 1 ? 'current' : 'done' },
      { name: 'Báo trả phòng', sub: openCheckout ? 'Đang xử lý' : doneCheckout ? 'Xong' : 'Khi bạn cần', state: stage === 2 ? 'current' : stage === 3 ? 'done' : 'todo' },
      { name: 'Nhận lại cọc', sub: doneCheckout ? 'Đã thanh lý' : '–', state: stage === 3 ? 'done' : 'todo' },
    ]),
    openCheckout
      ? h('div', { class: 'route-next' }, icon('route', 20), h('p', null, h('b', null, `Báo trả phòng ngày ${date(openCheckout.expectedCheckoutDate)}. `), CHECKOUT_TEXT[openCheckout.status] ?? ''))
      : live && !doneCheckout
        ? h('div', { class: 'route-next' }, icon('info', 20), h('p', { style: 'flex:1' }, 'Sắp chuyển đi? Báo trước để chủ trọ hẹn kiểm tra phòng và hoàn tiền cọc.'),
            h('button', { class: 'btn secondary sm', onclick: () => requestCheckout(live, () => void meHomePage(view)) }, icon('logout', 14), 'Báo trả phòng'))
        : null,
  );

  const recent = invoices.slice().sort((a, b) => b.dueDate.localeCompare(a.dueDate)).slice(0, 4);
  const history = h('section', { class: 'panel panel-flush' },
    h('div', { class: 'panel-head' }, h('h2', null, 'Hóa đơn gần đây'), h('a', { class: 'btn ghost sm', href: '#/me/invoices' }, 'Xem tất cả')),
    recent.length ? h('div', null, ...recent.map((i) => h('div', { class: 'list-row' },
        h('div', { class: 'grow' }, h('b', { class: 'money' }, `${money(i.totalAmount)} ₫`), h('span', null, `${i.invoiceCode} · hạn ${date(i.dueDate)}`)), statusBadge(i.paymentStatus))))
      : emptyState('Chưa có hóa đơn', 'Hóa đơn hàng tháng sẽ hiện ở đây khi chủ trọ phát hành.'));

  const room = c
    ? h('section', { class: 'panel' }, h('div', { class: 'panel-head' }, h('h2', null, 'Phòng của bạn'), statusBadge(c.status)),
        h('div', { class: 'panel-body' }, h('div', { style: 'display:flex;gap:12px;align-items:center;margin-bottom:12px' }, plate(c.roomCode, 'lg'), h('span', { class: 'muted' }, `Hết hạn ${date(c.endDate)}`)),
          dl([['Giá thuê', `${money(c.monthlyRentPrice)} ₫/tháng`], ['Tiền cọc', `${money(c.depositAmount)} ₫`], ['Hạn trả mỗi kỳ', `ngày ${c.paymentDueDay}`]])))
    : null;

  const shortcuts = h('section', { class: 'panel panel-flush' },
    unsigned.length ? h('a', { class: 'list-row', href: '#/me/contracts', style: 'background:var(--warn-bg)' }, icon('sign', 22), h('div', { class: 'grow' }, h('b', null, 'Hợp đồng chờ bạn ký'), h('span', null, unsigned[0].contractCode)), icon('right', 18)) : null,
    h('a', { class: 'list-row', href: '#/me/support' }, icon('wrench', 22), h('div', { class: 'grow' }, h('b', null, 'Báo sự cố trong phòng'), h('span', null, 'Điện, nước, máy lạnh, ổ khóa...')), icon('right', 18)),
    h('a', { class: 'list-row', href: '#/me/contracts' }, icon('file', 22), h('div', { class: 'grow' }, h('b', null, 'Xem hợp đồng'), h('span', null, 'Điều khoản, tiền cọc, hạn trả')), icon('right', 18)));

  mount(view,
    h('div', { class: 'me-hello' }, h('h2', null, greeting(s.name)), h('p', { class: 'muted' }, headline)),
    h('div', { class: 'me-grid' },
      h('div', { class: 'stack' }, bill, journey, history),
      h('div', { class: 'stack' }, room, shortcuts),
    ),
  );
}

function requestCheckout(c: Contract, done: () => void): void {
  const d = openDialog('Báo trả phòng', { width: 440 });
  const err = h('div');
  const tomorrow = isoDay(1);
  const when = h('input', { type: 'date', class: 'input', min: isoDay(), value: tomorrow }) as HTMLInputElement;
  d.body.append(err,
    h('p', null, `Phòng ${c.roomCode}. Chủ trọ sẽ hẹn giờ kiểm tra phòng cùng bạn, trừ các khoản còn nợ hoặc hư hỏng (nếu có) vào tiền cọc ${money(c.depositAmount)} ₫ rồi hoàn phần còn lại. Tiền phòng vẫn được tính cho đến ngày trả phòng thực tế, nên hãy chọn đúng ngày bạn dọn đi.`),
    field('Ngày bạn dự định trả phòng', when, 'Báo trước càng sớm càng dễ sắp xếp'));
  const ok = h('button', { class: 'btn' }, 'Gửi báo trả phòng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Để sau'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.post('/api/me/checkout-requests', { contractId: c.contractId, expectedCheckoutDate: when.value }); d.close(); toast('Đã gửi báo trả phòng'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function openMyInvoice(i: MyInvoice): Promise<void> {
  const detail = await api.get<InvoiceDetail>(`/api/invoices/${i.invoiceId}/detail`).catch((e) => { toast(errText(e), 'err'); return null; });
  if (!detail) return;
  const d = openDialog(`Hóa đơn ${i.invoiceCode}`, { width: 460 });
  d.body.append(
    dl([['Hạn trả', date(i.dueDate)], ['Tổng', `${money(i.totalAmount)} ₫`], ['Đã trả', `${money(i.paidAmount)} ₫`], ['Còn lại', `${money(i.remainingAmount)} ₫`]]),
    h('div', { class: 'stack', style: 'gap:6px' }, ...detail.items.map((x) => h('div', { class: 'list-row' },
      h('div', { class: 'grow' }, h('b', null, x.itemName), h('span', null, `${x.quantity} × ${money(x.unitPrice)} ₫`)), h('b', { class: 'money' }, `${money(x.subtotal)} ₫`)))));
  const pays = h('div', null, h('span', { class: 'muted' }, 'Đang tải lịch sử thanh toán…'));
  d.body.append(h('section', null, h('h3', { style: 'margin:10px 0 6px' }, 'Lịch sử thanh toán'), pays));
  const PAY_METHOD: Record<string, string> = { CASH: 'tiền mặt', BANK_TRANSFER: 'chuyển khoản', QR_CODE: 'mã QR', GATEWAY: 'cổng thanh toán', PREPAYMENT: 'trừ vào số dư' };
  void api.get<Array<{ id: number; method: string | null; status: string | null; amount: number; paidAt: string | null }>>(`/api/me/debts/invoices/${i.invoiceId}/payments`).then((l) => {
    pays.replaceChildren(l.length ? h('ul', { style: 'margin:0;padding-left:18px' }, ...l.map((x) => h('li', null, `${money(x.amount)} ₫ · ${PAY_METHOD[x.method ?? ''] ?? x.method ?? ''}${x.paidAt ? ` · ${dateTime(x.paidAt)}` : ''}${x.status && x.status !== 'SUCCESS' ? ' · chờ chủ trọ xác nhận' : ''}`))) : h('p', { class: 'muted', style: 'margin:0' }, 'Chưa có khoản thanh toán nào.'));
  }).catch((e) => pays.replaceChildren(formError(errText(e))));
  d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Đóng'));
}

function openHistory(code: string, d0: ContractDetail): void {
  const hist = d0.adjustments.filter((a) => !(isNum(a.oldValue) && isNum(a.newValue) && Number(a.oldValue) === Number(a.newValue)));
  const d = openDialog(`Lịch sử hợp đồng ${code}`, { width: 480 });
  d.body.append(hist.length
    ? h('ol', { class: 'timeline' }, ...hist.map((a) => h('li', null, h('span', { class: 'pip' }),
        h('div', null, h('b', null, ADJ[a.adjustmentType] ?? a.adjustmentType), h('span', null, `${a.oldValue ? fmtVal(a.oldValue) + ' → ' : ''}${fmtVal(a.newValue ?? '')} · ${dateTime(a.adjustedAt)}`)))))
    : h('p', { class: 'muted' }, 'Hợp đồng chưa có thay đổi nào kể từ lúc lập.'));
  d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Đóng'));
}

async function payQr(inv: MyInvoice, btn: HTMLElement): Promise<void> {
  await busy(btn, async () => {
    try {
      const q = await api.post<QrInfo>('/api/me/payments/qr', { invoiceId: inv.invoiceId, method: 'QR_CODE' });
      const d = openDialog('Quét mã để trả tiền', { width: 420 });
      d.body.append(
        h('div', { class: 'qr-box' }, q.qrImageUrl ? privateImage(q.qrImageUrl, 'Mã QR ngân hàng') : h('p', { class: 'muted' }, 'Chủ trọ chưa tải ảnh QR. Hãy chuyển khoản theo thông tin bên dưới.')),
        dl([['Số tiền', `${money(q.payment.amountPaid)} ₫`], ['Ngân hàng', q.bankName], ['Số tài khoản', q.accountNumber], ['Chủ tài khoản', q.accountHolder], ['Nội dung', h('span', { class: 'plate' }, q.transferContent)]]),
        h('p', { class: 'muted' }, 'Giữ nguyên nội dung chuyển khoản để chủ trọ đối chiếu. Hóa đơn được cập nhật sau khi chủ trọ xác nhận đã nhận tiền.'),
      );
      d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Đã chuyển xong'));
    } catch (e) { toast(errText(e), 'err'); }
  });
}

export async function meInvoicesPage(view: HTMLElement): Promise<void> {
  const list = (await api.get<MyInvoice[]>('/api/me/debts/invoices')).filter((i) => i.paymentStatus !== 'CANCELLED');
  view.replaceChildren(
    h('h2', null, 'Hóa đơn của tôi'),
    list.length
      ? h('section', { class: 'panel panel-flush' }, ...[...list].sort((a, b) => Number(owed(b)) - Number(owed(a)) || a.dueDate.localeCompare(b.dueDate)).map((i) => h('div', { class: 'inv-row' },
          h('div', { class: 'inv-top' }, h('b', { class: 'money', style: 'font-size:18px' }, `${money(i.totalAmount)} ₫`), statusBadge(i.paymentStatus)),
          h('span', { class: 'muted' }, `${i.invoiceCode} · hạn ${date(i.dueDate)}`),
          h('button', { class: 'btn secondary', onclick: () => void openMyInvoice(i) }, icon('receipt', 16), 'Xem chi tiết các khoản'),
          owed(i) && i.paidAmount > 0 ? h('span', { class: 'muted' }, `Đã trả ${money(i.paidAmount)} ₫, còn ${money(i.remainingAmount)} ₫`) : null,
          owed(i) ? h('button', { class: 'btn block', onclick: (e: Event) => payQr(i, e.currentTarget as HTMLElement) }, icon('qr', 16), `Trả ${money(i.remainingAmount)} ₫ bằng QR`) : null,
          owed(i) ? h('button', { class: 'btn secondary block', onclick: (e: Event) => payOnline(i, e.currentTarget as HTMLElement) }, icon('card', 16), 'Thanh toán online (payOS)') : null)))
      : h('section', { class: 'panel' }, emptyState('Chưa có hóa đơn', 'Hóa đơn hàng tháng sẽ hiện ở đây khi chủ trọ phát hành.')),
    receiptsBox(),
  );
}

export async function meContractsPage(view: HTMLElement): Promise<void> {
  const list = await api.get<Contract[]>('/api/me/contracts');
  view.replaceChildren(
    h('h2', null, 'Hợp đồng của tôi'),
    myRoomsBox(),
    list.length ? h('div', { class: 'stack' }, ...list.map((c) => h('section', { class: 'panel' },
      h('div', { class: 'panel-head' }, h('div', { style: 'display:flex;gap:10px;align-items:center' }, plate(c.roomCode), h('b', null, c.contractCode)), statusBadge(c.status)),
      h('div', { class: 'panel-body stack', style: 'gap:14px' },
        dl([['Thời hạn', `${date(c.startDate)} – ${date(c.endDate)}`], ['Giá thuê', `${money(c.monthlyRentPrice)} ₫/tháng`], ['Tiền cọc', `${money(c.depositAmount)} ₫`], ['Hạn trả mỗi kỳ', `ngày ${c.paymentDueDay}`], ['Phạt trễ hạn', `${money(c.lateFeePerDay ?? 0)} ₫/ngày`]]),
        c.digitalSignatureUrl
          ? h('span', { class: 'st ok', style: 'align-self:flex-start' }, icon('check', 14), 'Bạn đã ký hợp đồng này')
          : (c.status === 'ACTIVE' || c.status === 'EXPIRING_SOON') ? h('button', { class: 'btn lg block', onclick: () => signPad(c.contractId, () => void meContractsPage(view), true) }, icon('sign', 20), 'Ký hợp đồng') : null,
        h('button', { class: 'btn secondary block', onclick: async () => {
          const d = await api.get<ContractDetail>(`/api/me/contracts/${c.contractId}`).catch(() => null);
          if (d) openHistory(c.contractCode, d); else toast('Chưa tải được lịch sử, thử lại sau.', 'err');
        } }, 'Xem lịch sử thay đổi'))))) : h('section', { class: 'panel' }, emptyState('Chưa có hợp đồng', 'Hợp đồng sẽ hiện ở đây sau khi chủ trọ lập cho bạn.')),
  );
}

export async function meNotificationsPage(view: HTMLElement): Promise<void> {
  const r = await api.get<Page<Notification>>('/api/me/notifications?size=30');
  const action = (label: string, apply: () => Promise<unknown>) => {
    const button = h('button', { class: 'btn ghost sm' }, label);
    button.addEventListener('click', () => busy(button, async () => {
      try { await apply(); await meNotificationsPage(view); }
      catch (e) { toast(errText(e), 'err'); }
    }));
    return button;
  };
  const readAll = action('Đánh dấu đã đọc hết', () => api.patch('/api/me/notifications/read-all'));
  view.replaceChildren(
    h('div', { style: 'display:flex;justify-content:space-between;align-items:center' }, h('h2', null, 'Thông báo'), r.items.some((n) => !n.isRead) ? readAll : null),
    r.items.length
      ? h('section', { class: 'panel panel-flush' }, ...r.items.map((n) => h('div', { class: 'list-row', style: n.isRead ? '' : 'background:var(--primary-faint)' },
          h('div', { class: 'grow' }, h('b', null, n.title), h('span', { style: 'display:block' }, n.content), h('span', null, dateTime(n.createdAt))),
          h('div', { class: 'stack' },
            n.isRead
              ? action('Xóa', () => api.del('/api/me/notifications/' + n.userNotificationId))
              : action('Đánh dấu đã đọc', () => api.patch(`/api/me/notifications/${n.userNotificationId}/read`))))))
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

interface MyEquip { equipmentRequestId: number; roomCode: string; equipmentName: string; requestType: string; estimatedMonthlyFee: number | null; status: string; note: string | null }
const EQ_LABEL: Record<string, [string, string]> = { PENDING: ['Chờ chủ trọ duyệt', 'warn'], APPROVED: ['Đã duyệt, chờ lắp đặt', 'info'], REJECTED: ['Bị từ chối', 'off'], INSTALLED: ['Đã lắp đặt, tính phụ phí hàng tháng', 'ok'], REMOVED: ['Đã tháo', 'off'] };

export async function meSupportPage(view: HTMLElement): Promise<void> {
  const list = await api.get<MyTicket[]>('/api/me/maintenance-requests');
  const equip = await api.get<MyEquip[]>('/api/me/equipment-requests').catch(() => [] as MyEquip[]);
  const booking = h('button', { class: 'btn secondary block', onclick: () => requestBooking() }, 'Gửi yêu cầu thuê phòng');
  const send = h('button', { class: 'btn lg block', onclick: () => newTicket(() => void meSupportPage(view)) }, icon('plus', 20), 'Báo sự cố');
  mount(view,
    h('h2', null, 'Hỗ trợ'),
    send,
    h('button', { class: 'btn secondary block', onclick: () => newEquipment(() => void meSupportPage(view)) }, icon('plus', 20), 'Xin lắp hoặc dùng thêm thiết bị'),
    booking,
    equip.length ? h('section', { class: 'panel panel-flush' }, h('h3', { style: 'padding:12px 16px 0' }, 'Yêu cầu thiết bị của bạn'), ...equip.map((q) => h('div', { class: 'inv-row' },
      h('div', { class: 'inv-top' }, h('b', null, q.equipmentName), (() => { const [l, tone] = EQ_LABEL[q.status] ?? [q.status, 'off']; return h('span', { class: `st ${tone}` }, l); })()),
      h('span', { class: 'muted' }, `Phòng ${q.roomCode} · ${q.requestType === 'INSTALL' ? 'Lắp mới' : 'Dùng thêm'}${q.estimatedMonthlyFee ? ` · phí dự kiến ${money(q.estimatedMonthlyFee)} ₫/tháng` : ''}`),
      q.note ? h('span', { class: 'muted' }, `Ghi chú của chủ trọ: ${q.note}`) : null))) : null,
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
  // danh mục do chủ trọ quản lý (UC113); lỗi thì dùng danh sách mặc định
  void api.get<Array<{ categoryCode: string; categoryName: string }>>('/api/me/incident-categories?type=INCIDENT').then((l) => { if (l.length) { cat.replaceChildren(...l.map((c) => new Option(c.categoryName, c.categoryCode))); } }).catch(() => undefined);
  const photo = input({ type: 'file', accept: 'image/*', 'aria-label': 'Ảnh sự cố' });
  const desc = h('textarea', { class: 'textarea', placeholder: 'Mô tả sự cố để chủ trọ xử lý nhanh' }) as HTMLTextAreaElement;
  const urgent = h('input', { type: 'checkbox' }) as HTMLInputElement;
  d.body.append(err, field('Loại sự cố', cat), field('Mô tả', desc), field('Ảnh sự cố (không bắt buộc)', photo), h('label', { style: 'display:flex;gap:8px;align-items:center;font-weight:500' }, urgent, 'Khẩn cấp (nguy hiểm hoặc mất điện nước hoàn toàn)'));
  const ok = h('button', { class: 'btn' }, 'Gửi');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      const body = { category: cat.value, description: desc.value.trim(), priority: urgent.checked ? 'URGENT' : 'NORMAL' };
      if (photo.files?.[0]) {
        const form = new FormData();
        form.append('request', new Blob([JSON.stringify(body)], { type: 'application/json' }));
        form.append('file', photo.files[0]);
        await api.uploadForm('/api/me/maintenance-requests', form);
      } else await api.post('/api/me/maintenance-requests', body);
      d.close(); toast('Đã gửi yêu cầu'); done();
    }
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

function newEquipment(done: () => void): void {
  const d = openDialog('Xin lắp hoặc dùng thêm thiết bị');
  const err = h('div');
  const kind = select([['INSTALL', 'Lắp thiết bị mới trong phòng'], ['EXTRA_USAGE', 'Dùng thêm thiết bị (tính thêm phí)']]);
  const cat = select([['', 'Không chọn nhóm']]);
  void api.get<Array<{ categoryId: number; categoryName: string }>>('/api/me/incident-categories?type=EQUIPMENT').then((l) => l.forEach((c) => cat.append(new Option(c.categoryName, String(c.categoryId))))).catch(() => undefined);
  const name = input({ placeholder: 'Ví dụ: Tủ lạnh mini, máy giặt', maxlength: '100', 'aria-label': 'Tên thiết bị' });
  const fee = input({ type: 'number', min: '0', step: '1000', placeholder: 'Nếu đã thỏa thuận', class: 'input num-in', 'aria-label': 'Phí dự kiến mỗi tháng' });
  d.body.append(err, field('Loại yêu cầu', kind), field('Nhóm thiết bị', cat), field('Tên thiết bị', name), field('Phí dự kiến mỗi tháng (₫, không bắt buộc)', fee));
  const ok = h('button', { class: 'btn' }, 'Gửi yêu cầu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!name.value.trim()) { err.replaceChildren(formError('Nhập tên thiết bị.')); return; }
    try { await api.post('/api/me/equipment-requests', { requestType: kind.value, categoryId: cat.value ? Number(cat.value) : null, equipmentName: name.value.trim(), estimatedMonthlyFee: fee.value ? Number(fee.value) : null }); d.close(); toast('Đã gửi yêu cầu, chờ chủ trọ duyệt'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

// ------------------------------------------------------------------ UC86: thanh toán online qua payOS (cần chủ trọ cấu hình khóa payOS)
async function payOnline(inv: MyInvoice, btn: HTMLElement): Promise<void> {
  await busy(btn, async () => {
    try {
      const q = await api.post<QrInfo>('/api/me/payments/qr', { invoiceId: inv.invoiceId, method: 'GATEWAY' });
      const c = await api.post<{ checkoutUrl: string }>(`/api/me/payments/${q.payment.transactionId}/payos/checkout`);
      window.open(c.checkoutUrl, '_blank', 'noopener');
      const d = openDialog('Thanh toán online', { width: 420 });
      d.body.append(h('p', null, 'Trang thanh toán payOS đã mở ở tab mới. Thanh toán xong, quay lại đây bấm Kiểm tra để cập nhật hóa đơn.'));
      const check = h('button', { class: 'btn' }, 'Kiểm tra đã thanh toán');
      check.addEventListener('click', () => busy(check, async () => {
        try {
          const r = await api.post<{ confirmed: boolean; message: string }>(`/api/me/payments/${q.payment.transactionId}/payos/reconcile`);
          toast(r.confirmed ? 'Đã ghi nhận thanh toán' : 'payOS chưa xác nhận đã nhận tiền, thử lại sau ít phút', r.confirmed ? 'ok' : 'err');
          if (r.confirmed) { d.close(); location.reload(); }
        } catch (e) { toast(errText(e), 'err'); }
      }));
      d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'), check);
    } catch (e) {
      const m = errText(e);
      toast(/404|khong tim thay|not found/i.test(m) || (e instanceof Error && /Lỗi 404/.test(e.message)) ? 'Cổng thanh toán online chưa được bật. Hãy trả bằng mã QR hoặc liên hệ chủ trọ.' : m, 'err');
    }
  });
}

// ------------------------------------------------------------------ UC90: biên nhận của tôi
interface MyReceipt { voucherId: number; voucherCode: string; invoiceCode: string; voucherType: string; amount: number; createdAt: string }

function receiptsBox(): HTMLElement {
  const box = h('section', { class: 'panel panel-flush' }, h('p', { class: 'muted', style: 'padding:12px 16px' }, 'Đang tải biên nhận…'));
  void api.get<MyReceipt[]>('/api/me/receipts').then((l) => {
    box.replaceChildren(h('h3', { style: 'padding:12px 16px 0' }, 'Biên nhận đã nhận'), ...(l.length ? l.map((r) => h('div', { class: 'inv-row' },
      h('div', { class: 'inv-top' }, h('b', null, r.voucherCode), h('b', { class: 'money' }, `${money(r.amount)} ₫`)),
      h('span', { class: 'muted' }, `Hóa đơn ${r.invoiceCode} · ${dateTime(r.createdAt)}`),
      h('button', { class: 'btn secondary', onclick: async () => {
        const s = loadSession();
        const res = await fetch(`/api/me/receipts/${r.voucherId}/print`, { headers: s ? { Authorization: `Bearer ${s.token}` } : {} });
        if (!res.ok) { toast(`Không mở được biên nhận (lỗi ${res.status}).`, 'err'); return; }
        window.open(URL.createObjectURL(new Blob([await res.text()], { type: 'text/html' })), '_blank');
      } }, icon('print', 16), 'Xem và in biên nhận'))) : [h('p', { class: 'muted', style: 'padding:0 16px 12px' }, 'Chưa có biên nhận nào. Biên nhận hiện ở đây sau khi chủ trọ lập phiếu thu.')]));
  }).catch((e) => box.replaceChildren(formError(errText(e))));
  return box;
}

// ------------------------------------------------------------------ UC36: phòng của tôi và người ở cùng
interface MyRoom { roomId: number; roomCode: string; areaSqm: number; maxOccupants: number; contractCode: string; residents: Array<{ fullName: string; relationshipWithPrimary: string | null }> }

export function myRoomsBox(): HTMLElement {
  const box = h('section', { class: 'panel' }, h('p', { class: 'muted' }, 'Đang tải thông tin phòng…'));
  void api.get<MyRoom[]>('/api/me/rooms').then((l) => {
    box.replaceChildren(...(l.length ? l.map((r) => h('div', { class: 'panel-body stack', style: 'gap:10px' },
      h('div', { style: 'display:flex;gap:10px;align-items:center' }, plate(r.roomCode, 'lg'), h('span', { class: 'muted' }, `Hợp đồng ${r.contractCode}`)),
      dl([['Diện tích', `${r.areaSqm} m²`], ['Số người tối đa', String(r.maxOccupants)], ['Người ở cùng', r.residents.length ? r.residents.map((x) => `${x.fullName}${x.relationshipWithPrimary ? ` (${x.relationshipWithPrimary})` : ''}`).join(', ') : 'Chưa khai báo']]))) : [emptyState('Chưa có phòng', 'Thông tin phòng hiện ở đây khi bạn có hợp đồng.')]));
  }).catch((e) => box.replaceChildren(formError(errText(e))));
  return box;
}
