import { requestBooking } from './bookings';
import { busy, h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, clearMustChangePassword, errText, login, logout } from '../lib/api';
import { field, formError, input, openDialog, toast } from '../ui/kit';
import { logo } from '../ui/shell';

export function renderLogin(root: HTMLElement, onDone: () => void): void {
  const id = input({ type: 'text', autocomplete: 'username', placeholder: 'email hoặc số điện thoại', required: true });
  const pw = input({ type: 'password', autocomplete: 'current-password', placeholder: '••••••••', required: true });
  const err = h('div');
  const btn = h('button', { class: 'btn lg block', type: 'submit' }, 'Đăng nhập', icon('arrowRight', 18));
  const form = h('form', { class: 'login-card', novalidate: true },
    h('div', { class: 'login-brand-m' }, logo(40), h('b', null, 'Stayly')),
    h('div', null, h('h1', null, 'Đăng nhập'), h('p', { class: 'muted', style: 'margin-top:6px' }, 'Dùng email hoặc số điện thoại đã đăng ký với khu trọ.')),
    err,
    field('Email hoặc số điện thoại', id),
    field('Mật khẩu', pw),
    btn,
    h('button', { type: 'button', class: 'btn secondary', onclick: () => requestBooking(true) }, 'Gửi yêu cầu thuê phòng'),
    h('button', { type: 'button', class: 'link-btn', style: 'align-self:flex-start', onclick: () => forgot() }, 'Quên mật khẩu?'),
    h('button', { type: 'button', class: 'link-btn', style: 'align-self:flex-start', onclick: () => register() }, 'Đăng ký tài khoản chủ trọ'),
  );
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.replaceChildren();
    id.removeAttribute('aria-invalid');
    pw.removeAttribute('aria-invalid');
    if (!id.value.trim() || !pw.value) {
      (id.value.trim() ? pw : id).setAttribute('aria-invalid', 'true');
      err.appendChild(formError('Nhập đủ email hoặc số điện thoại và mật khẩu.'));
      return;
    }
    btn.classList.add('loading');
    btn.setAttribute('disabled', '');
    try {
      await login(id.value.trim(), pw.value);
      onDone();
    } catch (ex) {
      err.appendChild(formError(errText(ex)));
      pw.value = '';
      pw.focus();
    } finally {
      btn.classList.remove('loading');
      btn.removeAttribute('disabled');
    }
  });

  /** UC1/UC2: chủ trọ tự đăng ký, nhập mã xác thực gửi qua email để kích hoạt. */
  function register(): void {
    const dialog = openDialog('Đăng ký tài khoản chủ trọ');
    const error = h('div', { role: 'alert' });
    const name = input({ maxlength: '100', autocomplete: 'name', 'aria-label': 'Họ và tên' });
    const email = input({ type: 'email', autocomplete: 'email', 'aria-label': 'Email' });
    const phone = input({ type: 'tel', autocomplete: 'tel', placeholder: 'Không bắt buộc', 'aria-label': 'Số điện thoại' });
    const pass = input({ type: 'password', autocomplete: 'new-password', 'aria-label': 'Mật khẩu' });
    const body = h('div', { class: 'stack' }, error, field('Họ và tên', name), field('Email (nhận mã xác thực)', email), field('Số điện thoại', phone), field('Mật khẩu', pass, 'Từ 8 ký tự, có cả chữ và số'));
    dialog.body.append(body);
    const send = h('button', { class: 'btn' }, 'Đăng ký');
    dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Hủy'), send);
    send.addEventListener('click', () => busy(send, async () => {
      error.replaceChildren();
      if (!name.value.trim() || !email.value.trim() || !pass.value) { error.replaceChildren(formError('Nhập họ tên, email và mật khẩu.')); return; }
      try {
        await api.post('/api/auth/register', { fullName: name.value.trim(), email: email.value.trim(), phoneNumber: phone.value.trim() || null, password: pass.value });
      } catch (e) { error.replaceChildren(formError(errText(e))); return; }
      verify(email.value.trim());
      dialog.close();
    }));
  }

  function verify(mail: string): void {
    const dialog = openDialog('Xác thực email');
    const error = h('div', { role: 'alert' });
    const code = input({ inputmode: 'numeric', autocomplete: 'one-time-code', maxlength: '10', 'aria-label': 'Mã xác thực' });
    dialog.body.append(error, h('p', null, `Mã xác thực đã gửi tới ${mail}. Nhập mã để kích hoạt tài khoản.`), field('Mã xác thực', code));
    const ok = h('button', { class: 'btn' }, 'Kích hoạt tài khoản');
    const resend = h('button', { class: 'btn secondary' }, 'Gửi lại mã');
    dialog.foot.append(resend, ok);
    resend.addEventListener('click', () => busy(resend, async () => {
      try { await api.post('/api/auth/resend-verification', { identifier: mail }); toast('Đã gửi lại mã nếu tài khoản đang chờ xác thực'); } catch (e) { error.replaceChildren(formError(errText(e))); }
    }));
    ok.addEventListener('click', () => busy(ok, async () => {
      error.replaceChildren();
      if (!code.value.trim()) { error.replaceChildren(formError('Nhập mã xác thực.')); return; }
      try { await api.post('/api/auth/verify-email', { email: mail, code: code.value.trim() }); dialog.close(); id.value = mail; toast('Đã kích hoạt tài khoản, hãy đăng nhập'); }
      catch (e) { error.replaceChildren(formError(errText(e))); }
    }));
  }

  function forgot(): void {
    const dialog = openDialog('Đặt lại mật khẩu');
    const identifier = input({ value: id.value.trim(), autocomplete: 'username', required: true });
    const code = input({ autocomplete: 'one-time-code', inputmode: 'numeric', required: true });
    const password = input({ type: 'password', autocomplete: 'new-password', required: true });
    const confirmation = input({ type: 'password', autocomplete: 'new-password', required: true });
    const message = h('div', { role: 'status' });
    const send = h('button', { class: 'btn secondary' }, 'Gửi mã xác nhận');
    const save = h('button', { class: 'btn' }, 'Đặt lại mật khẩu');
    dialog.body.append(message, field('Email hoặc số điện thoại', identifier), send,
      field('Mã xác nhận', code), field('Mật khẩu mới', password, 'Từ 8 ký tự, có cả chữ và số'),
      field('Nhập lại mật khẩu mới', confirmation));
    dialog.foot.append(h('button', { class: 'btn secondary', onclick: dialog.close }, 'Hủy'), save);
    send.addEventListener('click', () => busy(send, async () => {
      if (!identifier.value.trim()) {
        message.replaceChildren(formError('Nhập email hoặc số điện thoại.'));
        return;
      }
      try {
        await api.post('/api/auth/forgot-password', { identifier: identifier.value.trim() });
        message.textContent = 'Nếu tài khoản tồn tại, mã xác nhận đã được gửi.';
        code.focus();
      } catch (e) { message.replaceChildren(formError(errText(e))); }
    }));
    save.addEventListener('click', () => busy(save, async () => {
      if (!identifier.value.trim() || !code.value.trim() || !password.value || password.value !== confirmation.value) {
        message.replaceChildren(formError('Nhập tài khoản, mã xác nhận và hai mật khẩu mới giống nhau.'));
        return;
      }
      try {
        await api.post('/api/auth/reset-password', { identifier: identifier.value.trim(), code: code.value.trim(), newPassword: password.value });
        id.value = identifier.value.trim();
        pw.value = '';
        dialog.close();
        toast('Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.');
      } catch (e) { message.replaceChildren(formError(errText(e))); }
    }));
  }

  root.replaceChildren(
    h('div', { class: 'login' },
      h('div', { class: 'login-side' },
        h('div', { style: 'display:flex;align-items:center;gap:12px' }, logo(44), h('b', { style: 'font-size:26px;color:#fff;letter-spacing:-0.02em' }, 'Stayly')),
        h('div', null,
          h('p', { class: 'login-headline' }, 'Chốt tiền tháng không sót một phòng.'),
          h('p', null, 'Từ chỉ số điện nước đến hóa đơn và thu tiền, làm lần lượt từng bước cho từng phòng.'),
        ),
        h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' }, ...['P101', 'P102', 'P103', 'P201', 'P202'].map((c) => h('span', { class: 'plate' }, c))),
      ),
      h('main', { class: 'login-main' }, form),
    ),
  );
  id.focus();
}

let forceOpen = false;
/** Tài khoản đang dùng mật khẩu tạm: hộp thoại không đóng được cho tới khi đổi mật khẩu (hoặc đăng xuất). */
export function forcePasswordChange(): void {
  if (forceOpen) return;
  forceOpen = true;
  const d = openDialog('Đổi mật khẩu trước khi làm việc', { locked: true });
  const err = h('div');
  const cur = input({ type: 'password', autocomplete: 'current-password' });
  const nw = input({ type: 'password', autocomplete: 'new-password' });
  d.body.append(err, h('p', null, 'Bạn đang dùng mật khẩu tạm do chủ trọ cấp. Hãy đặt mật khẩu của riêng bạn.'),
    field('Mật khẩu tạm hiện tại', cur), field('Mật khẩu mới', nw, 'Từ 8 ký tự, có cả chữ và số'));
  const ok = h('button', { class: 'btn' }, 'Đổi mật khẩu');
  const out = h('button', { class: 'btn secondary' }, 'Đăng xuất');
  out.addEventListener('click', () => { d.close(); forceOpen = false; logout(); });
  d.foot.append(out, ok);
  ok.addEventListener('click', async () => {
    err.replaceChildren();
    if (!cur.value || !nw.value) { err.append(formError('Nhập mật khẩu tạm và mật khẩu mới.')); return; }
    ok.setAttribute('disabled', '');
    try {
      await api.post('/api/auth/password', { currentPassword: cur.value, newPassword: nw.value });
      clearMustChangePassword();
      d.close();
      forceOpen = false;
      toast('Đã đổi mật khẩu');
    } catch (e) {
      err.append(formError(errText(e)));
    } finally {
      ok.removeAttribute('disabled');
    }
  });
  cur.focus();
}
