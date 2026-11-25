import { h, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { date, money } from '../lib/format';
import { loadLive, LiveRoom } from '../data/live';
import { residentsBox } from '../ui/occupancy';
import { busy } from '../lib/dom';
import { api, errText, PROPERTY_ID } from '../lib/api';
import { loadProperties } from '../data/live';
import { confirmDialog, dl, field, formError, input, openDialog, pageHead, plate, select, statusBadge, toast } from '../ui/kit';

export async function roomsPage(view: HTMLElement): Promise<void> {
  const { rooms } = await loadLive();
  let filter = '';
  const codeQuery = input({ type: 'search', placeholder: 'Nhập mã phòng' });
  const floorFilter = select([['', 'Tất cả tầng'], ...[...new Set(rooms.map((r) => String(r.floor)))].map((floor): [string, string] => [floor, `Tầng ${floor}`])]);
  const areaMin = input({ type: 'number', min: '0' });
  const areaMax = input({ type: 'number', min: '0' });
  const rentMin = input({ type: 'number', min: '0' });
  const rentMax = input({ type: 'number', min: '0' });
  const board = h('div', { class: 'stack' });
  const counts = (s: string) => rooms.filter((r) => r.status === s).length;

  const paint = () => {
    board.replaceChildren();
    for (const floor of [...new Set(rooms.map((r) => r.floor))]) {
      if (floorFilter.value && String(floor) !== floorFilter.value) continue;
      const query = codeQuery.value.trim().toLocaleLowerCase('vi');
      const list = rooms.filter((r) => r.floor === floor
        && (!filter || r.status === filter)
        && r.code.toLocaleLowerCase('vi').includes(query)
        && (!areaMin.value || r.area >= Number(areaMin.value))
        && (!areaMax.value || r.area <= Number(areaMax.value))
        && (!rentMin.value || r.price >= Number(rentMin.value))
        && (!rentMax.value || r.price <= Number(rentMax.value)));
      if (!list.length) continue;
      board.appendChild(h('section', { class: 'floor' }, h('h2', null, `Tầng ${floor}`), h('div', { class: 'room-grid' }, ...list.map(tile))));
    }
    if (!board.childElementCount) board.append(h('p', null, 'Không có phòng phù hợp với bộ lọc.'));
  };
  const tile = (r: LiveRoom) =>
    h('button', { class: `room-tile ${r.status === 'VACANT' ? 'vacant' : r.status === 'RENTED' ? '' : 'repair'}`, onclick: () => openRoom(r, () => void roomsPage(view)) },
      h('div', { class: 'top' }, plate(r.code, r.status === 'VACANT' ? 'ghost' : r.status === 'RENTED' ? '' : 'off'), statusBadge(r.status)),
      h('div', { class: 'who' }, r.tenantName ?? (r.status === 'VACANT' ? 'Đang trống' : r.status === 'REPAIRING' ? 'Đang sửa chữa' : 'Tạm ngưng cho thuê')),
      h('div', { class: 'meta' }, h('span', null, `${r.area} m²`), h('span', { class: 'money' }, `${money(r.price)} ₫`)));
  const seg = h('div', { class: 'seg', role: 'group', 'aria-label': 'Lọc theo trạng thái' });
  for (const [v, l] of [['', `Tất cả ${rooms.length}`], ['RENTED', `Đang thuê ${counts('RENTED')}`], ['VACANT', `Trống ${counts('VACANT')}`], ['REPAIRING', `Đang sửa ${counts('REPAIRING')}`], ['SUSPENDED', `Tạm ngưng ${counts('SUSPENDED')}`]]) {
    seg.appendChild(h('button', { type: 'button', 'aria-pressed': String(v === filter), onclick: (e: Event) => {
      filter = v;
      seg.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', 'false'));
      (e.currentTarget as HTMLElement).setAttribute('aria-pressed', 'true');
      paint();
    } }, l));
  }
  for (const control of [codeQuery, floorFilter, areaMin, areaMax, rentMin, rentMax]) {
    control.addEventListener('input', paint);
  }
  paint();
  view.replaceChildren(
    pageHead('Phòng', 'Sơ đồ phòng theo tầng. Phòng có hợp đồng còn hiệu lực là «Đang thuê».', [
      h('button', { class: 'btn secondary', onclick: () => api.download(`/api/chi/exports/rooms-tenants?propertyId=${PROPERTY_ID}${filter ? `&status=${filter}` : ''}`, 'phong-va-khach-thue.xlsx').catch((e) => toast(errText(e), 'err')) }, icon('download', 16), 'Xuất Excel'),
      h('button', { class: 'btn', onclick: () => void addRoom(() => void roomsPage(view)) }, icon('plus', 16), 'Thêm phòng'),
    ]),
    h('div', { class: 'stack' },
      h('div', { class: 'form-grid' }, field('Mã phòng', codeQuery), field('Tầng', floorFilter),
        field('Diện tích từ (m²)', areaMin), field('Diện tích đến (m²)', areaMax),
        field('Giá thuê từ (₫)', rentMin), field('Giá thuê đến (₫)', rentMax)),
      h('div', null, seg), board),
  );
}

function openRoom(r: LiveRoom, done: () => void): void {
  const c = r.contract;
  const d = openDialog(`Phòng ${r.code}`, { drawer: true });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(r.code, 'lg'), statusBadge(r.status)),
    dl([['Tầng', r.floor], ['Diện tích', `${r.area} m²`], ['Giá thuê niêm yết', `${money(r.price)} ₫/tháng`], ['Tiền cọc', `${money(r.deposit)} ₫`], ['Số người tối đa', r.maxPeople], ['Tiện nghi', r.amenities.join(', ')],
      ['Người thuê', c ? c.tenantName : 'Chưa có'], ['Hợp đồng', c ? `${c.contractCode} (${date(c.startDate)} – ${date(c.endDate)})` : '–']]),
    roomPhotos(r),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Người ở cùng (tính tiền dịch vụ theo đầu người)'), residentsBox(r.roomId, r.maxPeople)),
  );
  mount(d.foot, h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'),
    h('button', { class: 'btn secondary', onclick: () => { d.close(); editRoom(r, done); } }, icon('edit', 16), 'Sửa phòng'),
    !c && r.status !== 'RENTED' ? h('button', { class: 'btn secondary', onclick: () => { d.close(); changeRoomStatus(r, done); } }, 'Đổi trạng thái') : null,
    c ? h('a', { class: 'btn', href: `#/contracts?open=${c.contractId}`, onclick: d.close }, icon('file', 16), 'Mở hợp đồng') : null,
    r.status === 'VACANT' ? h('a', { class: 'btn', href: `#/contracts?new=${r.roomId}`, onclick: d.close }, icon('file', 16), 'Lập hợp đồng') : null);
}

interface RoomPhoto { mediaId: number; mediaUrl: string; isCover: boolean }

function roomPhotos(room: LiveRoom): HTMLElement {
  const list = h('div', { class: 'stack' }, 'Đang tải ảnh…');
  const error = h('div', { role: 'alert' });
  const files = input({ type: 'file', accept: 'image/png,image/jpeg,image/webp', multiple: true });
  const upload = h('button', { class: 'btn secondary' }, 'Tải ảnh phòng');
  const load = async () => {
    try {
      const photos = await api.get<RoomPhoto[]>('/api/chi/media/room/' + room.roomId);
      if (!photos.length) {
        list.replaceChildren(h('p', { class: 'muted' }, 'Phòng chưa có ảnh.'));
        return;
      }
      list.replaceChildren(...photos.map((photo, index) => {
        const path = '/api/chi/media/' + photo.mediaId;
        const cover = h('button', { class: 'btn secondary sm' }, 'Đặt làm ảnh bìa');
        const remove = h('button', { class: 'btn secondary sm' }, 'Xóa ảnh');
        cover.addEventListener('click', () => busy(cover, async () => {
          try { await api.patch(path + '/cover'); await load(); }
          catch (e) { toast(errText(e), 'err'); }
        }));
        remove.addEventListener('click', () => busy(remove, async () => {
          if (!(await confirmDialog('Xóa ảnh phòng', 'Xóa ảnh này khỏi phòng ' + room.code + '?', 'Xóa ảnh', true))) return;
          try { await api.del(path); await load(); }
          catch (e) { toast(errText(e), 'err'); }
        }));
        return h('div', { class: 'list-row' },
          h('img', { src: photo.mediaUrl, alt: 'Phòng ' + room.code + ', ảnh ' + (index + 1), width: 112, height: 76, style: 'object-fit:cover' }),
          h('div', { class: 'row-actions' }, photo.isCover ? h('span', { class: 'st ok' }, 'Ảnh bìa') : cover, remove));
      }));
    } catch (e) { list.replaceChildren(formError(errText(e))); }
  };
  upload.addEventListener('click', () => busy(upload, async () => {
    error.replaceChildren();
    const pending = Array.from(files.files ?? []);
    if (!pending.length) {
      error.replaceChildren(formError('Chọn ít nhất một ảnh.'));
      return;
    }
    files.disabled = true;
    try {
      while (pending.length) {
        await api.upload('/api/chi/media/upload?roomId=' + room.roomId, pending[0]);
        pending.shift();
      }
      toast('Đã tải ảnh phòng');
    } catch (e) {
      error.replaceChildren(formError('Chưa tải được ' + pending[0].name + '. ' + errText(e)));
    } finally {
      const remaining = new DataTransfer();
      pending.forEach((file) => remaining.items.add(file));
      files.files = remaining.files;
      files.disabled = false;
      await load();
    }
  }));
  void load();
  return h('section', { class: 'stack' }, h('h3', null, 'Ảnh phòng'), list, error,
    field('Chọn ảnh phòng', files, 'PNG, JPG hoặc WebP. Tối đa 5 MB mỗi ảnh.'), h('div', null, upload));
}

function changeRoomStatus(room: LiveRoom, done: () => void): void {
  const d = openDialog(`Đổi trạng thái phòng ${room.code}`, { width: 440 });
  const status = select([['VACANT', 'Trống'], ['REPAIRING', 'Đang sửa chữa'], ['SUSPENDED', 'Tạm ngưng']], room.status);
  const error = h('div', { role: 'alert' });
  const save = h('button', { class: 'btn' }, 'Lưu trạng thái');
  save.addEventListener('click', () => busy(save, async () => {
    try {
      await api.patch(`/api/chi/rooms/${room.roomId}/status?status=${status.value}`);
      d.close();
      toast('Đã lưu trạng thái phòng');
      done();
    } catch (e) { error.replaceChildren(formError(errText(e))); }
  }));
  d.body.append(error, field('Trạng thái', status));
  mount(d.foot, h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), save);
}

function editRoom(r: LiveRoom, done: () => void): void {
  const d = openDialog(`Sửa phòng ${r.code}`, { width: 520 });
  const err = h('div');
  const code = input({ value: r.code, maxlength: '50' });
  const area = input({ type: 'number', min: '1', step: '0.5', value: String(r.area), class: 'input num-in' });
  const people = input({ type: 'number', min: '1', value: String(r.maxPeople), class: 'input num-in' });
  const rent = input({ type: 'number', min: '0', step: '10000', value: String(r.price), class: 'input num-in' });
  const dep = input({ type: 'number', min: '0', step: '10000', value: String(r.deposit), class: 'input num-in' });
  d.body.append(err, ...(r.contract ? [h('p', { class: 'muted' }, 'Phòng đang có hợp đồng: giá và cọc sửa ở đây chỉ áp cho hợp đồng mới, hợp đồng hiện tại giữ nguyên giá đã ký.')] : []),
    h('div', { class: 'form-grid' }, field('Mã phòng', code), field('Diện tích (m²)', area), field('Số người tối đa', people),
      field('Giá thuê niêm yết (₫/tháng)', rent), field('Tiền cọc (₫)', dep)));
  const ok = h('button', { class: 'btn' }, 'Lưu phòng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!code.value.trim()) { err.replaceChildren(formError('Nhập mã phòng.')); return; }
    try {
      await api.put(`/api/chi/rooms/${r.roomId}`, { roomCode: code.value.trim(), areaSqm: Number(area.value), maxOccupants: Number(people.value), baseRentPrice: Number(rent.value), depositAmount: Number(dep.value) });
      d.close(); toast('Đã lưu phòng'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function addRoom(done: () => void): Promise<void> {
  const props = await loadProperties().catch(() => []);
  const prop = props.find((p) => p.propertyId === PROPERTY_ID);
  const d = openDialog('Thêm phòng', { width: 560 });
  if (!prop || !prop.floors.length) {
    d.body.append(h('p', { class: 'muted' }, 'Khu trọ này chưa có tầng nào nên chưa thêm phòng được.'));
    d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'));
    return;
  }
  const err = h('div');
  const floor = select(prop.floors.map((f) => [String(f.floorId), f.floorName] as [string, string]));
  const code = input({ placeholder: 'Ví dụ P105', maxlength: '50' });
  const area = input({ type: 'number', min: '1', step: '0.5', value: '20', class: 'input num-in' });
  const people = input({ type: 'number', min: '1', value: '2', class: 'input num-in' });
  const rent = input({ type: 'number', min: '0', step: '10000', value: '3000000', class: 'input num-in' });
  const dep = input({ type: 'number', min: '0', step: '10000', value: '3000000', class: 'input num-in' });
  d.body.append(err, h('div', { class: 'form-grid' }, field('Tầng', floor), field('Mã phòng', code),
    field('Diện tích (m²)', area), field('Số người tối đa', people),
    field('Giá thuê niêm yết (₫/tháng)', rent), field('Tiền cọc (₫)', dep)));
  const ok = h('button', { class: 'btn' }, 'Thêm phòng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!code.value.trim()) { err.replaceChildren(formError('Nhập mã phòng.')); return; }
    try {
      await api.post('/api/chi/rooms', {
        floorId: Number(floor.value), roomCode: code.value.trim(), areaSqm: Number(area.value), maxOccupants: Number(people.value),
        baseRentPrice: Number(rent.value), depositAmount: Number(dep.value), status: 'VACANT',
      });
      d.close();
      toast(`Đã thêm phòng ${code.value.trim()}`);
      done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
