import { busy, h } from '../lib/dom';
import { api, errText } from '../lib/api';
import { confirmDialog, emptyState, field, formError, input, openDialog, select, table, toast } from '../ui/kit';

interface ContractTemplate {
  templateId: number;
  templateName: string;
  termsContent: string;
  isDefault: boolean;
}

const endpoint = '/api/chi/contract-templates';

export function contractTemplateField(): { root: HTMLElement; value: () => number | null } {
  const picker = select([['', 'Không dùng mẫu']]);
  const status = h('p', { class: 'muted', role: 'status' }, 'Đang tải danh sách mẫu…');
  void api.get<ContractTemplate[]>(endpoint).then((templates) => {
    picker.append(...templates.map((t) => h('option', { value: String(t.templateId) }, t.templateName)));
    picker.value = String(templates.find((t) => t.isDefault)?.templateId ?? '');
    status.textContent = '';
  }).catch((e) => status.replaceChildren(formError(errText(e))));
  return { root: h('div', null, field('Mẫu hợp đồng', picker), status), value: () => picker.value ? Number(picker.value) : null };
}

export function manageContractTemplates(): void {
  const dialog = openDialog('Mẫu hợp đồng', { width: 760 });
  const list = h('div', null, 'Đang tải mẫu…');
  const load = async () => {
    try {
      const templates = await api.get<ContractTemplate[]>(endpoint);
      list.replaceChildren(table<ContractTemplate>([
        { key: 'n', label: 'Tên mẫu', render: (t) => t.templateName },
        { key: 'd', label: 'Mặc định', render: (t) => t.isDefault ? 'Có' : '–' },
        { key: 'a', label: '', render: (t) => h('div', { class: 'row-actions' },
          h('button', { class: 'btn secondary sm', onclick: () => editTemplate(t, load) }, 'Sửa mẫu'),
          h('button', { class: 'btn secondary sm', onclick: async () => {
            if (!(await confirmDialog('Xóa mẫu hợp đồng', 'Xóa mẫu ' + t.templateName + '?', 'Xóa mẫu', true))) return;
            try { await api.del(endpoint + '/' + t.templateId); await load(); }
            catch (e) { toast(errText(e), 'err'); }
          } }, 'Xóa mẫu')) },
      ], templates, { empty: emptyState('Chưa có mẫu hợp đồng', 'Thêm mẫu để dùng khi lập hợp đồng.') }));
    } catch (e) { list.replaceChildren(formError(errText(e))); }
  };
  dialog.body.append(list);
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Đóng'),
    h('button', { class: 'btn', onclick: () => editTemplate(undefined, load) }, 'Thêm mẫu'));
  void load();
}

function editTemplate(template: ContractTemplate | undefined, load: () => Promise<void>): void {
  const dialog = openDialog(template ? 'Sửa mẫu hợp đồng' : 'Thêm mẫu hợp đồng');
  const error = h('div', { role: 'alert' });
  const name = input({ maxlength: '100', required: true, value: template?.templateName ?? '' });
  const terms = h('textarea', { class: 'input', rows: 8, required: true }) as HTMLTextAreaElement;
  terms.value = template?.termsContent ?? '';
  const isDefault = input({ type: 'checkbox', checked: template?.isDefault ?? false });
  const save = h('button', { class: 'btn' }, 'Lưu mẫu');
  dialog.body.append(error, field('Tên mẫu', name), field('Nội dung điều khoản', terms),
    field('Dùng làm mẫu mặc định', isDefault));
  dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Hủy'), save);
  save.addEventListener('click', () => busy(save, async () => {
    if (!name.value.trim() || !terms.value.trim()) {
      error.replaceChildren(formError('Nhập tên mẫu và nội dung điều khoản.'));
      return;
    }
    const data = { templateName: name.value.trim(), termsContent: terms.value.trim(), isDefault: isDefault.checked };
    try {
      if (template) await api.put(endpoint + '/' + template.templateId, data);
      else await api.post(endpoint, data);
      dialog.close();
      await load();
      toast('Đã lưu mẫu hợp đồng');
    } catch (e) { error.replaceChildren(formError(errText(e))); }
  }));
}
