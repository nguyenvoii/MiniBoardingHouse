import { busy, h } from '../lib/dom';
import { api, errText, PROPERTY_ID } from '../lib/api';
import { date } from '../lib/format';
import { field, formError, input, openDialog, select, table } from '../ui/kit';

interface Point {
  periodId: number;
  periodName: string;
  startDate: string;
  usedElectric: number;
  usedWater: number;
}

export function meterConsumption(): void {
  const dialog = openDialog('Lịch sử tiêu thụ điện nước', { width: 880 });
  const room = select([['', 'Tổng toàn khu']]);
  const from = input({ type: 'date' });
  const to = input({ type: 'date' });
  const result = h('div', { role: 'region', 'aria-label': 'Kết quả tiêu thụ' });
  const error = h('div', { role: 'alert' });
  const apply = h('button', { class: 'btn secondary' }, 'Xem tiêu thụ');
  dialog.body.append(error, h('div', { class: 'form-grid' },
    field('Phạm vi', room), field('Từ ngày', from), field('Đến ngày', to)), apply, result);
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Đóng'));
  const load = async () => {
    error.replaceChildren();
    if (from.value && to.value && from.value > to.value) {
      error.replaceChildren(formError('Ngày kết thúc phải từ ngày bắt đầu trở đi.'));
      return;
    }
    const query = new URLSearchParams();
    if (from.value) query.set('from', from.value);
    if (to.value) query.set('to', to.value);
    const suffix = query.size ? '?' + query.toString() : '';
    const roomId = room.value;
    result.textContent = 'Đang tải tiêu thụ…';
    try {
      const points = roomId
        ? (await api.get<{ points: Point[] }>('/api/rooms/' + roomId + '/consumption' + suffix)).points
        : await api.get<Point[]>('/api/properties/' + PROPERTY_ID + '/consumption-comparison' + suffix);
      result.replaceChildren(points.length ? consumptionTable(points) : h('p', null, 'Chưa có chỉ số trong khoảng ngày này.'));
    } catch (e) { result.replaceChildren(formError(errText(e))); }
  };
  apply.addEventListener('click', () => busy(apply, load));
  void api.get<Array<{ roomId: number; roomCode: string }>>('/api/chi/rooms?propertyId=' + PROPERTY_ID)
    .then((rooms) => room.append(...rooms.map((r) => h('option', { value: String(r.roomId) }, r.roomCode))))
    .catch((e) => error.replaceChildren(formError(errText(e))));
  void busy(apply, load);
}

function consumptionTable(inputPoints: Point[]): HTMLElement {
  const points = [...inputPoints].sort((a, b) => a.startDate.localeCompare(b.startDate));
  const electricMax = Math.max(1, ...points.map((p) => p.usedElectric));
  const waterMax = Math.max(1, ...points.map((p) => p.usedWater));
  const bar = (value: number, max: number, unit: string) => h('div', null,
    h('meter', { min: 0, max, value, 'aria-label': value + ' ' + unit }),
    h('span', null, ' ' + value.toLocaleString('vi-VN')));
  return table<Point>([
    { key: 'period', label: 'Kỳ ghi số', render: (p) => p.periodName },
    { key: 'date', label: 'Từ ngày', render: (p) => date(p.startDate) },
    { key: 'electric', label: 'Điện (kWh)', render: (p) => bar(p.usedElectric, electricMax, 'kWh') },
    { key: 'water', label: 'Nước (m³)', render: (p) => bar(p.usedWater, waterMax, 'm³') },
  ], points);
}
