import { h, busy } from '../lib/dom';
import { api, errText, loadPrivateImage, loadSession, renewSession, setMyProfile } from '../lib/api';
import { dl, field, formError, input, pageHead, panel, toast } from '../ui/kit';
import { ROLE_NAMES } from '../ui/shell';

interface Profile { fullName: string; email: string | null; phoneNumber: string | null; avatarUrl: string | null }

export async function profilePage(view: HTMLElement): Promise<void> {
  const s = loadSession()!;
  const p = await api.get<Profile>('/api/auth/profile');
  const name = input({ value: p.fullName });
  const phone = input({ type: 'tel', value: p.phoneNumber ?? '' });
  const err1 = h('div');
  const save = h('button', { class: 'btn' }, 'Lưu hồ sơ');
  save.addEventListener('click', () => busy(save, async () => {
    try { const r = await api.put<Profile>('/api/auth/profile', { fullName: name.value.trim(), phoneNumber: phone.value.trim() || null }); setMyProfile({ fullName: r?.fullName ?? name.value.trim() }); err1.replaceChildren(); toast('Đã lưu hồ sơ'); }
    catch (e) { err1.replaceChildren(formError(errText(e))); }
  }));

  const avatar = input({ type: 'file', accept: 'image/png,image/jpeg,image/webp' });
  const upload = h('button', { class: 'btn secondary' }, 'Lưu ảnh đại diện');
  upload.addEventListener('click', () => busy(upload, async () => {
    const file = avatar.files?.[0];
    if (!file) { err1.replaceChildren(formError('Chọn ảnh trước khi lưu.')); return; }
    try {
      const r = await api.upload<Profile>('/api/auth/avatar', file, 'PUT');
      setMyProfile({ avatarUrl: r.avatarUrl });
      toast('Đã lưu ảnh đại diện');
      await profilePage(view);
    } catch (e) { err1.replaceChildren(formError(errText(e))); }
  }));

  const cur = input({ type: 'password', autocomplete: 'current-password' });
  const nw = input({ type: 'password', autocomplete: 'new-password' });
  const err2 = h('div');
  const chg = h('button', { class: 'btn' }, 'Đổi mật khẩu');
  chg.addEventListener('click', () => busy(chg, async () => {
    try {
      const { accessToken } = await api.post<{ accessToken: string }>('/api/auth/password', { currentPassword: cur.value, newPassword: nw.value });
      renewSession(accessToken);
      err2.replaceChildren(); cur.value = nw.value = ''; toast('Đã đổi mật khẩu');
    }
    catch (e) { err2.replaceChildren(formError(errText(e))); }
  }));

  const avatarPreview = p.avatarUrl
    ? h('img', { alt: 'Ảnh đại diện hiện tại', width: 64, height: 64, style: 'object-fit:cover;border-radius:50%' })
    : null;
  if (avatarPreview && p.avatarUrl) loadPrivateImage(avatarPreview, p.avatarUrl);
  view.replaceChildren(
    pageHead('Tài khoản của tôi'),
    h('div', { class: 'grid-eq' },
      panel('Hồ sơ', h('div', { class: 'panel-body stack' }, err1, dl([['Email', p.email ?? '–'], ['Vai trò', s.roles.map((r) => ROLE_NAMES[r] ?? r).join(', ')]]),
        avatarPreview,
        field('Ảnh đại diện', avatar, 'Tối đa 2 MB'), h('div', { class: 'form-actions' }, upload),
        field('Họ tên', name), field('Số điện thoại', phone), h('div', { class: 'form-actions' }, save))),
      panel('Xác thực số điện thoại', phoneVerifyBox(p.phoneNumber)),
      panel('Đổi mật khẩu', h('div', { class: 'panel-body stack' }, err2, field('Mật khẩu hiện tại', cur), field('Mật khẩu mới', nw, 'Từ 8 ký tự, có cả chữ và số'), h('div', { class: 'form-actions' }, chg))),
    ),
  );
}

/** UC2: xác thực số điện thoại bằng mã OTP gửi qua SMS (cần cấu hình nhà cung cấp SMS). */
function phoneVerifyBox(current: string | null): HTMLElement {
  const err = h('div');
  const phone = input({ type: 'tel', value: current ?? '', 'aria-label': 'Số điện thoại cần xác thực' });
  const code = input({ inputmode: 'numeric', maxlength: '10', placeholder: 'Mã gồm 6 số', 'aria-label': 'Mã SMS' });
  const send = h('button', { class: 'btn secondary' }, 'Gửi mã SMS');
  const ok = h('button', { class: 'btn' }, 'Xác thực');
  send.addEventListener('click', () => busy(send, async () => {
    err.replaceChildren();
    if (!phone.value.trim()) { err.replaceChildren(formError('Nhập số điện thoại.')); return; }
    try { await api.post('/api/auth/send-phone-verification', { identifier: phone.value.trim() }); toast('Đã gửi mã SMS'); } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!phone.value.trim() || !code.value.trim()) { err.replaceChildren(formError('Nhập số điện thoại và mã SMS.')); return; }
    try { await api.post('/api/auth/verify-phone', { phone: phone.value.trim(), code: code.value.trim() }); toast('Đã xác thực số điện thoại'); code.value = ''; } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
  return h('div', { class: 'panel-body stack' }, err, field('Số điện thoại', phone, 'Mã gửi qua SMS; cần chủ hệ thống cấu hình nhà cung cấp SMS'), h('div', { class: 'form-actions' }, send), field('Mã SMS', code), h('div', { class: 'form-actions' }, ok));
}
