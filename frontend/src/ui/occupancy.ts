import { h, busy, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, can } from '../lib/api';
import { date, isoDay } from '../lib/format';
import { confirmDialog, field, formError, input, openDialog, select, toast } from './kit';

export interface Vehicle { vehicleId: number; tenantId: number; licensePlate: string; vehicleType: string; brand: string | null; color: string | null }
export interface Resident { residentId: number; roomId: number; fullName: string; phoneNumber: string | null; identityNumber: string | null; relationship: string | null; startDate: string;
  dateOfBirth: string | null; permanentAddress: string | null; tempResidenceRegisteredOn: string | null }

const VEHICLE_TYPES: Array<[string, string]> = [['MOTORBIKE', 'Xe máy'], ['ELECTRIC_BIKE', 'Xe đạp điện'], ['BICYCLE', 'Xe đạp'], ['CAR', 'Ô tô'], ['OTHER', 'Khác']];
const typeLabel = (t: string) => VEHICLE_TYPES.find((x) => x[0] === t)?.[1] ?? t;

/** Khối "Xe đăng ký" của một khách: số xe này là số lượng thật để tính dịch vụ gửi xe. */
export function vehiclesBox(tenantId: number): HTMLElement {
  const box = h('div', { class: 'stack', style: 'gap:10px' });
  const canEdit = can('LANDLORD', 'MANAGER');
  const load = async () => {
    try {
      const list = await api.get<Vehicle[]>(`/api/tenants/${tenantId}/vehicles`);
      mount(box,
        list.length
          ? h('ul', null, ...list.map((v) => h('li', { class: 'list-row' },
              h('span', { class: 'plate ghost' }, v.licensePlate),
              h('div', { class: 'grow' }, h('b', null, typeLabel(v.vehicleType)), h('span', null, [v.brand, v.color].filter(Boolean).join(' · ') || ' ')),
              canEdit ? h('div', { class: 'row-actions' },
                h('button', { class: 'btn secondary sm', onclick: () => vehicleDialog(tenantId, v, load) }, icon('edit', 14), 'Sửa'),
                h('button', { class: 'btn secondary sm', onclick: async () => {
                  if (!(await confirmDialog('Ngừng dùng xe', `Bỏ xe ${v.licensePlate}? Tiền gửi xe các kỳ sau sẽ không tính xe này.`, 'Bỏ xe', true))) return;
                  try { await api.del(`/api/vehicles/${v.vehicleId}`); toast('Đã bỏ xe'); void load(); } catch (e) { toast(errText(e), 'err'); }
                } }, 'Bỏ')) : null)))
          : h('p', { class: 'muted' }, 'Chưa đăng ký xe nào. Dịch vụ gửi xe chỉ tính cho xe đã đăng ký ở đây.'),
        h('p', { class: 'muted' }, `Số xe đang tính tiền gửi xe: ${list.length}`),
        canEdit ? h('button', { class: 'btn secondary sm', style: 'align-self:flex-start', onclick: () => vehicleDialog(tenantId, null, load) }, icon('plus', 14), 'Thêm xe') : null,
      );
    } catch {
      box.replaceChildren(h('p', { class: 'muted' }, 'Chưa tải được danh sách xe (hồ sơ khách chưa có trong cơ sở dữ liệu).'));
    }
  };
  void load();
  return box;
}

function vehicleDialog(tenantId: number, v: Vehicle | null, done: () => void): void {
  const d = openDialog(v ? 'Sửa xe' : 'Đăng ký xe');
  const err = h('div');
  const plate = input({ value: v?.licensePlate ?? '', placeholder: 'Ví dụ: 59A1-123.45' });
  const type = select(VEHICLE_TYPES, v?.vehicleType ?? 'MOTORBIKE');
  const brand = input({ value: v?.brand ?? '', placeholder: 'Hãng xe (không bắt buộc)' });
  const color = input({ value: v?.color ?? '', placeholder: 'Màu xe (không bắt buộc)' });
  d.body.append(err, field('Biển số', plate), field('Loại xe', type), h('div', { class: 'form-grid' }, field('Hãng', brand), field('Màu', color)));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const body = { licensePlate: plate.value, vehicleType: type.value, brand: brand.value, color: color.value };
    try { await (v ? api.put(`/api/vehicles/${v.vehicleId}`, body) : api.post(`/api/tenants/${tenantId}/vehicles`, body)); d.close(); toast('Đã lưu xe'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

/** Khối "Người ở cùng" của một phòng: tiền dịch vụ theo đầu người = 1 (người đứng tên) + số người ở cùng ở đây. */
export function residentsBox(roomId: number, maxOccupants: number): HTMLElement {
  const box = h('div', { class: 'stack', style: 'gap:10px' });
  const canEdit = can('LANDLORD', 'MANAGER');
  const load = async () => {
    try {
      const list = await api.get<Resident[]>(`/api/rooms/${roomId}/residents`);
      mount(box,
        list.length
          ? h('ul', null, ...list.map((r) => h('li', { class: 'list-row' },
              h('div', { class: 'grow' }, h('b', null, r.fullName), h('span', null, [r.relationship, r.phoneNumber, `ở từ ${date(r.startDate)}`, r.tempResidenceRegisteredOn ? `tạm trú ${date(r.tempResidenceRegisteredOn)}` : 'chưa khai tạm trú'].filter(Boolean).join(' · '))),
              canEdit ? h('div', { class: 'row-actions' },
                h('button', { class: 'btn secondary sm', onclick: () => residentDialog(roomId, r, load) }, icon('edit', 14), 'Sửa'),
                h('button', { class: 'btn secondary sm', onclick: async () => {
                  if (!(await confirmDialog('Bỏ người ở cùng', `${r.fullName} rời phòng? Tiền dịch vụ theo đầu người các kỳ sau sẽ giảm.`, 'Xác nhận', true))) return;
                  try { await api.del(`/api/residents/${r.residentId}`); toast('Đã cập nhật'); void load(); } catch (e) { toast(errText(e), 'err'); }
                } }, 'Rời phòng')) : null)))
          : h('p', { class: 'muted' }, 'Chưa khai người ở cùng. Phòng đang được tính 1 người (người đứng tên hợp đồng).'),
        h('p', { class: 'muted' }, `Số người tính tiền dịch vụ theo đầu người: ${1 + list.length} (tối đa ${maxOccupants})`),
        canEdit ? h('button', { class: 'btn secondary sm', style: 'align-self:flex-start', onclick: () => residentDialog(roomId, null, load) }, icon('plus', 14), 'Thêm người ở cùng') : null,
      );
    } catch {
      box.replaceChildren(h('p', { class: 'muted' }, 'Chưa tải được danh sách người ở (phòng chưa có trong cơ sở dữ liệu).'));
    }
  };
  void load();
  return box;
}

function residentDialog(roomId: number, r: Resident | null, done: () => void): void {
  const d = openDialog(r ? 'Sửa người ở cùng' : 'Thêm người ở cùng');
  const err = h('div');
  const name = input({ value: r?.fullName ?? '', placeholder: 'Họ và tên' });
  const rel = input({ value: r?.relationship ?? '', placeholder: 'Quan hệ: bạn bè, vợ/chồng, con...' });
  const phone = input({ type: 'tel', value: r?.phoneNumber ?? '' });
  const idn = input({ value: r?.identityNumber ?? '', placeholder: 'CCCD (không bắt buộc)' });
  const since = input({ type: 'date', value: r?.startDate ?? isoDay(), 'aria-label': 'Ngày vào ở' });
  const dob = input({ type: 'date', value: r?.dateOfBirth ?? '', 'aria-label': 'Ngày sinh' });
  const addr = input({ value: r?.permanentAddress ?? '', maxlength: '255', placeholder: 'Địa chỉ thường trú (không bắt buộc)', 'aria-label': 'Địa chỉ thường trú' });
  const temp = input({ type: 'date', value: r?.tempResidenceRegisteredOn ?? '', 'aria-label': 'Ngày đăng ký tạm trú' });
  d.body.append(err, field('Họ tên', name), field('Quan hệ với người đứng tên', rel), h('div', { class: 'form-grid' }, field('Số điện thoại', phone), field('CCCD', idn),
    field('Ngày sinh', dob), field('Ngày vào ở', since), field('Địa chỉ thường trú', addr), field('Ngày đăng ký tạm trú', temp, 'Để trống nếu chưa khai báo với công an khu vực')));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const body = { fullName: name.value, relationship: rel.value, phoneNumber: phone.value, identityNumber: idn.value, startDate: since.value || null,
      dateOfBirth: dob.value || null, permanentAddress: addr.value, tempResidenceRegisteredOn: temp.value || null };
    try { await (r ? api.put(`/api/residents/${r.residentId}`, body) : api.post(`/api/rooms/${roomId}/residents`, body)); d.close(); toast('Đã lưu'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
