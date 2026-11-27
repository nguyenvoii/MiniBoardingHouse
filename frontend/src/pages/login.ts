import { busy, h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, clearMustChangePassword, errText, login, loginWithQr, logout, viaQrSession } from '../lib/api';
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
    h('button', { type: 'button', class: 'link-btn', style: 'align-self:flex-start', onclick: () => forgot() }, 'Quên mật khẩu?'),
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

  function forgot(): void {
    // Hai bước: 1) gửi và nhập mã, mã đúng mới sang 2) đặt mật khẩu mới.
    const dialog = openDialog('Đặt lại mật khẩu');
    const identifier = input({ value: id.value.trim(), autocomplete: 'username', required: true });
    const code = input({ autocomplete: 'one-time-code', inputmode: 'numeric', required: true });
    const message = h('div', { role: 'status' });
    const cancel = h('button', { class: 'btn secondary', onclick: dialog.close }, 'Hủy');
    let verified = '';

    function stepCode(): void {
      const send = h('button', { class: 'btn secondary' }, 'Gửi mã xác nhận');
      const next = h('button', { class: 'btn' }, 'Tiếp tục');
      message.replaceChildren();
      dialog.body.replaceChildren(message, field('Email hoặc số điện thoại', identifier), send,
        field('Mã xác nhận', code, 'Mã 6 số, có hiệu lực 10 phút. Nhập sai tối đa 5 lần.'));
      dialog.foot.replaceChildren(cancel, next);
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
      next.addEventListener('click', () => busy(next, async () => {
        if (!identifier.value.trim() || !code.value.trim()) {
          message.replaceChildren(formError('Nhập tài khoản và mã xác nhận.'));
          return;
        }
        try {
          await api.post('/api/auth/verify-reset-code', { identifier: identifier.value.trim(), code: code.value.trim() });
          verified = code.value.trim();
          stepPassword();
        } catch (e) { message.replaceChildren(formError(errText(e))); }
      }));
    }

    function stepPassword(): void {
      const password = input({ type: 'password', autocomplete: 'new-password', required: true });
      const confirmation = input({ type: 'password', autocomplete: 'new-password', required: true });
      const save = h('button', { class: 'btn' }, 'Đặt lại mật khẩu');
      message.replaceChildren(h('p', { class: 'muted' }, 'Mã đúng. Đặt mật khẩu mới cho tài khoản của bạn.'));
      dialog.body.replaceChildren(message, field('Mật khẩu mới', password, 'Từ 8 ký tự, có cả chữ và số'),
        field('Nhập lại mật khẩu mới', confirmation));
      dialog.foot.replaceChildren(cancel, save);
      password.focus();
      save.addEventListener('click', () => busy(save, async () => {
        if (!password.value || password.value !== confirmation.value) {
          message.replaceChildren(formError('Nhập hai mật khẩu mới giống nhau.'));
          return;
        }
        try {
          await api.post('/api/auth/reset-password', { identifier: identifier.value.trim(), code: verified, newPassword: password.value });
          id.value = identifier.value.trim();
          pw.value = '';
          dialog.close();
          toast('Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.');
        } catch (e) { message.replaceChildren(formError(errText(e))); }
      }));
    }

    stepCode();
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

  // Khách quét QR lúc được cấp tài khoản: link có ?qr=<mã>, đăng nhập luôn không cần gõ mật khẩu tạm
  const qr = new URLSearchParams(location.search).get('qr');
  if (qr) {
    history.replaceState(null, '', location.pathname + location.hash); // bỏ mã khỏi thanh địa chỉ và lịch sử
    err.replaceChildren(h('p', { class: 'muted' }, 'Đang đăng nhập bằng mã QR…'));
    loginWithQr(qr).then(onDone, (ex) => { err.replaceChildren(formError(errText(ex))); });
  }
}

let forceOpen = false;
/** Tài khoản đang dùng mật khẩu tạm: hộp thoại không đóng được cho tới khi đổi mật khẩu (hoặc đăng xuất). */
export function forcePasswordChange(): void {
  if (forceOpen) return;
  forceOpen = true;
  const qr = viaQrSession(); // vào bằng mã QR: chưa biết mật khẩu tạm, đặt thẳng mật khẩu mới
  const d = openDialog(qr ? 'Đặt mật khẩu mới' : 'Đổi mật khẩu trước khi làm việc', { locked: true });
  const err = h('div');
  const cur = input({ type: 'password', autocomplete: 'current-password' });
  const nw = input({ type: 'password', autocomplete: 'new-password' });
  const again = input({ type: 'password', autocomplete: 'new-password' });
  d.body.append(err, h('p', null, qr ? 'Bạn vừa vào bằng mã QR. Hãy đặt mật khẩu của riêng bạn để đăng nhập những lần sau.'
      : 'Bạn đang dùng mật khẩu tạm do chủ trọ cấp. Hãy đặt mật khẩu của riêng bạn.'),
    ...(qr ? [] : [field('Mật khẩu tạm hiện tại', cur)]), field('Mật khẩu mới', nw, 'Từ 8 ký tự, có cả chữ và số'),
    ...(qr ? [field('Nhập lại mật khẩu mới', again)] : []));
  const ok = h('button', { class: 'btn' }, 'Đổi mật khẩu');
  const out = h('button', { class: 'btn secondary' }, 'Đăng xuất');
  out.addEventListener('click', () => { d.close(); forceOpen = false; logout(); });
  d.foot.append(out, ok);
  ok.addEventListener('click', async () => {
    err.replaceChildren();
    if (qr ? !nw.value : !cur.value || !nw.value) { err.append(formError(qr ? 'Nhập mật khẩu mới.' : 'Nhập mật khẩu tạm và mật khẩu mới.')); return; }
    if (qr && nw.value !== again.value) { err.append(formError('Hai lần nhập mật khẩu mới chưa giống nhau.')); return; }
    ok.setAttribute('disabled', '');
    try {
      if (qr) await api.post('/api/auth/qr-set-password', { newPassword: nw.value });
      else await api.post('/api/auth/password', { currentPassword: cur.value, newPassword: nw.value });
      clearMustChangePassword();
      d.close();
      forceOpen = false;
      toast(qr ? 'Đã đặt mật khẩu mới' : 'Đã đổi mật khẩu');
    } catch (e) {
      err.append(formError(errText(e)));
    } finally {
      ok.removeAttribute('disabled');
    }
  });
  (qr ? nw : cur).focus();
}
