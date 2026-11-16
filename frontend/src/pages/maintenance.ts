import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, Page } from '../lib/api';
import { dateTime, money } from '../lib/format';
import { liveContracts } from '../data/live';
import type { Staff } from '../data/types';
import { emptyState, field, formError, input, openDialog, pageHead, plate, select, table, toast } from '../ui/kit';

export interface Ticket {
  requestId: number; roomId: number; roomCode: string; tenantId: number | null; tenantName: string | null; category: string; description: string;
  priority: string; status: string; createdAt: string;
  log: { assignedStaffId: number | null; assignedStaffName: string | null; repairCost: number | null; tenantRating: number | null; resolutionNotes: string | null; completedAt: string | null } | null;
}
interface Category { categoryId: number; categoryCode: string; categoryName: string; categoryType: string; active?: boolean; isActive?: boolean }

export const TICKET_STATUS: Record<string, [string, string]> = {
  OPEN: ['Mới nhận', 'warn'], ASSIGNED: ['Đã phân công', 'info'], IN_PROGRESS: ['Đang xử lý', 'info'], RESOLVED: ['Đã xử lý xong', 'ok'], CLOSED: ['Đã đóng', 'off'],
};
export const PRIORITY: Record<string, string> = { LOW: 'Thấp', NORMAL: 'Bình thường', URGENT: 'Khẩn cấp' };
export const ticketBadge = (s: string) => { const [l, t] = TICKET_STATUS[s] ?? [s, 'off']; return h('span', { class: `st ${t}` }, l); };

export async function maintenancePage(view: HTMLElement): Promise<void> {
  const state = { status: '' };
  const holder = h('div');
  const cats = await api.get<Category[]>('/api/incident-categories?type=INCIDENT').catch(() => [] as Category[]);
  const catName = (code: string) => cats.find((c) => c.categoryCode === code)?.categoryName ?? code;
  const status = select([['', 'Mọi trạng thái'], ...Object.entries(TICKET_STATUS).map(([k, v]) => [k, v[0]] as [string, string])], '');
  const load = async () => {
    const r = await api.get<Page<Ticket>>(`/api/maintenance-requests?status=${state.status}&size=100`);
    holder.replaceChildren(table<Ticket>([
      { key: 'd', label: 'Gửi lúc', render: (t) => dateTime(t.createdAt), sort: (a, b) => a.createdAt.localeCompare(b.createdAt) },
      { key: 'r', label: 'Phòng', render: (t) => plate(t.roomCode) },
      { key: 'c', label: 'Loại sự cố', render: (t) => catName(t.category) },
      { key: 'n', label: 'Mô tả', render: (t) => t.description.length > 60 ? t.description.slice(0, 60) + '…' : t.description },
      { key: 'p', label: 'Mức độ', render: (t) => (t.priority === 'URGENT' ? h('span', { class: 'st danger' }, icon('alert', 14), PRIORITY[t.priority]) : PRIORITY[t.priority] ?? t.priority) },
      { key: 's', label: 'Trạng thái', render: (t) => ticketBadge(t.status) },
      { key: 'a', label: 'Người xử lý', render: (t) => t.log?.assignedStaffName ?? '–' },
    ], r.items, { onRow: (t) => openTicket(t, catName(t.category), () => void load()),
      empty: emptyState('Chưa có yêu cầu sửa chữa', 'Người thuê gửi sự cố từ ứng dụng, hoặc bạn tạo giúp khách bằng nút Tạo yêu cầu.') }));
  };
  status.addEventListener('change', () => { state.status = status.value; void load(); });
  view.replaceChildren(
    pageHead('Sửa chữa', 'Sự cố người thuê báo: phân công, theo dõi, ghi chi phí và đóng.', [h('button', { class: 'btn', onclick: () => createTicket(cats, () => void load()) }, icon('plus', 16), 'Tạo yêu cầu')]),
    h('section', { class: 'panel panel-flush' }, h('div', { class: 'toolbar' }, h('span', { class: 'grow' }), status), holder),
  );
  await load();
}

async function createTicket(cats: Category[], done: () => void): Promise<void> {
  const contracts = await liveContracts().catch(() => []);
  const d = openDialog('Tạo yêu cầu sửa chữa');
  const err = h('div');
  const room = select(contracts.map((c) => [String(c.roomId), `${c.roomCode} · ${c.tenantName}`] as [string, string]));
  const cat = select(cats.length ? cats.map((c) => [c.categoryCode, c.categoryName] as [string, string]) : [['', 'Chưa có nhóm sự cố (nạp dữ liệu mẫu)']]);
  const prio = select([['NORMAL', 'Bình thường'], ['URGENT', 'Khẩn cấp'], ['LOW', 'Thấp']]);
  const desc = h('textarea', { class: 'textarea', placeholder: 'Mô tả sự cố' }) as HTMLTextAreaElement;
  d.body.append(err, field('Phòng', room), h('div', { class: 'form-grid' }, field('Loại sự cố', cat), field('Mức độ', prio)), field('Mô tả', desc));
  const ok = h('button', { class: 'btn' }, 'Tạo yêu cầu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const c = contracts.find((x) => String(x.roomId) === room.value);
    try {
      await api.post('/api/maintenance-requests', { roomId: Number(room.value), tenantId: c?.tenantId ?? null, category: cat.value, description: desc.value.trim(), priority: prio.value });
      d.close(); toast('Đã tạo yêu cầu'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function openTicket(t: Ticket, catLabel: string, reload: () => void): Promise<void> {
  const d = openDialog(`Sự cố phòng ${t.roomCode}`, { drawer: true });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(t.roomCode, 'lg'), ticketBadge(t.status), t.priority === 'URGENT' ? h('span', { class: 'st danger' }, icon('alert', 14), 'Khẩn cấp') : null),
    h('dl', { class: 'dl' }, h('dt', null, 'Loại'), h('dd', null, catLabel), h('dt', null, 'Người báo'), h('dd', null, t.tenantName ?? '–'), h('dt', null, 'Gửi lúc'), h('dd', null, dateTime(t.createdAt)),
      h('dt', null, 'Người xử lý'), h('dd', null, t.log?.assignedStaffName ?? 'Chưa phân công'), h('dt', null, 'Chi phí sửa'), h('dd', null, t.log?.repairCost != null ? `${money(t.log.repairCost)} ₫` : '–'),
      h('dt', null, 'Kết quả'), h('dd', null, t.log?.resolutionNotes ?? '–'), h('dt', null, 'Người thuê đánh giá'), h('dd', null, t.log?.tenantRating ? `${t.log.tenantRating}/5 sao` : '–')),
    h('div', null, h('h3', { style: 'margin-bottom:6px' }, 'Mô tả'), h('p', null, t.description)),
  );
  const act = (label: string, fn: () => void, cls = '') => h('button', { class: `btn ${cls}`, onclick: fn }, label);
  const call = async (path: string, body?: unknown, msg = 'Đã cập nhật') => {
    try { await api.patch(path, body); d.close(); toast(msg); reload(); } catch (e) { toast(errText(e), 'err'); }
  };
  const foot: HTMLElement[] = [h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng')];
  if (t.status === 'OPEN' || t.status === 'ASSIGNED') foot.push(act(t.status === 'OPEN' ? 'Phân công' : 'Phân công lại', () => { d.close(); assign(t, reload); }, 'secondary'));
  if (t.status === 'ASSIGNED') foot.push(act('Bắt đầu xử lý', () => void call(`/api/maintenance-requests/${t.requestId}/start`, undefined, 'Đã bắt đầu xử lý')));
  if (t.status === 'ASSIGNED' || t.status === 'IN_PROGRESS') foot.push(act('Hoàn tất', () => { d.close(); resolve(t, reload); }));
  if (t.status === 'RESOLVED') foot.push(act('Đóng yêu cầu', () => void call(`/api/maintenance-requests/${t.requestId}/close`, undefined, 'Đã đóng')));
  d.foot.append(...foot);
}

async function assign(t: Ticket, done: () => void): Promise<void> {
  const staff = await api.get<Page<Staff>>('/api/staff?size=100').catch(() => ({ items: [] as Staff[] }));
  const d = openDialog('Phân công xử lý');
  const err = h('div');
  const who = select(staff.items.filter((s) => s.status === 'ACTIVE').map((s) => [String(s.userId), s.fullName] as [string, string]));
  d.body.append(err, field('Nhân viên xử lý', who, staff.items.length ? undefined : 'Chưa có nhân viên nào, hãy thêm ở mục Nhân viên'));
  const ok = h('button', { class: 'btn' }, 'Phân công');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.patch(`/api/maintenance-requests/${t.requestId}/assign`, { staffId: Number(who.value) }); d.close(); toast('Đã phân công'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function resolve(t: Ticket, done: () => void): void {
  const d = openDialog('Hoàn tất sửa chữa');
  const err = h('div');
  const cost = input({ type: 'number', min: '0', step: '1000', class: 'input num-in', placeholder: '0' });
  const notes = h('textarea', { class: 'textarea', placeholder: 'Đã làm gì, thay linh kiện gì' }) as HTMLTextAreaElement;
  d.body.append(err, field('Chi phí sửa (₫)', cost), field('Ghi chú kết quả', notes));
  const ok = h('button', { class: 'btn' }, 'Hoàn tất');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.patch(`/api/maintenance-requests/${t.requestId}/resolve`, { repairCost: Number(cost.value || 0), resolutionNotes: notes.value.trim() }); d.close(); toast('Đã hoàn tất'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
