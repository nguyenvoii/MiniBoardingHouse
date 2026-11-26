import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, can, errText, Page } from '../lib/api';
import { dateTime } from '../lib/format';
import type { ActivityLog, Staff } from '../data/types';
import { confirmDialog, emptyState, field, formError, input, openDialog, pageHead, panel, select, statusBadge, table, toast } from '../ui/kit';
import { ROLE_NAMES } from '../ui/shell';

const STAFF_ROLES: Array<[string, string]> = [['MANAGER', 'Quản lý'], ['CASHIER', 'Thu ngân'], ['METER_READER', 'Ghi chỉ số']];

export async function staffPage(view: HTMLElement): Promise<void> {
  const holder = h('div');
  const q = input({ type: 'search', placeholder: 'Tên, email hoặc số điện thoại', 'aria-label': 'Tìm nhân viên' });
  const load = async () => {
    const r = await api.get<Page<Staff>>(`/api/staff?keyword=${encodeURIComponent(q.value.trim())}&size=50`);
    holder.replaceChildren(table<Staff>([
      { key: 'n', label: 'Họ tên', render: (s) => h('span', { class: 'cell-main' }, s.fullName), sort: (a, b) => a.fullName.localeCompare(b.fullName, 'vi') },
      { key: 'c', label: 'Liên hệ', render: (s) => h('span', null, s.email ?? '–', h('span', { class: 'sub' }, s.phoneNumber ?? '')) },
      { key: 'r', label: 'Vai trò', render: (s) => s.roles.map((x) => ROLE_NAMES[x] ?? x).join(', ') },
      { key: 's', label: 'Trạng thái', render: (s) => statusBadge(s.status) },
      { key: 'a', label: '', render: (s) => can('LANDLORD') ? h('div', { class: 'row-actions' },
        h('button', { class: 'btn secondary sm', onclick: () => edit(s, load) }, icon('edit', 14), 'Sửa'),
        h('button', { class: `btn sm ${s.status === 'ACTIVE' ? 'danger' : 'secondary'}`, onclick: () => toggle(s, load) }, icon('lock', 14), s.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa')) : '' },
    ], r.items, { empty: emptyState('Chưa có nhân viên', 'Thêm quản lý, thu ngân hoặc người ghi chỉ số để chia việc.') }));
  };
  let timer: number | undefined;
  q.addEventListener('input', () => { clearTimeout(timer); timer = window.setTimeout(() => void load(), 300); });
  view.replaceChildren(
    pageHead('Nhân viên', 'Tài khoản và quyền của người vận hành khu trọ.', [h('button', { class: 'btn secondary', onclick: () => void permissionMatrix() }, icon('lock', 16), 'Quyền theo vai trò'), ...(can('LANDLORD') ? [h('button', { class: 'btn', onclick: () => add(load) }, icon('plus', 16), 'Thêm nhân viên')] : [])]),
    panel(null, [h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, q)), holder], { flush: true }),
  );
  await load();
}

function roleBoxes(selected: string[]): { box: HTMLElement; get: () => string[] } {
  const boxes = STAFF_ROLES.map(([v, l]) => {
    const c = h('input', { type: 'checkbox', value: v, checked: selected.includes(v) }) as HTMLInputElement;
    return { c, el: h('label', { style: 'display:flex;gap:8px;align-items:center;font-weight:500' }, c, l) };
  });
  return { box: h('div', { class: 'field' }, h('span', { class: 'label' }, 'Vai trò'), ...boxes.map((b) => b.el)), get: () => boxes.filter((b) => b.c.checked).map((b) => b.c.value) };
}

function add(done: () => void): void {
  const d = openDialog('Thêm nhân viên');
  const err = h('div');
  const name = input({ placeholder: 'Họ và tên' });
  const email = input({ type: 'email', placeholder: 'ten@example.com' });
  const phone = input({ type: 'tel', placeholder: '09xx xxx xxx' });
  const roles = roleBoxes(['CASHIER']);
  d.body.append(err, field('Họ tên', name), field('Email', email), field('Số điện thoại', phone), roles.box);
  const ok = h('button', { class: 'btn' }, 'Tạo tài khoản');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      const r = await api.post<{ staff: Staff; temporaryPassword: string }>('/api/staff', { fullName: name.value.trim(), email: email.value.trim() || null, phoneNumber: phone.value.trim() || null, roles: roles.get() });
      d.body.replaceChildren(h('p', null, `Đã tạo tài khoản cho ${r.staff.fullName}. Gửi mật khẩu tạm này cho nhân viên, hệ thống không hiện lại.`),
        h('div', { class: 'qr-box' }, h('span', { class: 'plate lg', style: 'font-size:22px;letter-spacing:0.08em' }, r.temporaryPassword)));
      d.foot.replaceChildren(h('button', { class: 'btn', onclick: () => { d.close(); done(); } }, 'Xong'));
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function edit(s: Staff, done: () => void): void {
  const d = openDialog(`Sửa ${s.fullName}`);
  const err = h('div');
  const name = input({ value: s.fullName });
  const email = input({ type: 'email', value: s.email ?? '' });
  const phone = input({ type: 'tel', value: s.phoneNumber ?? '' });
  const roles = roleBoxes(s.roles);
  d.body.append(err, field('Họ tên', name), field('Email', email), field('Số điện thoại', phone), roles.box);
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      await api.put(`/api/staff/${s.userId}`, { fullName: name.value.trim(), email: email.value.trim() || null, phoneNumber: phone.value.trim() || null, roles: roles.get() });
      d.close(); toast('Đã lưu'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function toggle(s: Staff, done: () => void): Promise<void> {
  const lock = s.status === 'ACTIVE';
  if (!(await confirmDialog(lock ? 'Khóa tài khoản' : 'Mở khóa tài khoản', lock ? `${s.fullName} sẽ không đăng nhập được ngay lập tức.` : `${s.fullName} đăng nhập lại được.`, lock ? 'Khóa' : 'Mở khóa', lock))) return;
  try { await api.patch(`/api/staff/${s.userId}/status`, { status: lock ? 'LOCKED' : 'ACTIVE' }); toast(lock ? 'Đã khóa' : 'Đã mở khóa'); done(); }
  catch (e) { toast(errText(e), 'err'); }
}

export async function activityTable(): Promise<HTMLElement> {
  const r = await api.get<Page<ActivityLog>>('/api/activity-logs?size=50');
  return table<ActivityLog>([
    { key: 't', label: 'Thời điểm', render: (l) => dateTime(l.createdAt) },
    { key: 'u', label: 'Người thực hiện', render: (l) => l.userFullName },
    { key: 'a', label: 'Hành động', render: (l) => h('span', { class: 'plate ghost' }, l.actionType) },
    { key: 'd', label: 'Mô tả', render: (l) => l.description },
    { key: 'i', label: 'IP', render: (l) => l.ipAddress ?? '–' },
  ], r.items, { empty: emptyState('Chưa có nhật ký', 'Mọi thao tác quan trọng sẽ được ghi lại ở đây.') });
}

void select;

/** UC11/UC12: bảng quyền chi tiết của từng vai trò (chủ trọ, quản lý, thu ngân, ghi chỉ số, người thuê). */
async function permissionMatrix(): Promise<void> {
  const d = openDialog('Quyền theo vai trò', { drawer: true });
  d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Đóng'));
  try {
    const [perms, matrix, mine] = await Promise.all([
      api.get<Array<{ permissionCode: string; permissionName: string; module: string }>>('/api/permissions'),
      api.get<Record<string, string[]>>('/api/roles/permissions'),
      api.get<string[]>('/api/auth/permissions'),
    ]);
    const roles = Object.keys(matrix);
    const mark = (ok: boolean) => (ok ? h('span', { class: 'st ok', 'aria-label': 'Có quyền' }, '✓') : h('span', { class: 'muted', 'aria-label': 'Không có quyền' }, '–'));
    d.body.append(
      h('p', { class: 'muted' }, 'Quyền mặc định của từng vai trò. Gán vai trò cho nhân viên ở bảng danh sách; việc chặn thao tác được áp dụng ở máy chủ theo vai trò.'),
      h('p', null, `Bạn đang có ${mine.length} quyền.`),
      table<{ permissionCode: string; permissionName: string; module: string }>([
        { key: 'm', label: 'Phân hệ', render: (x) => x.module },
        { key: 'n', label: 'Quyền', render: (x) => x.permissionName },
        ...roles.map((r) => ({ key: r, label: ROLE_NAMES[r] ?? r, render: (x: { permissionCode: string }) => mark(matrix[r].includes(x.permissionCode)) })),
      ], perms, { empty: emptyState('Chưa có danh mục quyền', 'Máy chủ chưa nạp danh mục quyền.') }));
  } catch (e) { d.body.append(formError(errText(e))); }
}
