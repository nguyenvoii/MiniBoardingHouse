import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText } from '../lib/api';
import { date } from '../lib/format';
import { loadLive, LiveTenant } from '../data/live';
import { vehiclesBox } from '../ui/occupancy';
import { confirmDialog, dl, input, openDialog, pageHead, panel, plate, sampleNote, statusBadge, table, toast } from '../ui/kit';

interface AccountInfo { exists: boolean; userId: number | null; phoneNumber: string | null; status: string | null }
type Accounts = Map<number, AccountInfo | null>;

export async function tenantsPage(view: HTMLElement): Promise<void> {
  const { tenants } = await loadLive();
  // tình trạng tài khoản đăng nhập của từng khách (null = hồ sơ khách chưa có trong DB)
  const accounts: Accounts = new Map();
  await Promise.all(tenants.map(async (t) => {
    accounts.set(t.tenantId, await api.get<AccountInfo>(`/api/tenants/${t.tenantId}/account`).catch(() => null));
  }));

  const holder = h('div');
  const q = input({ type: 'search', placeholder: 'Tên, số điện thoại hoặc phòng', 'aria-label': 'Tìm khách thuê' });
  const paint = () => {
    const k = q.value.trim().toLowerCase();
    const rows = tenants.filter((t) => !k || `${t.name} ${t.phone} ${t.roomCode ?? ''}`.toLowerCase().includes(k));
    holder.replaceChildren(table<LiveTenant>([
      { key: 'n', label: 'Họ tên', render: (t) => h('span', { class: 'cell-main' }, t.name), sort: (a, b) => a.name.localeCompare(b.name, 'vi') },
      { key: 'r', label: 'Phòng', render: (t) => (t.roomCode ? plate(t.roomCode) : h('span', { class: 'muted' }, 'Chưa thuê')) },
      { key: 'p', label: 'Điện thoại', render: (t) => t.phone },
      { key: 'j', label: 'Nghề nghiệp', render: (t) => t.job },
      { key: 's', label: 'Ở từ', render: (t) => date(t.since), sort: (a, b) => (a.since ?? '').localeCompare(b.since ?? '') },
      { key: 'a', label: 'Tài khoản đăng nhập', render: (t) => accountBadge(accounts.get(t.tenantId)) },
    ], rows, { onRow: (t) => open(t, accounts, () => void tenantsPage(view)) }));
  };
  q.addEventListener('input', paint);
  paint();
  view.replaceChildren(
    pageHead('Khách thuê', 'Hồ sơ người thuê, phòng đang ở và tài khoản đăng nhập để họ xem hóa đơn trên điện thoại.', [sampleNote('Hồ sơ khách là dữ liệu mẫu'), h('button', { class: 'btn', disabled: true, title: 'Chưa có chức năng thêm khách thuê' }, icon('plus', 16), 'Thêm khách thuê')]),
    panel(null, [h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, q)), holder], { flush: true }),
  );
}

function accountBadge(a: AccountInfo | null | undefined): HTMLElement {
  if (a === null || a === undefined) return h('span', { class: 'st off' }, icon('info', 14), 'Chưa đồng bộ hồ sơ');
  if (!a.exists) return h('span', { class: 'st wait' }, icon('clock', 14), 'Chưa cấp');
  return statusBadge(a.status === 'LOCKED' ? 'LOCKED' : 'ACTIVE');
}

function open(t: LiveTenant, accounts: Accounts, refresh: () => void): void {
  const d = openDialog(t.name, { drawer: true });
  const acct = h('div', { class: 'stack', style: 'gap:12px' });
  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, t.roomCode ? plate(t.roomCode, 'lg') : null, sampleNote('Hồ sơ là dữ liệu mẫu')),
    dl([['Điện thoại', t.phone], ['Email', t.email ?? '–'], ['CCCD', t.idNumber], ['Nghề nghiệp', t.job], ['Ở từ', date(t.since)], ['Hợp đồng', t.contract ? t.contract.contractCode : 'Chưa có']]),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Xe đăng ký (tính tiền gửi xe)'), vehiclesBox(t.tenantId)),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Tài khoản đăng nhập'), acct),
  );
  const paint = (a: AccountInfo | null | undefined) => {
    acct.replaceChildren();
    if (a === null || a === undefined) {
      acct.append(h('p', { class: 'muted' }, 'Hồ sơ khách này chưa có trong cơ sở dữ liệu nên chưa cấp được tài khoản. Cần API của Chi để thêm khách thuê.'));
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
    acct.append(h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, statusBadge(locked ? 'LOCKED' : 'ACTIVE'), h('span', { class: 'muted' }, `Đăng nhập bằng số điện thoại ${a.phoneNumber ?? t.phone}`)), toggle);
  };
  paint(accounts.get(t.tenantId));
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'));
}

function showPassword(phone: string, password: string): void {
  const d = openDialog('Đã cấp tài khoản', { width: 440 });
  d.body.append(
    h('p', null, 'Gửi thông tin này cho khách thuê. Mật khẩu tạm chỉ hiện một lần, khách nên đổi sau khi đăng nhập.'),
    dl([['Đăng nhập bằng', phone], ['Mật khẩu tạm', h('span', { class: 'plate lg', style: 'font-size:20px;letter-spacing:0.08em' }, password)]]),
  );
  const copy = h('button', { class: 'btn secondary' }, icon('check', 16), 'Chép mật khẩu');
  copy.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(password); toast('Đã chép mật khẩu'); } catch { toast('Không chép được, hãy chép tay', 'err'); }
  });
  d.foot.append(copy, h('button', { class: 'btn', onclick: d.close }, 'Xong'));
}
