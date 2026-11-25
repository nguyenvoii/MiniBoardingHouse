import { h, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { date, money } from '../lib/format';
import { loadLive, LiveRoom } from '../data/live';
import { residentsBox } from '../ui/occupancy';
import { busy } from '../lib/dom';
import { api, errText, PROPERTY_ID } from '../lib/api';
import { loadProperties } from '../data/live';
import { dl, field, formError, input, openDialog, pageHead, plate, select, statusBadge, toast } from '../ui/kit';

export async function roomsPage(view: HTMLElement): Promise<void> {
  const { rooms } = await loadLive();
  let filter = '';
  const board = h('div', { class: 'stack' });
  const counts = (s: string) => rooms.filter((r) => r.status === s).length;

  const paint = () => {
    board.replaceChildren();
    for (const floor of [...new Set(rooms.map((r) => r.floor))]) {
      const list = rooms.filter((r) => r.floor === floor && (!filter || r.status === filter));
      if (!list.length) continue;
      board.appendChild(h('section', { class: 'floor' }, h('h2', null, `Tầng ${floor}`), h('div', { class: 'room-grid' }, ...list.map(tile))));
    }
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
  paint();
  view.replaceChildren(
    pageHead('Phòng', 'Sơ đồ phòng theo tầng. Phòng có hợp đồng còn hiệu lực là «Đang thuê».', [
      h('button', { class: 'btn secondary', onclick: () => api.download(`/api/chi/exports/rooms-tenants?propertyId=${PROPERTY_ID}${filter ? `&status=${filter}` : ''}`, 'phong-va-khach-thue.xlsx').catch((e) => toast(errText(e), 'err')) }, icon('download', 16), 'Xuất Excel'),
      h('button', { class: 'btn', onclick: () => void addRoom(() => void roomsPage(view)) }, icon('plus', 16), 'Thêm phòng'),
    ]),
    h('div', { class: 'stack' }, h('div', null, seg), board),
  );
}

function openRoom(r: LiveRoom, done: () => void): void {
  const c = r.contract;
  const d = openDialog(`Phòng ${r.code}`, { drawer: true });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(r.code, 'lg'), statusBadge(r.status)),
    dl([['Tầng', r.floor], ['Diện tích', `${r.area} m²`], ['Giá thuê niêm yết', `${money(r.price)} ₫/tháng`], ['Tiền cọc', `${money(r.deposit)} ₫`], ['Số người tối đa', r.maxPeople], ['Tiện nghi', r.amenities.join(', ')],
      ['Người thuê', c ? c.tenantName : 'Chưa có'], ['Hợp đồng', c ? `${c.contractCode} (${date(c.startDate)} – ${date(c.endDate)})` : '–']]),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Người ở cùng (tính tiền dịch vụ theo đầu người)'), residentsBox(r.roomId, r.maxPeople)),
  );
  mount(d.foot, h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'),
    h('button', { class: 'btn secondary', onclick: () => { d.close(); editRoom(r, done); } }, icon('edit', 16), 'Sửa phòng'),
    c ? h('a', { class: 'btn', href: `#/contracts?open=${c.contractId}`, onclick: d.close }, icon('file', 16), 'Mở hợp đồng') : null,
    r.status === 'VACANT' ? h('a', { class: 'btn', href: `#/contracts?new=${r.roomId}`, onclick: d.close }, icon('file', 16), 'Lập hợp đồng') : null);
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
