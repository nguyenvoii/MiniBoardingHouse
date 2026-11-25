import { busy, h } from '../lib/dom';
import { api, errText } from '../lib/api';
import { date, isoDay, money } from '../lib/format';
import { confirmDialog, field, formError, input, openDialog, select, statusBadge, table, toast } from '../ui/kit';

interface AvailableRoom { roomId: number; roomCode: string; maxOccupants: number }
interface Booking {
  bookingId: number;
  roomId: number;
  roomCode: string;
  tenantName: string;
  tenantPhone: string;
  expectedStartDate: string;
  occupantsCount: number;
  depositPaid: number;
  status: string;
  rejectionReason: string | null;
}

export function requestBooking(): void {
  const dialog = openDialog('Gửi yêu cầu thuê phòng');
  const error = h('div', { role: 'alert' }, 'Đang tải phòng trống…');
  const room = select([['', 'Chọn phòng']]);
  const start = input({ type: 'date', min: isoDay(), required: true });
  const occupants = input({ type: 'number', min: '1', step: '1', value: '1', required: true });
  const send = h('button', { class: 'btn', disabled: true }, 'Gửi yêu cầu');
  dialog.body.append(error, field('Phòng trống', room), field('Ngày dự kiến thuê', start), field('Số người ở', occupants));
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Đóng'), send);
  void api.get<AvailableRoom[]>('/api/me/bookings/available-rooms').then((rooms) => {
    room.append(...rooms.map((item) => h('option', { value: String(item.roomId) }, item.roomCode + ' · tối đa ' + item.maxOccupants + ' người')));
    error.textContent = rooms.length ? '' : 'Hiện chưa có phòng trống.';
    send.disabled = !rooms.length;
  }).catch((e) => error.replaceChildren(formError(errText(e))));
  send.addEventListener('click', () => busy(send, async () => {
    if (!room.value || !start.checkValidity() || !occupants.checkValidity()) {
      error.replaceChildren(formError('Chọn phòng, ngày thuê và số người ở.'));
      return;
    }
    try {
      await api.post('/api/me/bookings', {
        roomId: Number(room.value), expectedStartDate: start.value, occupantsCount: occupants.valueAsNumber,
      });
      dialog.close();
      toast('Đã gửi yêu cầu. Chủ trọ sẽ xem xét và liên hệ.');
    } catch (e) { error.replaceChildren(formError(errText(e))); }
  }));
}

export function manageBookings(roomIds: Set<number>): void {
  const dialog = openDialog('Yêu cầu thuê phòng', { width: 900 });
  const body = h('div', null, 'Đang tải yêu cầu…');
  dialog.body.append(body);
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Đóng'));
  const reload = async () => {
    try {
      const all = await api.get<Booking[]>('/api/chi/bookings');
      body.replaceChildren(table<Booking>([
        { key: 'room', label: 'Phòng', render: (b) => b.roomCode },
        { key: 'tenant', label: 'Khách', render: (b) => h('div', null, b.tenantName, h('div', { class: 'muted' }, b.tenantPhone)) },
        { key: 'day', label: 'Ngày thuê', render: (b) => date(b.expectedStartDate) },
        { key: 'people', label: 'Số người', render: (b) => String(b.occupantsCount) },
        { key: 'deposit', label: 'Đã cọc (₫)', render: (b) => money(b.depositPaid) },
        { key: 'status', label: 'Trạng thái', render: (b) => h('div', null, statusBadge(b.status), b.rejectionReason ? h('p', null, b.rejectionReason) : null) },
        { key: 'action', label: '', render: (b) => bookingActions(b, reload) ?? "" },
      ], all.filter((b) => roomIds.has(b.roomId))));
    } catch (e) { body.replaceChildren(formError(errText(e))); }
  };
  void reload();
}

function bookingActions(booking: Booking, reload: () => Promise<void>): HTMLElement | null {
  if (booking.status !== 'PENDING' && booking.status !== 'APPROVED') return null;
  const path = '/api/chi/bookings/' + booking.bookingId;
  const approve = h('button', { class: 'btn secondary sm' }, 'Duyệt');
  approve.addEventListener('click', () => busy(approve, async () => {
    if (!(await confirmDialog('Duyệt yêu cầu thuê', 'Duyệt yêu cầu phòng ' + booking.roomCode + '?', 'Duyệt'))) return;
    try { await api.patch(path + '/approve'); await reload(); }
    catch (e) { toast(errText(e), 'err'); }
  }));
  return h('div', { class: 'row-actions' },
    booking.status === 'PENDING' ? approve : null,
    booking.status === 'PENDING' ? h('button', { class: 'btn secondary sm', onclick: () => editBooking(booking, 'reject', reload) }, 'Từ chối') : null,
    h('button', { class: 'btn secondary sm', onclick: () => editBooking(booking, 'deposit', reload) }, 'Ghi nhận cọc'));
}

function editBooking(booking: Booking, action: 'reject' | 'deposit', reload: () => Promise<void>): void {
  const rejecting = action === 'reject';
  const dialog = openDialog(rejecting ? 'Từ chối yêu cầu thuê' : 'Ghi nhận cọc giữ chỗ');
  const error = h('div', { role: 'alert' });
  const value = rejecting ? input({ maxlength: '255', required: true })
    : input({ type: 'number', min: '0', step: '1', required: true, value: String(booking.depositPaid) });
  const save = h('button', { class: 'btn' }, 'Lưu');
  dialog.body.append(error, field(rejecting ? 'Lý do từ chối' : 'Tổng tiền cọc đã nhận (₫)', value));
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Hủy'), save);
  save.addEventListener('click', () => busy(save, async () => {
    if (!value.value.trim() || !value.checkValidity()) {
      error.replaceChildren(formError('Nhập giá trị hợp lệ.'));
      return;
    }
    try {
      await api.patch('/api/chi/bookings/' + booking.bookingId + '/' + action,
        rejecting ? { rejectionReason: value.value.trim() } : { depositPaid: value.valueAsNumber });
      dialog.close(); await reload();
    } catch (e) { error.replaceChildren(formError(errText(e))); }
  }));
}
