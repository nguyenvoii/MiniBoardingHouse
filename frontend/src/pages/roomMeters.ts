import { busy, h } from '../lib/dom';
import { api, errText } from '../lib/api';
import { date, isoDay } from '../lib/format';
import { field, formError, input, openDialog, select, table, toast } from '../ui/kit';

interface MeterChange {
  meterKind: 'ELECTRIC' | 'WATER';
  installedOn: string;
  serialNumber: string;
  previousReading: number | null;
  installedReading: number;
}

export function meterChanges(roomId: number): void {
  const path = '/api/chi/rooms/' + roomId + '/meter-changes';
  const dialog = openDialog('Lịch sử công tơ và đồng hồ nước', { width: 760 });
  const list = h('div', null, 'Đang tải lịch sử…');
  const reload = async () => {
    try {
      const rows = await api.get<MeterChange[]>(path);
      list.replaceChildren(rows.length ? table<MeterChange>([
        { key: 'kind', label: 'Thiết bị', render: (row) => row.meterKind === 'ELECTRIC' ? 'Công tơ điện' : 'Đồng hồ nước' },
        { key: 'date', label: 'Ngày lắp', render: (row) => date(row.installedOn) },
        { key: 'serial', label: 'Số sê-ri', render: (row) => row.serialNumber },
        { key: 'prior', label: 'Chỉ số lúc tháo', render: (row) => row.previousReading ?? '–' },
        { key: 'new', label: 'Chỉ số lúc lắp', render: (row) => row.installedReading },
      ], rows) : h('p', null, 'Chưa có lần lắp hoặc thay thiết bị nào.'));
    } catch (e) { list.replaceChildren(formError(errText(e))); }
  };
  dialog.body.append(list);
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Đóng'),
    h('button', { class: 'btn', onclick: () => addMeterChange(path, reload) }, 'Ghi lần lắp hoặc thay'));
  void reload();
}

function addMeterChange(path: string, reload: () => Promise<void>): void {
  const dialog = openDialog('Ghi lần lắp hoặc thay thiết bị');
  const kind = select([['ELECTRIC', 'Công tơ điện'], ['WATER', 'Đồng hồ nước']]);
  const day = input({ type: 'date', required: true, max: isoDay(), value: isoDay() });
  const serial = input({ maxlength: '80', required: true });
  const prior = input({ type: 'number', min: '0', step: '1' });
  const initial = input({ type: 'number', min: '0', step: '1', required: true });
  const error = h('div', { role: 'alert' });
  const save = h('button', { class: 'btn' }, 'Lưu lần lắp');
  dialog.body.append(error, field('Thiết bị', kind), field('Ngày lắp', day), field('Số sê-ri', serial),
    field('Chỉ số lúc tháo', prior, 'Để trống nếu đây là lần lắp đầu tiên'), field('Chỉ số lúc lắp', initial));
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Hủy'), save);
  save.addEventListener('click', () => busy(save, async () => {
    if (!serial.value.trim() || ![day, prior, initial].every((input) => input.checkValidity())) {
      error.replaceChildren(formError('Kiểm tra ngày lắp, số sê-ri và chỉ số.'));
      return;
    }
    try {
      await api.post(path, { meterKind: kind.value, installedOn: day.value, serialNumber: serial.value.trim(),
        previousReading: prior.value ? prior.valueAsNumber : null, installedReading: initial.valueAsNumber });
      dialog.close();
      await reload();
      toast('Đã lưu lịch sử công tơ');
    } catch (e) { error.replaceChildren(formError(errText(e))); }
  }));
}
