import { h } from '../lib/dom';
import { icon } from '../lib/icons';
import { date } from '../lib/format';
import { SAMPLE_TENANTS, SampleTenant } from '../data/sample';
import { dl, input, openDialog, pageHead, panel, plate, sampleNote, table } from '../ui/kit';

export function tenantsPage(view: HTMLElement): void {
  const holder = h('div');
  const q = input({ type: 'search', placeholder: 'Tên, số điện thoại hoặc phòng', 'aria-label': 'Tìm khách thuê' });
  const paint = () => {
    const k = q.value.trim().toLowerCase();
    const rows = SAMPLE_TENANTS.filter((t) => !k || `${t.name} ${t.phone} ${t.roomCode ?? ''}`.toLowerCase().includes(k));
    holder.replaceChildren(table<SampleTenant>([
      { key: 'n', label: 'Họ tên', render: (t) => h('span', { class: 'cell-main' }, t.name), sort: (a, b) => a.name.localeCompare(b.name, 'vi') },
      { key: 'r', label: 'Phòng', render: (t) => (t.roomCode ? plate(t.roomCode) : '–') },
      { key: 'p', label: 'Điện thoại', render: (t) => t.phone },
      { key: 'j', label: 'Nghề nghiệp', render: (t) => t.job },
      { key: 's', label: 'Ở từ', render: (t) => date(t.since), sort: (a, b) => (a.since ?? '').localeCompare(b.since ?? '') },
    ], rows, { onRow: open }));
  };
  q.addEventListener('input', paint);
  paint();
  view.replaceChildren(
    pageHead('Khách thuê', 'Hồ sơ người thuê và phòng đang ở.', [sampleNote(), h('button', { class: 'btn', disabled: true, title: 'Chờ API của Chi' }, icon('plus', 16), 'Thêm khách thuê')]),
    panel(null, [h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, q)), holder], { flush: true }),
  );
}

function open(t: SampleTenant): void {
  const d = openDialog(t.name, { drawer: true });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center' }, t.roomCode ? plate(t.roomCode, 'lg') : null, sampleNote()),
    dl([['Điện thoại', t.phone], ['Email', t.email ?? '–'], ['CCCD', t.idNumber], ['Nghề nghiệp', t.job], ['Ở từ', date(t.since)], ['Xe', t.vehicles.length ? t.vehicles.join(', ') : 'Không có']]),
  );
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'));
}
