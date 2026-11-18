import { h, busy } from '../lib/dom';
import { api, clearMustChangePassword, errText, loadSession } from '../lib/api';
import { dl, field, formError, input, pageHead, panel, toast } from '../ui/kit';
import { ROLE_NAMES } from '../ui/shell';

interface Profile { fullName: string; email: string | null; phoneNumber: string | null }

export async function profilePage(view: HTMLElement): Promise<void> {
  const s = loadSession()!;
  const p = await api.get<Profile>('/api/auth/profile');
  const name = input({ value: p.fullName });
  const phone = input({ type: 'tel', value: p.phoneNumber ?? '' });
  const err1 = h('div');
  const save = h('button', { class: 'btn' }, 'Lưu hồ sơ');
  save.addEventListener('click', () => busy(save, async () => {
    try { await api.put('/api/auth/profile', { fullName: name.value.trim(), phoneNumber: phone.value.trim() || null }); err1.replaceChildren(); toast('Đã lưu hồ sơ'); }
    catch (e) { err1.replaceChildren(formError(errText(e))); }
  }));

  const cur = input({ type: 'password', autocomplete: 'current-password' });
  const nw = input({ type: 'password', autocomplete: 'new-password' });
  const err2 = h('div');
  const chg = h('button', { class: 'btn' }, 'Đổi mật khẩu');
  chg.addEventListener('click', () => busy(chg, async () => {
    try { await api.post('/api/auth/password', { currentPassword: cur.value, newPassword: nw.value }); err2.replaceChildren(); cur.value = nw.value = ''; clearMustChangePassword(); toast('Đã đổi mật khẩu'); }
    catch (e) { err2.replaceChildren(formError(errText(e))); }
  }));

  view.replaceChildren(
    pageHead('Tài khoản của tôi'),
    h('div', { class: 'grid-eq' },
      panel('Hồ sơ', h('div', { class: 'panel-body stack' }, err1, dl([['Email', p.email ?? '–'], ['Vai trò', s.roles.map((r) => ROLE_NAMES[r] ?? r).join(', ')]]),
        field('Họ tên', name), field('Số điện thoại', phone), h('div', { class: 'form-actions' }, save))),
      panel('Đổi mật khẩu', h('div', { class: 'panel-body stack' }, err2, field('Mật khẩu hiện tại', cur), field('Mật khẩu mới', nw, 'Từ 8 ký tự, có cả chữ và số'), h('div', { class: 'form-actions' }, chg))),
    ),
  );
}
