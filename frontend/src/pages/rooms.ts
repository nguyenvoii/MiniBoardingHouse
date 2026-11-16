import { h, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { date, money } from '../lib/format';
import { loadLive, LiveRoom } from '../data/live';
import { residentsBox } from '../ui/occupancy';
import { dl, openDialog, pageHead, plate, sampleNote, statusBadge } from '../ui/kit';

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
    h('button', { class: `room-tile ${r.status === 'VACANT' ? 'vacant' : r.status === 'REPAIRING' ? 'repair' : ''}`, onclick: () => openRoom(r) },
      h('div', { class: 'top' }, plate(r.code, r.status === 'VACANT' ? 'ghost' : r.status === 'REPAIRING' ? 'off' : ''), statusBadge(r.status)),
      h('div', { class: 'who' }, r.tenantName ?? (r.status === 'VACANT' ? 'Đang trống' : 'Đang sửa chữa')),
      h('div', { class: 'meta' }, h('span', null, `${r.area} m²`), h('span', { class: 'money' }, `${money(r.price)} ₫`)));
  const seg = h('div', { class: 'seg', role: 'group', 'aria-label': 'Lọc theo trạng thái' });
  for (const [v, l] of [['', `Tất cả ${rooms.length}`], ['RENTED', `Đang thuê ${counts('RENTED')}`], ['VACANT', `Trống ${counts('VACANT')}`], ['REPAIRING', `Đang sửa ${counts('REPAIRING')}`]]) {
    seg.appendChild(h('button', { type: 'button', 'aria-pressed': String(v === filter), onclick: (e: Event) => {
      filter = v;
      seg.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', 'false'));
      (e.currentTarget as HTMLElement).setAttribute('aria-pressed', 'true');
      paint();
    } }, l));
  }
  paint();
  view.replaceChildren(
    pageHead('Phòng', 'Sơ đồ phòng theo tầng. Phòng có hợp đồng còn hiệu lực là «Đang thuê».', [sampleNote('Danh sách phòng là dữ liệu mẫu, trạng thái lấy từ hợp đồng thật'), h('button', { class: 'btn', disabled: true, title: 'Chưa có chức năng thêm phòng' }, icon('plus', 16), 'Thêm phòng')]),
    h('div', { class: 'stack' }, h('div', null, seg), board),
  );
}

function openRoom(r: LiveRoom): void {
  const c = r.contract;
  const d = openDialog(`Phòng ${r.code}`, { drawer: true });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(r.code, 'lg'), statusBadge(r.status)),
    dl([['Tầng', r.floor], ['Diện tích', `${r.area} m²`], ['Giá thuê niêm yết', `${money(r.price)} ₫/tháng`], ['Tiền cọc', `${money(r.deposit)} ₫`], ['Số người tối đa', r.maxPeople], ['Tiện nghi', r.amenities.join(', ')],
      ['Người thuê', c ? c.tenantName : 'Chưa có'], ['Hợp đồng', c ? `${c.contractCode} (${date(c.startDate)} – ${date(c.endDate)})` : '–']]),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Người ở cùng (tính tiền dịch vụ theo đầu người)'), residentsBox(r.roomId, r.maxPeople)),
  );
  mount(d.foot, h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'),
    c ? h('a', { class: 'btn', href: `#/contracts?open=${c.contractId}`, onclick: d.close }, icon('file', 16), 'Mở hợp đồng') : null,
    r.status === 'VACANT' ? h('a', { class: 'btn', href: `#/contracts?new=${r.roomId}`, onclick: d.close }, icon('file', 16), 'Lập hợp đồng') : null);
}
