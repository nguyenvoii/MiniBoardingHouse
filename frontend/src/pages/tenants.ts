import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText } from '../lib/api';
import { date } from '../lib/format';
import { loadLive, LiveTenant } from '../data/live';
import { vehiclesBox } from '../ui/occupancy';
import { confirmDialog, dl, field, formError, input, openDialog, pageHead, panel, plate, select, statusBadge, table, toast } from '../ui/kit';

interface StayHistory { roomCode: string; startDate: string; endDate: string | null; checkoutReason: string | null }
interface AccountInfo { exists: boolean; userId: number | null; phoneNumber: string | null; status: string | null }
type Accounts = Map<number, AccountInfo | null>;

export async function tenantsPage(view: HTMLElement): Promise<void> {
  const { tenants } = await loadLive();
  // Chỉ tải trạng thái tài khoản khi mở hồ sơ; danh sách không tạo N+1 request.
  const accounts: Accounts = new Map();

  const holder = h('div');
  const q = input({ type: 'search', placeholder: 'Tên, số điện thoại, CCCD hoặc phòng', 'aria-label': 'Tìm khách hàng' });
  const kind = select([['', 'Tất cả khách'], ['renting', 'Đang thuê phòng'], ['none', 'Chưa thuê (khách tiềm năng)']], '', { 'aria-label': 'Lọc theo tình trạng thuê' });
  const paint = () => {
    const k = q.value.trim().toLowerCase();
    const rows = tenants.filter((t) => (!k || `${t.name} ${t.phone} ${t.idNumber} ${t.roomCode ?? ''}`.toLowerCase().includes(k))
      && (kind.value === '' || (kind.value === 'renting' ? !!t.roomCode : !t.roomCode)));
    holder.replaceChildren(table<LiveTenant>([
      { key: 'n', label: 'Họ tên', render: (t) => h('span', { class: 'cell-main' }, t.name), sort: (a, b) => a.name.localeCompare(b.name, 'vi') },
      { key: 'r', label: 'Tình trạng', render: (t) => (t.roomCode ? h('span', { style: 'display:inline-flex;gap:8px;align-items:center' }, plate(t.roomCode), h('span', { class: 'st ok' }, 'Đang thuê')) : h('span', { class: 'st wait' }, 'Chưa thuê')) },
      { key: 'p', label: 'Điện thoại', render: (t) => t.phone },
      { key: 'j', label: 'Nghề nghiệp', render: (t) => t.job },
      { key: 's', label: 'Ở từ', render: (t) => date(t.since), sort: (a, b) => (a.since ?? '').localeCompare(b.since ?? '') },
      { key: 'a', label: 'Tài khoản đăng nhập', render: (t) => accountBadge(accounts.get(t.tenantId)) },
    ], rows, { onRow: (t) => open(t, accounts, () => void tenantsPage(view)) }));
  };
  q.addEventListener('input', paint);
  kind.addEventListener('change', paint);
  paint();
  view.replaceChildren(
    pageHead('Khách hàng', 'Hồ sơ khách: người đã đến hỏi thuê, đang thuê hoặc đã trả phòng. Khách chỉ trở thành người thuê khi có hợp đồng.', [h('button', { class: 'btn', onclick: () => addTenant(() => void tenantsPage(view)) }, icon('plus', 16), 'Thêm khách hàng')]),
    panel(null, [h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, q), kind), holder], { flush: true }),
  );
}

function accountBadge(a: AccountInfo | null | undefined): HTMLElement {
  if (a === null || a === undefined) return h('span', { class: 'st off' }, icon('info', 14), 'Chưa đồng bộ hồ sơ');
  if (!a.exists) return h('span', { class: 'st wait' }, icon('clock', 14), 'Chưa cấp');
  return statusBadge(a.status === 'LOCKED' ? 'LOCKED' : 'ACTIVE');
}

function open(t: LiveTenant, accounts: Accounts, refresh: () => void): void {
  const d = openDialog(t.name, { drawer: true });
  const history = h('div', null, 'Đang tải lịch sử thuê…');
  void loadStayHistory(t.tenantId, history);
  const acct = h('div', { class: 'stack', style: 'gap:12px' });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, t.roomCode ? plate(t.roomCode, 'lg') : null),
    dl([['Điện thoại', t.phone], ['Email', t.email ?? '–'], ['CCCD', t.idNumber], ['Nghề nghiệp', t.job], ['Ở từ', date(t.since)], ['Hợp đồng', t.contract ? t.contract.contractCode : 'Chưa có']]),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Xe đăng ký (tính tiền gửi xe)'), vehiclesBox(t.tenantId)),
    h('section', null, h('h3', null, 'Lịch sử thuê phòng'), history),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Tài khoản đăng nhập'), acct),
  );
  const paint = (a: AccountInfo | null | undefined) => {
    acct.replaceChildren();
    if (a === undefined) {
      acct.append(h('p', { class: 'muted' }, 'Đang kiểm tra tài khoản…'));
      return;
    }
    if (a === null) {
      acct.append(h('p', { class: 'muted' }, 'Chưa lấy được tình trạng tài khoản của khách này. Hãy đóng cửa sổ và thử lại.'));
      return;
    }
    if (!a.exists) {
      const go = h('button', { class: 'btn' }, icon('key', 16), 'Cấp tài khoản đăng nhập');
      go.addEventListener('click', () => busy(go, async () => {
        try {
          const r = await api.post<{ userId: number; phoneNumber: string; temporaryPassword: string }>(`/api/tenants/${t.tenantId}/account`);
          accounts.set(t.tenantId, { exists: true, userId: r.userId, phoneNumber: r.phoneNumber, status: 'ACTIVE' });
          showPassword(r.phoneNumber, r.temporaryPassword);
          paint(accounts.get(t.tenantId));
          refresh();
        } catch (e) { toast(errText(e), 'err'); }
      }));
      acct.append(h('p', { class: 'muted' }, `Khách chưa có tài khoản. Cấp tài khoản để họ đăng nhập bằng số điện thoại ${t.phone}, xem hóa đơn, trả tiền bằng QR và ký hợp đồng.`), go);
      return;
    }
    const locked = a.status === 'LOCKED';
    const toggle = h('button', { class: `btn ${locked ? 'secondary' : 'danger'}` }, icon('lock', 16), locked ? 'Mở khóa tài khoản' : 'Khóa tài khoản');
    toggle.addEventListener('click', async () => {
      if (!(await confirmDialog(locked ? 'Mở khóa tài khoản' : 'Khóa tài khoản', locked ? `${t.name} đăng nhập lại được.` : `${t.name} không đăng nhập được ngay lập tức, kể cả khi đang mở ứng dụng.`, locked ? 'Mở khóa' : 'Khóa', !locked))) return;
      try {
        await api.patch(`/api/tenants/${t.tenantId}/account/status`, { status: locked ? 'ACTIVE' : 'LOCKED' });
        accounts.set(t.tenantId, { ...a, status: locked ? 'ACTIVE' : 'LOCKED' });
        toast(locked ? 'Đã mở khóa' : 'Đã khóa');
        paint(accounts.get(t.tenantId));
        refresh();
      } catch (e) { toast(errText(e), 'err'); }
    });
    const reset = h('button', { class: 'btn secondary' }, icon('key', 16), 'Cấp lại mật khẩu tạm');
    reset.addEventListener('click', async () => {
      if (!(await confirmDialog('Cấp lại mật khẩu', `Mật khẩu cũ của ${t.name} mất hiệu lực ngay. Bạn sẽ đưa mật khẩu mới cho khách.`, 'Cấp lại'))) return;
      try {
        const r = await api.post<{ phoneNumber: string; temporaryPassword: string }>(`/api/tenants/${t.tenantId}/account/reset-password`);
        showPassword(r.phoneNumber, r.temporaryPassword);
      } catch (e) { toast(errText(e), 'err'); }
    });
    acct.append(h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, statusBadge(locked ? 'LOCKED' : 'ACTIVE'), h('span', { class: 'muted' }, `Đăng nhập bằng số điện thoại ${a.phoneNumber ?? t.phone}`)),
      h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' }, reset, toggle));
  };
  paint(accounts.get(t.tenantId));
  if (!accounts.has(t.tenantId)) {
    void api.get<AccountInfo>(`/api/tenants/${t.tenantId}/account`)
      .then((a) => { accounts.set(t.tenantId, a); paint(a); })
      .catch(() => { accounts.set(t.tenantId, null); paint(null); });
  }
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'),
    h('button', { class: 'btn secondary', onclick: () => { d.close(); editTenant(t, refresh); } }, icon('edit', 16), 'Sửa hồ sơ'));
  if (!t.roomCode) d.foot.append(h('a', { class: 'btn', href: `#/checkin?tenant=${t.tenantId}`, onclick: d.close }, icon('key', 16), 'Cho khách thuê phòng'));
}

async function loadStayHistory(tenantId: number, target: HTMLElement): Promise<void> {
  try {
    const rows = await api.get<StayHistory[]>('/api/chi/tenants/' + tenantId + '/stay-history');
    if (!rows.length) {
      target.replaceChildren(h('p', { class: 'muted' }, 'Chưa có lịch sử thuê phòng.'));
      return;
    }
    target.replaceChildren(table<StayHistory>([
      { key: 'room', label: 'Phòng', render: (r) => r.roomCode },
      { key: 'start', label: 'Từ ngày', render: (r) => date(r.startDate) },
      { key: 'end', label: 'Đến ngày', render: (r) => date(r.endDate) },
      { key: 'reason', label: 'Lý do trả phòng', render: (r) => r.checkoutReason ?? '–' },
    ], rows));
  } catch (e) { target.replaceChildren(formError(errText(e))); }
}

function editTenant(t: LiveTenant, done: () => void): void {
  const d = openDialog(`Sửa hồ sơ ${t.name}`, { width: 560 });
  const err = h('div');
  const name = input({ value: t.name, maxlength: '100' });
  const phone = input({ type: 'tel', value: t.phone, maxlength: '20' });
  const cccd = input({ value: t.idNumber, maxlength: '20' });
  const email = input({ type: 'email', value: t.email ?? '', placeholder: 'Không bắt buộc' });
  const job = input({ value: t.job === '–' ? '' : t.job, maxlength: '100', placeholder: 'Không bắt buộc' });
  d.body.append(err, h('div', { class: 'form-grid' }, field('Họ và tên', name), field('Số điện thoại', phone, 'Đổi số điện thoại thì khách đăng nhập bằng số mới'),
    field('Số CCCD', cccd), field('Email', email), field('Nghề nghiệp', job)));
  const ok = h('button', { class: 'btn' }, 'Lưu hồ sơ');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!name.value.trim() || !phone.value.trim() || !cccd.value.trim()) { err.replaceChildren(formError('Họ tên, số điện thoại và số CCCD không được để trống.')); return; }
    try {
      // email, nghề nghiệp để trống thì gửi chuỗi rỗng (xóa); backend giữ nguyên trường không gửi
      await api.put(`/api/chi/tenants/${t.tenantId}`, { fullName: name.value.trim(), phoneNumber: phone.value.trim(), identityNumber: cccd.value.trim(), email: email.value.trim(), occupation: job.value.trim() });
      d.close(); toast('Đã lưu hồ sơ khách'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function showPassword(phone: string, password: string): void {
  const d = openDialog('Đã cấp tài khoản', { width: 440 });
  d.body.append(
    h('p', null, 'Gửi thông tin này cho khách. Mật khẩu tạm chỉ hiện một lần, khách nên đổi sau khi đăng nhập.'),
    dl([['Đăng nhập bằng', phone], ['Mật khẩu tạm', h('span', { class: 'plate lg', style: 'font-size:20px;letter-spacing:0.08em' }, password)]]),
  );
  const copy = h('button', { class: 'btn secondary' }, icon('check', 16), 'Chép mật khẩu');
  copy.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(password); toast('Đã chép mật khẩu'); } catch { toast('Không chép được, hãy chép tay', 'err'); }
  });
  d.foot.append(copy, h('button', { class: 'btn', onclick: d.close }, 'Xong'));
}

export function addTenant(done: (created?: { tenantId: number }) => void): void {
  const d = openDialog('Thêm khách hàng', { width: 560 });
  const err = h('div');
  const name = input({ placeholder: 'Họ và tên', maxlength: '100' });
  const phone = input({ type: 'tel', placeholder: '09xx xxx xxx', maxlength: '20' });
  const cccd = input({ placeholder: 'Số CCCD', maxlength: '20' });
  const email = input({ type: 'email', placeholder: 'Không bắt buộc' });
  const job = input({ placeholder: 'Không bắt buộc', maxlength: '100' });
  d.body.append(err, h('div', { class: 'form-grid' }, field('Họ và tên', name), field('Số điện thoại', phone, 'Dùng làm tài khoản đăng nhập của khách'),
    field('Số CCCD', cccd), field('Email', email), field('Nghề nghiệp', job)));
  const ok = h('button', { class: 'btn' }, 'Thêm khách hàng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!name.value.trim() || !phone.value.trim() || !cccd.value.trim()) { err.replaceChildren(formError('Nhập đủ họ tên, số điện thoại và số CCCD.')); return; }
    try {
      const created = await api.post<{ tenantId: number }>('/api/chi/tenants', {
        fullName: name.value.trim(), phoneNumber: phone.value.trim(), identityNumber: cccd.value.trim(),
        email: email.value.trim() || null, occupation: job.value.trim() || null,
      });
      d.close();
      toast(`Đã thêm khách ${name.value.trim()}`);
      done(created);
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
