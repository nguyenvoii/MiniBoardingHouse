import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText } from '../lib/api';
import { date } from '../lib/format';
import { liveContracts, mapTenants, LiveTenant, ApiTenant } from '../data/live';
import { vehiclesBox } from '../ui/occupancy';
import { confirmDialog, dl, field, formError, input, openDialog, pageHead, panel, plate, select, statusBadge, table, toast } from '../ui/kit';

interface StayHistory { roomCode: string; startDate: string; endDate: string | null; checkoutReason: string | null }
interface AccountInfo { exists: boolean; userId: number | null; phoneNumber: string | null; status: string | null }
type Accounts = Map<number, AccountInfo | null>;

export async function tenantsPage(view: HTMLElement): Promise<void> {
  const contracts = await liveContracts().catch(() => []);
  const PAGE = 50; // khách tải theo từng đợt, lọc ở server: danh sách lớn không bị đổ hết ra màn hình
  let tenants: LiveTenant[] = [];
  // Chỉ tải trạng thái tài khoản khi mở hồ sơ; danh sách không tạo N+1 request.
  const accounts: Accounts = new Map();

  const holder = h('div');
  const q = input({ type: 'search', placeholder: 'Tên, số điện thoại, CCCD hoặc phòng', 'aria-label': 'Tìm khách hàng' });
  const kind = select([['', 'Tất cả khách'], ['renting', 'Đang thuê phòng'], ['none', 'Chưa thuê (khách tiềm năng)']], '', { 'aria-label': 'Lọc theo tình trạng thuê' });
  const paint = () => {
    const rows = tenants;
    holder.replaceChildren(table<LiveTenant>([
      { key: 'n', label: 'Họ tên', render: (t) => h('span', { class: 'cell-main' }, t.name), sort: (a, b) => a.name.localeCompare(b.name, 'vi') },
      { key: 'r', label: 'Tình trạng', render: (t) => (t.roomCode ? h('span', { style: 'display:inline-flex;gap:8px;align-items:center' }, plate(t.roomCode), h('span', { class: 'st ok' }, 'Đang thuê')) : h('span', { class: 'st wait' }, 'Chưa thuê')) },
      { key: 'p', label: 'Điện thoại', render: (t) => t.phone },
      { key: 'j', label: 'Nghề nghiệp', render: (t) => t.job },
      { key: 's', label: 'Ở từ', render: (t) => date(t.since), sort: (a, b) => (a.since ?? '').localeCompare(b.since ?? '') },
      { key: 'a', label: 'Tài khoản đăng nhập', render: (t) => accountBadge(accounts.get(t.tenantId)) },
    ], rows, { onRow: (t) => open(t, accounts, () => void tenantsPage(view)) }),
      ...(rows.length >= PAGE ? [h('p', { class: 'muted', style: 'padding:10px 16px' }, `Đang hiện ${PAGE} khách đầu theo tên. Gõ tên, số điện thoại, CCCD hoặc mã phòng để thu hẹp.`)] : []));
  };
  let seq = 0;
  const load = async () => {
    const mine = ++seq;
    const params = new URLSearchParams({ limit: String(PAGE) });
    if (q.value.trim()) params.set('keyword', q.value.trim());
    if (kind.value === 'renting') params.set('renting', 'true');
    if (kind.value === 'none') params.set('available', 'true');
    try {
      const found = await api.get<ApiTenant[]>(`/api/chi/tenants?${params}`);
      if (mine !== seq) return; // có lần tìm mới hơn
      tenants = mapTenants(found, contracts);
      paint();
    } catch (e) { if (mine === seq) holder.replaceChildren(formError(errText(e))); }
  };
  let timer: number | undefined;
  q.addEventListener('input', () => { window.clearTimeout(timer); timer = window.setTimeout(() => void load(), 250); });
  kind.addEventListener('change', () => void load());
  await load();
  view.replaceChildren(
    pageHead('Khách hàng', 'Hồ sơ khách: người đã đến hỏi thuê, đang thuê hoặc đã trả phòng. Khách chỉ trở thành người thuê khi có hợp đồng.', [h('button', { class: 'btn', onclick: () => addTenant(() => void tenantsPage(view)) }, icon('plus', 16), 'Thêm khách hàng')]),
    panel(null, [h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, q), kind), holder], { flush: true }),
  );
}


interface FullTenant {
  dob?: string | null; permanentAddress?: string | null; emergencyContactName?: string | null; emergencyContactPhone?: string | null;
  residenceStartDate?: string | null; residenceEndDate?: string | null; isVerified?: boolean | null; frontImageUrl?: string | null; backImageUrl?: string | null;
}

/** Các trường hồ sơ ngoài tên/SĐT/CCCD: ngày sinh, địa chỉ, người liên hệ khẩn cấp, ngày bắt đầu/kết thúc cư trú. */
function profileFields(init: FullTenant = {}) {
  const dob = input({ type: 'date', value: init.dob ?? '', 'aria-label': 'Ngày sinh' });
  const addr = input({ value: init.permanentAddress ?? '', maxlength: '255', placeholder: 'Không bắt buộc', 'aria-label': 'Địa chỉ thường trú' });
  const emName = input({ value: init.emergencyContactName ?? '', maxlength: '100', placeholder: 'Không bắt buộc', 'aria-label': 'Người liên hệ khẩn cấp' });
  const emPhone = input({ type: 'tel', value: init.emergencyContactPhone ?? '', maxlength: '20', placeholder: 'Không bắt buộc', 'aria-label': 'SĐT liên hệ khẩn cấp' });
  const from = input({ type: 'date', value: init.residenceStartDate ?? '', 'aria-label': 'Ngày bắt đầu cư trú' });
  const to = input({ type: 'date', value: init.residenceEndDate ?? '', 'aria-label': 'Ngày kết thúc cư trú' });
  const node = h('div', { class: 'form-grid' }, field('Ngày sinh', dob), field('Địa chỉ thường trú', addr), field('Người liên hệ khẩn cấp', emName),
    field('SĐT liên hệ khẩn cấp', emPhone), field('Bắt đầu cư trú', from), field('Kết thúc cư trú', to));
  const check = (): string | null => (from.value && to.value && to.value < from.value ? 'Ngày kết thúc cư trú không được trước ngày bắt đầu.' : null);
  const body = () => ({ dob: dob.value || null, permanentAddress: addr.value.trim(), emergencyContactName: emName.value.trim(), emergencyContactPhone: emPhone.value.trim(),
    residenceStartDate: from.value || null, residenceEndDate: to.value || null });
  const set = (v: FullTenant) => { dob.value = v.dob ?? ''; addr.value = v.permanentAddress ?? ''; emName.value = v.emergencyContactName ?? ''; emPhone.value = v.emergencyContactPhone ?? ''; from.value = v.residenceStartDate ?? ''; to.value = v.residenceEndDate ?? ''; };
  return { node, check, body, set };
}

/** CCCD hai mặt + xác minh hồ sơ (UC32). */
function idCardBox(tenantId: number): HTMLElement {
  const box = h('div', { class: 'stack', style: 'gap:10px' });
  const front = input({ type: 'file', accept: 'image/*', 'aria-label': 'Ảnh CCCD mặt trước' });
  const back = input({ type: 'file', accept: 'image/*', 'aria-label': 'Ảnh CCCD mặt sau' });
  const paint = async () => {
    let t: FullTenant;
    try { t = await api.get<FullTenant>(`/api/chi/tenants/${tenantId}`); } catch (e) { box.replaceChildren(formError(errText(e))); return; }
    const img = async (url?: string | null, alt = '') => {
      const src = url ? await api.blobUrl(url) : null;
      return src ? h('img', { src, alt, style: 'max-width:220px;max-height:150px;border:1px solid var(--line);border-radius:8px' }) : h('span', { class: 'muted' }, 'Chưa có ảnh');
    };
    const err = h('div');
    const up = h('button', { class: 'btn secondary sm' }, icon('plus', 14), 'Tải ảnh CCCD');
    up.addEventListener('click', () => busy(up, async () => {
      err.replaceChildren();
      if (!front.files?.[0] || !back.files?.[0]) { err.replaceChildren(formError('Chọn đủ ảnh mặt trước và mặt sau.')); return; }
      const f = new FormData(); f.append('front', front.files[0]); f.append('back', back.files[0]);
      try { await api.uploadForm(`/api/chi/tenants/${tenantId}/id-card`, f); toast('Đã lưu ảnh CCCD'); await paint(); } catch (e) { err.replaceChildren(formError(errText(e))); }
    }));
    const ver = t.isVerified
      ? h('span', { class: 'st ok' }, icon('check', 14), 'Đã xác minh')
      : h('button', { class: 'btn sm' }, icon('check', 14), 'Xác minh hồ sơ');
    if (!t.isVerified) ver.addEventListener('click', () => busy(ver as HTMLButtonElement, async () => {
      try { await api.patch(`/api/chi/tenants/${tenantId}/verify`); toast('Đã xác minh hồ sơ'); await paint(); } catch (e) { toast(errText(e), 'err'); }
    }));
    box.replaceChildren(err,
      dl([['Ngày sinh', date(t.dob ?? undefined)], ['Địa chỉ thường trú', t.permanentAddress || '–'], ['Liên hệ khẩn cấp', [t.emergencyContactName, t.emergencyContactPhone].filter(Boolean).join(' · ') || '–'],
        ['Cư trú', t.residenceStartDate ? `${date(t.residenceStartDate)} – ${t.residenceEndDate ? date(t.residenceEndDate) : 'hiện nay'}` : '–'], ['Xác minh', ver]]),
      h('div', { style: 'display:flex;gap:12px;flex-wrap:wrap' }, h('figure', { style: 'margin:0' }, await img(t.frontImageUrl, 'CCCD mặt trước'), h('figcaption', { class: 'muted' }, 'Mặt trước')),
        h('figure', { style: 'margin:0' }, await img(t.backImageUrl, 'CCCD mặt sau'), h('figcaption', { class: 'muted' }, 'Mặt sau'))),
      h('div', { style: 'display:flex;gap:8px;align-items:center;flex-wrap:wrap' }, front, back, up));
  };
  void paint();
  return box;
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
    h('section', null, h('h3', { style: 'margin-bottom:10px' }, 'Hồ sơ cư trú và CCCD'), idCardBox(t.tenantId)),
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
          showPassword(r.phoneNumber, r.temporaryPassword, t.tenantId);
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
        showPassword(r.phoneNumber, r.temporaryPassword, t.tenantId);
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
  const prof = profileFields();
  d.body.append(err, h('div', { class: 'form-grid' }, field('Họ và tên', name), field('Số điện thoại', phone, 'Đổi số điện thoại thì khách đăng nhập bằng số mới'),
    field('Số CCCD', cccd), field('Email', email), field('Nghề nghiệp', job)), prof.node);
  void api.get<FullTenant>(`/api/chi/tenants/${t.tenantId}`).then(prof.set).catch(() => undefined);
  const ok = h('button', { class: 'btn' }, 'Lưu hồ sơ');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!name.value.trim() || !phone.value.trim() || !cccd.value.trim()) { err.replaceChildren(formError('Họ tên, số điện thoại và số CCCD không được để trống.')); return; }
    const bad = prof.check();
    if (bad) { err.replaceChildren(formError(bad)); return; }
    try {
      // email, nghề nghiệp để trống thì gửi chuỗi rỗng (xóa); backend giữ nguyên trường không gửi
      await api.put(`/api/chi/tenants/${t.tenantId}`, { fullName: name.value.trim(), phoneNumber: phone.value.trim(), identityNumber: cccd.value.trim(), email: email.value.trim(), occupation: job.value.trim(), ...prof.body() });
      d.close(); toast('Đã lưu hồ sơ khách'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

/** Đưa tài khoản cho khách: SĐT + mật khẩu tạm, kèm link đăng nhập nhanh (dùng cho mã QR) có hiệu lực 24 giờ, dùng được tới khi khách đổi mật khẩu. */
export function showPassword(phone: string, password: string, tenantId: number): void {
  const d = openDialog('Đã cấp tài khoản', { width: 440 });
  const quick = h('div', { class: 'stack', style: 'gap:6px' }, h('p', { class: 'muted' }, 'Đang tạo link đăng nhập nhanh…'));
  d.body.append(
    h('p', null, 'Gửi thông tin này cho khách. Mật khẩu tạm chỉ hiện một lần, khách nên đổi sau khi đăng nhập.'),
    dl([['Đăng nhập bằng', phone], ['Mật khẩu tạm', h('span', { class: 'plate lg', style: 'font-size:20px;letter-spacing:0.08em' }, password)]]),
    quick,
  );
  void api.post<{ token: string }>(`/api/tenants/${tenantId}/account/login-link`).then((r) => {
    const link = `${location.origin}${location.pathname}?qr=${encodeURIComponent(r.token)}`;
    const copyLink = h('button', { class: 'btn secondary sm' }, icon('check', 14), 'Chép link đăng nhập nhanh');
    copyLink.addEventListener('click', async () => {
      try { await navigator.clipboard.writeText(link); toast('Đã chép link'); } catch { toast('Không chép được, hãy chép tay', 'err'); }
    });
    quick.replaceChildren(h('b', null, 'Đăng nhập nhanh không cần gõ mật khẩu'),
      h('p', { class: 'muted' }, 'Khách mở link này là vào thẳng web (hiệu lực 24 giờ, mất hiệu lực khi khách đổi mật khẩu).'), copyLink);
  }, (e) => quick.replaceChildren(h('p', { class: 'muted' }, `Chưa tạo được link đăng nhập nhanh: ${errText(e)}`)));
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
  const prof = profileFields();
  d.body.append(err, h('div', { class: 'form-grid' }, field('Họ và tên', name), field('Số điện thoại', phone, 'Dùng làm tài khoản đăng nhập của khách'),
    field('Số CCCD', cccd), field('Email', email), field('Nghề nghiệp', job)), prof.node);
  const ok = h('button', { class: 'btn' }, 'Thêm khách hàng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!name.value.trim() || !phone.value.trim() || !cccd.value.trim()) { err.replaceChildren(formError('Nhập đủ họ tên, số điện thoại và số CCCD.')); return; }
    const bad = prof.check();
    if (bad) { err.replaceChildren(formError(bad)); return; }
    try {
      const created = await api.post<{ tenantId: number }>('/api/chi/tenants', {
        ...prof.body(),
        fullName: name.value.trim(), phoneNumber: phone.value.trim(), identityNumber: cccd.value.trim(),
        email: email.value.trim() || null, occupation: job.value.trim() || null,
      });
      d.close();
      toast(`Đã thêm khách ${name.value.trim()}`);
      done(created);
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
