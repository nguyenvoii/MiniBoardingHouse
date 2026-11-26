// Các tab cấu hình hệ thống: thiết bị & phụ phí (UC108-109), danh mục sự cố/thiết bị (UC113),
// mẫu thông báo (UC121), lịch tự động (UC120), sao lưu & phục hồi (UC122).
import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, Page, PROPERTY_ID } from '../lib/api';
import { date, dateTime, money } from '../lib/format';
import { loadLive } from '../data/live';
import { confirmDialog, emptyState, field, formError, input, openDialog, plate, select, table, toast } from '../ui/kit';

const badge = (text: string, tone: 'ok' | 'warn' | 'off' | 'info' = 'off') => h('span', { class: `st ${tone}` }, text);
const bar = (title: string, ...actions: Node[]) => h('div', { class: 'toolbar' }, h('h2', { class: 'grow' }, title), ...actions);

// ------------------------------------------------------------------ UC108-109: thiết bị và phụ phí
interface EquipReq { equipmentRequestId: number; roomId: number; roomCode: string; tenantName: string; equipmentName: string; requestType: string; estimatedMonthlyFee: number | null; status: string; note: string | null; createdAt: string }
interface AddonFee { addonFeeId: number; roomId: number; roomCode: string; equipmentRequestId: number | null; feeName: string; amount: number; billingCycleMonths: number; startDate: string; endDate: string | null; isActive: boolean }

const EQ_STATUS: Record<string, [string, 'ok' | 'warn' | 'off' | 'info']> = {
  PENDING: ['Chờ duyệt', 'warn'], APPROVED: ['Đã duyệt, chờ lắp', 'info'], REJECTED: ['Từ chối', 'off'], INSTALLED: ['Đã lắp đặt', 'ok'], REMOVED: ['Đã tháo', 'off'],
};
const EQ_TYPE: Record<string, string> = { INSTALL: 'Lắp thiết bị mới', EXTRA_USAGE: 'Dùng thêm thiết bị' };

export async function equipmentTab(reload: () => void): Promise<HTMLElement> {
  const [reqs, fees] = await Promise.all([
    api.get<Page<EquipReq>>('/api/equipment-requests?size=100'),
    api.get<AddonFee[]>('/api/room-addon-fees'),
  ]);
  const act = (label: string, fn: () => void, cls = 'secondary') => h('button', { class: `btn ${cls} sm`, onclick: fn }, label);
  const call = (b: HTMLElement, fn: () => Promise<unknown>, ok: string) => busy(b as HTMLButtonElement, async () => { try { await fn(); toast(ok); reload(); } catch (e) { toast(errText(e), 'err'); } });
  const requestActions = (r: EquipReq): HTMLElement => {
    const box = h('div', { class: 'row-actions' });
    if (r.status === 'PENDING') {
      const yes = h('button', { class: 'btn sm' }, icon('check', 14), 'Duyệt');
      yes.addEventListener('click', () => call(yes, () => api.patch(`/api/equipment-requests/${r.equipmentRequestId}/review`, { approve: true, note: null }), 'Đã duyệt yêu cầu'));
      box.append(yes, act('Từ chối', () => rejectDialog(r, reload)));
    }
    if (r.status === 'APPROVED') box.append(act('Đã lắp đặt', () => installDialog(r, reload), ''));
    if (r.status === 'INSTALLED') {
      const rm = h('button', { class: 'btn secondary sm' }, 'Đã tháo');
      rm.addEventListener('click', async () => { if (await confirmDialog('Đã tháo thiết bị', `Tắt phụ phí hàng tháng của ${r.equipmentName} (phòng ${r.roomCode})?`, 'Xác nhận')) call(rm, () => api.post(`/api/equipment-requests/${r.equipmentRequestId}/remove`), 'Đã tháo, phụ phí đã tắt'); });
      box.append(rm);
    }
    return box;
  };
  const toggle = (f: AddonFee) => {
    const b = h('button', { class: 'btn secondary sm' }, f.isActive ? 'Tắt phí' : 'Bật phí');
    b.addEventListener('click', () => call(b, () => api.patch(`/api/chi/addon-fees/${f.addonFeeId}/toggle`), f.isActive ? 'Đã tắt phụ phí' : 'Đã bật phụ phí'));
    return b;
  };
  return h('div', { class: 'stack' },
    bar('Yêu cầu thiết bị của khách'),
    table<EquipReq>([
      { key: 'r', label: 'Phòng', render: (x) => plate(x.roomCode) },
      { key: 't', label: 'Khách', render: (x) => x.tenantName },
      { key: 'e', label: 'Thiết bị', render: (x) => h('span', null, h('b', null, x.equipmentName), h('span', { class: 'muted' }, ` · ${EQ_TYPE[x.requestType] ?? x.requestType}`)) },
      { key: 'f', label: 'Phí dự kiến/tháng (₫)', num: true, render: (x) => (x.estimatedMonthlyFee == null ? '–' : money(x.estimatedMonthlyFee)) },
      { key: 's', label: 'Trạng thái', render: (x) => { const [l, tone] = EQ_STATUS[x.status] ?? [x.status, 'off']; return badge(l, tone); } },
      { key: 'n', label: 'Ghi chú', render: (x) => x.note ?? '' },
      { key: 'a', label: '', render: requestActions },
    ], reqs.items, { empty: emptyState('Chưa có yêu cầu thiết bị', 'Khách gửi yêu cầu lắp hoặc dùng thêm thiết bị ở mục Hỗ trợ trên trang của họ.') }),
    bar('Phụ phí hàng tháng theo phòng', h('button', { class: 'btn', onclick: () => addonDialog(reload) }, icon('plus', 16), 'Thêm phụ phí')),
    table<AddonFee>([
      { key: 'r', label: 'Phòng', render: (x) => plate(x.roomCode) },
      { key: 'n', label: 'Khoản phí', render: (x) => x.feeName },
      { key: 'a', label: 'Số tiền (₫)', num: true, render: (x) => money(x.amount) },
      { key: 'c', label: 'Chu kỳ', render: (x) => `${x.billingCycleMonths} tháng` },
      { key: 'd', label: 'Hiệu lực', render: (x) => `${date(x.startDate)} – ${x.endDate ? date(x.endDate) : 'không hạn'}` },
      { key: 's', label: 'Trạng thái', render: (x) => (x.isActive ? badge('Đang tính', 'ok') : badge('Đã tắt')) },
      { key: 'x', label: '', render: toggle },
    ], fees, { empty: emptyState('Chưa có phụ phí', 'Phụ phí được tạo khi xác nhận đã lắp thiết bị, hoặc thêm trực tiếp ở đây.') }),
  );
}

function rejectDialog(r: EquipReq, done: () => void): void {
  const d = openDialog(`Từ chối yêu cầu ${r.equipmentName}`);
  const err = h('div');
  const note = h('textarea', { class: 'textarea', placeholder: 'Lý do từ chối (bắt buộc)', 'aria-label': 'Lý do từ chối' }) as HTMLTextAreaElement;
  d.body.append(err, field('Lý do', note));
  const ok = h('button', { class: 'btn danger' }, 'Từ chối');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!note.value.trim()) { err.replaceChildren(formError('Nhập lý do từ chối để khách biết.')); return; }
    try { await api.patch(`/api/equipment-requests/${r.equipmentRequestId}/review`, { approve: false, note: note.value.trim() }); d.close(); toast('Đã từ chối yêu cầu'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function installDialog(r: EquipReq, done: () => void): void {
  const d = openDialog(`Đã lắp ${r.equipmentName} · phòng ${r.roomCode}`);
  const err = h('div');
  const name = input({ value: r.equipmentName, 'aria-label': 'Tên khoản phí' });
  const amount = input({ type: 'number', min: '0', step: '1000', value: String(r.estimatedMonthlyFee ?? ''), class: 'input num-in', 'aria-label': 'Số tiền mỗi kỳ' });
  const cycle = input({ type: 'number', min: '1', max: '12', value: '1', class: 'input num-in', 'aria-label': 'Chu kỳ (tháng)' });
  d.body.append(err, h('p', { class: 'muted' }, 'Hệ thống tạo phụ phí và cộng vào hóa đơn các kỳ sau.'), field('Tên khoản phí', name), h('div', { class: 'form-grid' }, field('Số tiền mỗi kỳ (₫)', amount), field('Chu kỳ (tháng)', cycle)));
  const ok = h('button', { class: 'btn' }, 'Xác nhận đã lắp');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!(Number(amount.value) > 0)) { err.replaceChildren(formError('Số tiền phụ phí phải lớn hơn 0.')); return; }
    try { await api.post(`/api/equipment-requests/${r.equipmentRequestId}/install`, { feeName: name.value.trim(), amount: Number(amount.value), billingCycleMonths: Number(cycle.value) }); d.close(); toast('Đã ghi nhận lắp đặt và tạo phụ phí'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function addonDialog(done: () => void): Promise<void> {
  const { rooms } = await loadLive();
  const d = openDialog('Thêm phụ phí cho phòng');
  const err = h('div');
  const room = select(rooms.filter((r) => r.status === 'RENTED').map((r) => [String(r.roomId), r.code] as [string, string]));
  const name = input({ placeholder: 'Ví dụ: Tủ lạnh riêng', 'aria-label': 'Tên khoản phí' });
  const amount = input({ type: 'number', min: '0', step: '1000', class: 'input num-in', 'aria-label': 'Số tiền mỗi kỳ' });
  const cycle = input({ type: 'number', min: '1', max: '12', value: '1', class: 'input num-in', 'aria-label': 'Chu kỳ (tháng)' });
  const start = input({ type: 'date', 'aria-label': 'Bắt đầu' });
  d.body.append(err, field('Phòng đang thuê', room), field('Tên khoản phí', name), h('div', { class: 'form-grid' }, field('Số tiền (₫)', amount), field('Chu kỳ (tháng)', cycle)), field('Bắt đầu tính từ', start));
  const ok = h('button', { class: 'btn' }, 'Thêm phụ phí');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!room.value || !name.value.trim() || !(Number(amount.value) > 0)) { err.replaceChildren(formError('Chọn phòng, nhập tên và số tiền lớn hơn 0.')); return; }
    try { await api.post('/api/chi/addon-fees', { roomId: Number(room.value), feeName: name.value.trim(), amount: Number(amount.value), billingCycleMonths: Number(cycle.value), startDate: start.value || null, isActive: true }); d.close(); toast('Đã thêm phụ phí'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

// ------------------------------------------------------------------ UC113: danh mục nhóm sự cố / thiết bị
interface Category { categoryId: number; categoryCode: string; categoryName: string; categoryType: string; description: string | null; isActive: boolean }
const CAT_TYPE: Record<string, string> = { INCIDENT: 'Nhóm sự cố', EQUIPMENT: 'Nhóm thiết bị' };

export async function categoriesTab(reload: () => void): Promise<HTMLElement> {
  const list = await api.get<Category[]>('/api/incident-categories');
  const toggle = (c: Category) => {
    const b = h('button', { class: 'btn secondary sm' }, c.isActive ? 'Ngừng dùng' : 'Dùng lại');
    b.addEventListener('click', () => busy(b, async () => { try { await api.patch(`/api/incident-categories/${c.categoryId}/active`, { active: !c.isActive }); toast(c.isActive ? 'Đã ngừng dùng nhóm' : 'Đã dùng lại nhóm'); reload(); } catch (e) { toast(errText(e), 'err'); } }));
    return b;
  };
  return h('div', { class: 'stack' },
    bar('Danh mục nhóm sự cố và thiết bị', h('button', { class: 'btn', onclick: () => categoryDialog(null, reload) }, icon('plus', 16), 'Thêm nhóm')),
    table<Category>([
      { key: 'c', label: 'Mã', render: (c) => h('span', { class: 'cell-main' }, c.categoryCode) },
      { key: 'n', label: 'Tên nhóm', render: (c) => c.categoryName },
      { key: 't', label: 'Loại', render: (c) => CAT_TYPE[c.categoryType] ?? c.categoryType },
      { key: 'd', label: 'Mô tả', render: (c) => c.description ?? '' },
      { key: 's', label: 'Trạng thái', render: (c) => (c.isActive ? badge('Đang dùng', 'ok') : badge('Ngừng dùng')) },
      { key: 'a', label: '', render: (c) => h('div', { class: 'row-actions' }, h('button', { class: 'btn secondary sm', onclick: () => categoryDialog(c, reload) }, icon('edit', 14), 'Sửa'), toggle(c)) },
    ], list, { empty: emptyState('Chưa có nhóm nào', 'Thêm nhóm sự cố (điện, nước...) hoặc nhóm thiết bị để phân loại yêu cầu.') }),
  );
}

function categoryDialog(c: Category | null, done: () => void): void {
  const d = openDialog(c ? `Sửa nhóm ${c.categoryName}` : 'Thêm nhóm');
  const err = h('div');
  const code = input({ value: c?.categoryCode ?? '', maxlength: '40', placeholder: 'Ví dụ: ELECTRIC_ISSUE', 'aria-label': 'Mã nhóm' });
  const name = input({ value: c?.categoryName ?? '', maxlength: '100', 'aria-label': 'Tên nhóm' });
  const type = select(Object.entries(CAT_TYPE) as [string, string][], c?.categoryType ?? 'INCIDENT', { 'aria-label': 'Loại nhóm' });
  const desc = input({ value: c?.description ?? '', maxlength: '255', placeholder: 'Không bắt buộc', 'aria-label': 'Mô tả' });
  d.body.append(err, h('div', { class: 'form-grid' }, field('Mã nhóm', code), field('Tên nhóm', name)), field('Loại nhóm', type), field('Mô tả', desc));
  const ok = h('button', { class: 'btn' }, c ? 'Lưu' : 'Thêm nhóm');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!code.value.trim() || !name.value.trim()) { err.replaceChildren(formError('Nhập mã và tên nhóm.')); return; }
    const body = { categoryCode: code.value.trim(), categoryName: name.value.trim(), categoryType: type.value, description: desc.value.trim() || null };
    try { if (c) await api.put(`/api/incident-categories/${c.categoryId}`, body); else await api.post('/api/incident-categories', body); d.close(); toast(c ? 'Đã lưu nhóm' : 'Đã thêm nhóm'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

// ------------------------------------------------------------------ UC121: mẫu thông báo và kênh gửi
interface Template { notifTemplateId: number; templateCode: string; channel: string; subject: string | null; content: string; isActive: boolean }
const CHANNEL: Record<string, string> = { EMAIL: 'Email', SYSTEM_APP: 'Trên website', SMS: 'SMS' };

export async function templatesTab(reload: () => void): Promise<HTMLElement> {
  const list = await api.get<Template[]>('/api/notification-templates');
  const toggle = (t: Template) => {
    const b = h('button', { class: 'btn secondary sm' }, t.isActive ? 'Tắt' : 'Bật');
    b.addEventListener('click', () => busy(b, async () => { try { await api.patch(`/api/notification-templates/${t.notifTemplateId}/active`, { active: !t.isActive }); toast(t.isActive ? 'Đã tắt mẫu' : 'Đã bật mẫu'); reload(); } catch (e) { toast(errText(e), 'err'); } }));
    return b;
  };
  return h('div', { class: 'stack' },
    bar('Mẫu thông báo', h('button', { class: 'btn', onclick: () => templateDialog(null, reload) }, icon('plus', 16), 'Thêm mẫu')),
    h('p', { class: 'muted', style: 'padding:0 16px' }, 'Hệ thống dùng mẫu theo mã: INVOICE_ISSUED (hóa đơn mới), INVOICE_ADJUSTED (hóa đơn điều chỉnh), PAYMENT_CONFIRMED (xác nhận thanh toán); chưa có mẫu thì dùng nội dung mặc định. Biến dạng {{invoice}}, {{amount}}, {{dueDate}}, {{reason}}, {{room}}, {{tenant}}, {{contract}}. Kênh Email cần cấu hình SMTP; kênh website luôn hoạt động.'),
    table<Template>([
      { key: 'c', label: 'Mã mẫu', render: (t) => h('span', { class: 'cell-main' }, t.templateCode) },
      { key: 'h', label: 'Kênh', render: (t) => CHANNEL[t.channel] ?? t.channel },
      { key: 's', label: 'Tiêu đề', render: (t) => t.subject ?? '–' },
      { key: 't', label: 'Trạng thái', render: (t) => (t.isActive ? badge('Đang dùng', 'ok') : badge('Đã tắt')) },
      { key: 'a', label: '', render: (t) => h('div', { class: 'row-actions' }, h('button', { class: 'btn secondary sm', onclick: () => templateDialog(t, reload) }, icon('edit', 14), 'Sửa'), h('button', { class: 'btn secondary sm', onclick: () => previewDialog(t) }, 'Xem trước'), toggle(t)) },
    ], list, { empty: emptyState('Chưa có mẫu thông báo', 'Thêm mẫu cho hóa đơn mới, nhắc nợ, hợp đồng sắp hết hạn.') }),
  );
}

function templateDialog(t: Template | null, done: () => void): void {
  const d = openDialog(t ? `Sửa mẫu ${t.templateCode}` : 'Thêm mẫu thông báo', { width: 600 });
  const err = h('div');
  const code = input({ value: t?.templateCode ?? '', maxlength: '60', placeholder: 'Ví dụ: INVOICE_ISSUED', 'aria-label': 'Mã mẫu' });
  const channel = select(Object.entries(CHANNEL) as [string, string][], t?.channel ?? 'SYSTEM_APP', { 'aria-label': 'Kênh gửi' });
  const subject = input({ value: t?.subject ?? '', maxlength: '200', 'aria-label': 'Tiêu đề' });
  const content = h('textarea', { class: 'textarea', style: 'min-height:140px', 'aria-label': 'Nội dung' }) as HTMLTextAreaElement;
  content.value = t?.content ?? '';
  d.body.append(err, h('div', { class: 'form-grid' }, field('Mã mẫu', code), field('Kênh gửi', channel)), field('Tiêu đề', subject), field('Nội dung', content));
  const ok = h('button', { class: 'btn' }, t ? 'Lưu mẫu' : 'Thêm mẫu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!code.value.trim() || !content.value.trim()) { err.replaceChildren(formError('Nhập mã mẫu và nội dung.')); return; }
    const body = { templateCode: code.value.trim(), channel: channel.value, subject: subject.value.trim() || null, content: content.value };
    try { if (t) await api.put(`/api/notification-templates/${t.notifTemplateId}`, body); else await api.post('/api/notification-templates', body); d.close(); toast('Đã lưu mẫu thông báo'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function previewDialog(t: Template): Promise<void> {
  const d = openDialog(`Xem trước ${t.templateCode}`, { width: 560 });
  d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Đóng'));
  try {
    const p = await api.post<{ subject: string | null; content: string }>(`/api/notification-templates/${t.notifTemplateId}/preview`, {});
    d.body.append(h('p', null, h('b', null, p.subject ?? '(không có tiêu đề)')), h('pre', { style: 'white-space:pre-wrap;font:inherit' }, p.content));
  } catch (e) { d.body.append(formError(errText(e))); }
}

// ------------------------------------------------------------------ UC120: lịch tự động
interface Job { name: string; description: string; cron: string; running: boolean; lastStart: string | null; lastEnd: string | null; lastResult: string | null; lastError: string | null; scheduled: boolean; overridden: boolean }

export async function jobsTab(reload: () => void): Promise<HTMLElement> {
  const jobs = await api.get<Job[]>('/api/admin/jobs');
  const run = (j: Job) => {
    const b = h('button', { class: 'btn secondary sm', disabled: j.running }, icon('refresh', 14), 'Chạy ngay');
    b.addEventListener('click', async () => {
      if (!(await confirmDialog('Chạy tác vụ ngay', `Chạy "${j.description || j.name}" bây giờ? Tác vụ có thể tạo hóa đơn hoặc gửi nhắc nhở thật.`, 'Chạy'))) return;
      busy(b, async () => { try { const r = await api.post<Job>(`/api/admin/jobs/${j.name}/run`); toast(r.lastResult === 'FAILED' ? `Tác vụ lỗi: ${r.lastError ?? ''}` : 'Đã chạy xong tác vụ', r.lastResult === 'FAILED' ? 'err' : 'ok'); reload(); } catch (e) { toast(errText(e), 'err'); } });
    });
    return b;
  };
  return h('div', { class: 'stack' },
    bar('Lịch tự động'),
    h('p', { class: 'muted', style: 'padding:0 16px' }, 'Sửa giờ chạy của từng tác vụ (áp dụng ngay, lưu vào cơ sở dữ liệu). Tác vụ chỉ tự chạy khi máy chủ bật chạy tự động (app.jobs.enabled hoặc minihouse.scheduler.enabled = true); tắt hoặc để "-" thì chỉ chạy tay.'),
    table<Job>([
      { key: 'n', label: 'Tác vụ', render: (j) => h('span', null, h('b', null, j.description || j.name), h('span', { class: 'muted' }, ` · ${j.name}`)) },
      { key: 'c', label: 'Lịch (cron)', render: (j) => h('span', null, h('code', null, j.cron === '-' ? 'tắt' : j.cron), j.overridden ? h('span', { class: 'st info', style: 'margin-left:6px' }, 'Đã chỉnh') : null, h('span', { class: 'muted', style: 'display:block' }, j.cron === '-' ? 'Chỉ chạy tay' : j.scheduled ? 'Đang tự chạy' : 'Chưa bật chạy tự động trên máy chủ')) },
      { key: 'l', label: 'Lần chạy gần nhất', render: (j) => (j.lastStart ? dateTime(j.lastStart) : 'Chưa chạy từ lúc bật máy chủ') },
      { key: 'r', label: 'Kết quả', render: (j) => (j.running ? badge('Đang chạy', 'info') : j.lastResult === 'SUCCESS' ? badge('Thành công', 'ok') : j.lastResult === 'FAILED' ? h('span', { class: 'st warn', title: j.lastError ?? '' }, 'Lỗi') : badge('–')) },
      { key: 'a', label: '', render: (j) => h('div', { class: 'row-actions' }, h('button', { class: 'btn secondary sm', onclick: () => scheduleDialog(j, reload) }, icon('edit', 14), 'Sửa lịch'), run(j)) },
    ], jobs, { empty: emptyState('Chưa có tác vụ tự động', 'Máy chủ chưa đăng ký tác vụ nào.') }),
  );
}

// ------------------------------------------------------------------ UC122: sao lưu và phục hồi
interface Backup { name: string; sizeBytes: number; modifiedAt: string }

export async function backupTab(reload: () => void): Promise<HTMLElement> {
  const list = await api.get<Backup[]>('/api/admin/backups');
  const create = h('button', { class: 'btn' }, icon('plus', 16), 'Sao lưu ngay');
  create.addEventListener('click', () => busy(create, async () => { try { const b = await api.post<Backup>('/api/admin/backups'); toast(`Đã sao lưu ${b.name}`); reload(); } catch (e) { toast(errText(e), 'err'); } }));
  const mb = (n: number) => `${(n / 1048576).toFixed(1)} MB`;
  return h('div', { class: 'stack' },
    bar('Sao lưu dữ liệu', create),
    h('p', { class: 'muted', style: 'padding:0 16px' }, 'Phục hồi ghi đè toàn bộ dữ liệu hiện tại bằng bản sao lưu và không hoàn tác được. Chỉ chủ trọ dùng được.'),
    table<Backup>([
      { key: 'n', label: 'Bản sao lưu', render: (b) => h('span', { class: 'cell-main' }, b.name) },
      { key: 's', label: 'Dung lượng', num: true, render: (b) => mb(b.sizeBytes) },
      { key: 'm', label: 'Thời điểm', render: (b) => dateTime(b.modifiedAt) },
      { key: 'a', label: '', render: (b) => h('button', { class: 'btn danger sm', onclick: () => restoreDialog(b) }, 'Phục hồi') },
    ], list, { empty: emptyState('Chưa có bản sao lưu', 'Bấm Sao lưu ngay để tạo bản đầu tiên.') }),
  );
}

function restoreDialog(b: Backup): void {
  const d = openDialog(`Phục hồi từ ${b.name}`);
  const err = h('div');
  const confirm = input({ autocomplete: 'off', placeholder: 'Gõ tên cơ sở dữ liệu', 'aria-label': 'Tên cơ sở dữ liệu để xác nhận' });
  d.body.append(err, h('p', null, 'Toàn bộ dữ liệu hiện tại sẽ bị thay bằng bản sao lưu này, mọi người dùng phải đăng nhập lại. Gõ đúng tên cơ sở dữ liệu để xác nhận.'), field('Tên cơ sở dữ liệu', confirm));
  const ok = h('button', { class: 'btn danger' }, 'Phục hồi dữ liệu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!confirm.value.trim()) { err.replaceChildren(formError('Gõ tên cơ sở dữ liệu để xác nhận.')); return; }
    try { await api.post(`/api/admin/backups/${encodeURIComponent(b.name)}/restore`, { confirm: confirm.value.trim() }); d.close(); toast('Đã phục hồi dữ liệu, hãy đăng nhập lại'); } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

void PROPERTY_ID;

const CRON_PRESETS: Array<[string, string]> = [['', 'Chọn mẫu giờ chạy…'], ['0 0 1 * * *', 'Hằng ngày lúc 01:00'], ['0 30 7 * * *', 'Hằng ngày lúc 07:30'], ['0 0 8 * * *', 'Hằng ngày lúc 08:00'], ['0 0 8 1 * *', 'Ngày 1 hằng tháng lúc 08:00'], ['0 0 9 * * MON', 'Thứ hai hằng tuần lúc 09:00']];

function scheduleDialog(j: Job, done: () => void): void {
  const d = openDialog(`Lịch chạy · ${j.description || j.name}`);
  const err = h('div');
  const on = h('input', { type: 'checkbox', checked: j.cron !== '-', 'aria-label': 'Tự chạy theo lịch' }) as HTMLInputElement;
  const cron = input({ value: j.cron === '-' ? '0 0 8 * * *' : j.cron, 'aria-label': 'Biểu thức cron', maxlength: '60' });
  const preset = select(CRON_PRESETS, '', { 'aria-label': 'Mẫu giờ chạy' });
  preset.addEventListener('change', () => { if (preset.value) cron.value = preset.value; });
  d.body.append(err, h('label', { style: 'display:flex;gap:8px;align-items:center;font-weight:500' }, on, 'Tự chạy theo lịch'), field('Mẫu giờ chạy', preset),
    field('Cron (giây phút giờ ngày tháng thứ)', cron, 'Ví dụ 0 0 8 * * * = 8 giờ sáng mỗi ngày. Giờ máy chủ.'));
  const ok = h('button', { class: 'btn' }, 'Lưu lịch');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.put(`/api/admin/jobs/${j.name}/schedule`, { cron: on.checked ? cron.value.trim() : '-', enabled: on.checked }); d.close(); toast('Đã lưu lịch chạy'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
