import { busy, h } from '../lib/dom';
import { api, errText } from '../lib/api';
import { confirmDialog, field, formError, input, openDialog, toast } from '../ui/kit';

interface Furnishing {
  furnishingId: number;
  itemName: string;
  quantity: number;
  conditionStatus: string;
  notes: string | null;
}

const endpoint = '/api/chi/furnishings';

export function roomFurnishings(roomId?: number): HTMLElement {
  const catalog = roomId === undefined;
  const target = catalog ? endpoint + '/standard-catalog' : endpoint;
  const listPath = catalog ? target : endpoint + '/room/' + roomId;
  const list = h('div', { class: 'stack' }, 'Đang tải thiết bị…');
  const load = async () => {
    try {
      const items = await api.get<Furnishing[]>(listPath);
      list.replaceChildren(...(items.length ? items.map((item) => {
        const remove = h('button', { class: 'btn secondary sm' }, 'Xóa');
        remove.addEventListener('click', () => busy(remove, async () => {
          if (!(await confirmDialog('Xóa thiết bị', 'Xóa ' + item.itemName + (catalog ? ' khỏi danh mục?' : ' khỏi phòng?'), 'Xóa thiết bị', true))) return;
          try { await api.del(target + '/' + item.furnishingId); await load(); }
          catch (e) { toast(errText(e), 'err'); }
        }));
        return h('div', { class: 'list-row' },
          h('div', { class: 'grow' }, h('b', null, item.itemName),
            h('p', null, item.quantity + ' · ' + item.conditionStatus), item.notes ? h('p', { class: 'muted' }, item.notes) : null),
          h('div', { class: 'row-actions' },
            h('button', { class: 'btn secondary sm', onclick: () => furnishingDialog(target, roomId, item, load) }, 'Sửa'), remove));
      }) : [h('p', { class: 'muted' }, catalog ? 'Danh mục chưa có thiết bị.' : 'Chưa ghi nhận thiết bị trong phòng.')]));
    } catch (e) { list.replaceChildren(formError(errText(e))); }
  };
  const standard = h('button', { class: 'btn secondary' }, 'Áp dụng bộ nội thất chuẩn');
  standard.addEventListener('click', () => busy(standard, async () => {
    try {
      const items = await api.get<Furnishing[]>(endpoint + '/standard-catalog');
      if (!items.length) { toast('Danh mục thiết bị chuẩn đang trống'); return; }
      if (!(await confirmDialog('Áp dụng bộ nội thất chuẩn', items.map((item) => item.itemName).join(', '), 'Áp dụng'))) return;
      const added = await api.post<Furnishing[]>(endpoint + '/apply-standard/' + roomId);
      toast(added.length ? 'Đã thêm ' + added.length + ' thiết bị' : 'Phòng đã có đủ thiết bị trong bộ chuẩn');
      await load();
    } catch (e) { toast(errText(e), 'err'); }
  }));
  void load();
  return h('section', { class: 'stack' }, h('h3', null, catalog ? 'Danh mục thiết bị chuẩn' : 'Thiết bị và nội thất'),
    h('div', { class: 'row-actions' }, h('button', { class: 'btn secondary', onclick: () => furnishingDialog(target, roomId, undefined, load) }, 'Thêm thiết bị'), catalog ? null : standard), list);
}

function furnishingDialog(target: string, roomId: number | undefined, item: Furnishing | undefined, done: () => Promise<void>): void {
  const dialog = openDialog(item ? 'Sửa thiết bị' : 'Thêm thiết bị');
  const error = h('div', { role: 'alert' });
  const name = input({ required: true, maxlength: '100', value: item?.itemName ?? '' });
  const quantity = input({ type: 'number', required: true, min: '1', step: '1', value: String(item?.quantity ?? 1) });
  const condition = input({ required: true, maxlength: '100', value: item?.conditionStatus ?? 'Hoạt động tốt' });
  const notes = input({ maxlength: '255', value: item?.notes ?? '' });
  const save = h('button', { class: 'btn' }, 'Lưu thiết bị');
  dialog.body.append(error, field('Tên thiết bị', name), field('Số lượng', quantity),
    field('Tình trạng', condition), field('Ghi chú', notes));
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Hủy'), save);
  save.addEventListener('click', () => busy(save, async () => {
    if (!name.value.trim() || !quantity.checkValidity() || !condition.value.trim()) {
      error.replaceChildren(formError('Nhập tên, số lượng nguyên dương và tình trạng thiết bị.'));
      return;
    }
    const data = { roomId, itemName: name.value.trim(), quantity: quantity.valueAsNumber, conditionStatus: condition.value.trim(), notes: notes.value.trim() };
    try {
      if (item) await api.put(target + '/' + item.furnishingId, data);
      else await api.post(target, data);
      dialog.close(); await done();
    } catch (e) { error.replaceChildren(formError(errText(e))); }
  }));
}
