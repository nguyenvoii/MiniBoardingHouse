import { h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, login } from '../lib/api';
import { field, formError, input, toast } from '../ui/kit';
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

  async function forgot(): Promise<void> {
    const v = id.value.trim();
    if (!v) {
      err.replaceChildren(formError('Nhập email hoặc số điện thoại ở ô trên rồi bấm Quên mật khẩu.'));
      id.focus();
      return;
    }
    try {
      await api.post('/api/auth/forgot-password', { identifier: v });
      toast('Nếu tài khoản tồn tại, mã xác nhận đã được gửi.');
    } catch (ex) {
      toast(errText(ex), 'err');
    }
  }

  root.replaceChildren(
    h('div', { class: 'login' },
      h('div', { class: 'login-side' },
        h('div', { style: 'display:flex;align-items:center;gap:12px' }, logo(44), h('b', { style: 'font-size:26px;color:#fff;letter-spacing:-0.02em' }, 'Stayly')),
        h('div', null,
          h('h2', null, 'Chốt tiền tháng không sót một phòng.'),
          h('p', null, 'Từ chỉ số điện nước đến hóa đơn và thu tiền: mỗi tháng là một tuyến, mỗi phòng là một trạm.'),
        ),
        h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' }, ...['P101', 'P102', 'P103', 'P201', 'P202'].map((c) => h('span', { class: 'plate' }, c))),
      ),
      h('div', { class: 'login-main' }, form),
    ),
  );
  id.focus();
}
