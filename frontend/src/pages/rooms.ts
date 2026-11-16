import { h, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { money } from '../lib/format';
import { SAMPLE_ROOMS, SAMPLE_TENANTS, SampleRoom } from '../data/sample';
import { dl, openDialog, pageHead, plate, sampleNote, statusBadge } from '../ui/kit';

export function roomsPage(view: HTMLElement): void {
  let filter = '';
  const board = h('div', { class: 'stack' });
  const counts = (s: string) => SAMPLE_ROOMS.filter((r) => r.status === s).length;

  const paint = () => {
    board.replaceChildren();
    for (const floor of [...new Set(SAMPLE_ROOMS.map((r) => r.floor))]) {
      const rooms = SAMPLE_ROOMS.filter((r) => r.floor === floor && (!filter || r.status === filter));
      if (!rooms.length) continue;
      board.appendChild(h('section', { class: 'floor' }, h('h3', null, `Tầng ${floor}`),
        h('div', { class: 'room-grid' }, ...rooms.map(tile))));
    }
  };
  const tile = (r: SampleRoom) => {
    const t = SAMPLE_TENANTS.find((x) => x.tenantId === r.tenantId);
    const b = h('button', { class: `room-tile ${r.status === 'VACANT' ? 'vacant' : r.status === 'REPAIRING' ? 'repair' : ''}`, onclick: () => openRoom(r) },
      h('div', { class: 'top' }, plate(r.code, r.status === 'VACANT' ? 'ghost' : r.status === 'REPAIRING' ? 'off' : ''), statusBadge(r.status)),
      h('div', { class: 'who' }, t ? t.name : r.status === 'VACANT' ? 'Đang trống' : 'Đang sửa chữa'),
      h('div', { class: 'meta' }, h('span', null, `${r.area} m²`), h('span', { class: 'money' }, `${money(r.price)} ₫`)));
    return b;
  };
  const seg = h('div', { class: 'seg', role: 'group', 'aria-label': 'Lọc theo trạng thái' });
  for (const [v, l] of [['', `Tất cả ${SAMPLE_ROOMS.length}`], ['RENTED', `Đang thuê ${counts('RENTED')}`], ['VACANT', `Trống ${counts('VACANT')}`], ['REPAIRING', `Đang sửa ${counts('REPAIRING')}`]]) {
    seg.appendChild(h('button', { type: 'button', 'aria-pressed': String(v === filter), onclick: (e: Event) => {
      filter = v;
      seg.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', 'false'));
      (e.currentTarget as HTMLElement).setAttribute('aria-pressed', 'true');
      paint();
    } }, l));
  }
  paint();
  view.replaceChildren(
    pageHead('Phòng', 'Sơ đồ phòng theo tầng.', [sampleNote(), h('button', { class: 'btn', disabled: true, title: 'Chờ API của Chi' }, icon('plus', 16), 'Thêm phòng')]),
    h('div', { class: 'stack' }, h('div', null, seg), board),
  );
}

function openRoom(r: SampleRoom): void {
  const t = SAMPLE_TENANTS.find((x) => x.tenantId === r.tenantId);
  const d = openDialog(`Phòng ${r.code}`, { drawer: true });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center' }, plate(r.code, 'lg'), statusBadge(r.status), sampleNote()),
    dl([['Tầng', r.floor], ['Diện tích', `${r.area} m²`], ['Giá thuê', `${money(r.price)} ₫/tháng`], ['Tiền cọc', `${money(r.deposit)} ₫`], ['Số người tối đa', r.maxPeople], ['Tiện nghi', r.amenities.join(', ')], ['Người thuê', t?.name ?? 'Chưa có']]),
  );
  mount(d.foot, h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'),
    r.status === 'VACANT' ? h('a', { class: 'btn', href: `#/contracts?new=${r.roomId}`, onclick: d.close }, icon('file', 16), 'Lập hợp đồng') : null);
}
