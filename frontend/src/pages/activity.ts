import { icon } from '../lib/icons';
import { h } from '../lib/dom';
import { api, errText, Page } from '../lib/api';
import { dateTime, money } from '../lib/format';
import type { ActivityLog } from '../data/types';
import { emptyState, pageHead, panel, table, toast } from '../ui/kit';

const ACTIONS: Record<string, string> = {
  LOGIN: 'Đăng nhập', LOGOUT: 'Đăng xuất', CHANGE_PASSWORD: 'Đổi mật khẩu', UPDATE_PROFILE: 'Sửa hồ sơ',
  CREATE_STAFF: 'Thêm nhân viên', UPDATE_STAFF: 'Sửa nhân viên', LOCK_STAFF: 'Khóa tài khoản', UNLOCK_STAFF: 'Mở khóa tài khoản',
  CREATE_TENANT_ACCOUNT: 'Cấp tài khoản người thuê', CREATE_CONTRACT: 'Lập hợp đồng', UPDATE_CONTRACT_PRICING: 'Sửa giá hợp đồng',
  UPDATE_CONTRACT_TERMS: 'Sửa điều khoản hợp đồng', UPDATE_CONTRACT_UTILITY: 'Sửa điều khoản điện nước', SEND_CONTRACT: 'Gửi hợp đồng',
  SIGN_CONTRACT: 'Ký hợp đồng', EXTEND_CONTRACT: 'Gia hạn hợp đồng', ADJUST_CONTRACT: 'Điều chỉnh hợp đồng', TRANSFER_ROOM: 'Đổi phòng',
  TERMINATE_CONTRACT: 'Chấm dứt hợp đồng', CREATE_BILLING_PERIOD: 'Tạo kỳ tính tiền', CALCULATE_BILLING: 'Tính tiền', UPDATE_FEE_ITEM: 'Sửa dòng phí',
  APPROVE_BILLING: 'Duyệt kỳ tính tiền', RECORD_PAYMENT: 'Ghi nhận thanh toán', CREATE_QR_PAYMENT: 'Tạo mã QR thanh toán', CONFIRM_PAYMENT: 'Xác nhận thanh toán',
  CREATE_BANK_ACCOUNT: 'Thêm tài khoản ngân hàng', UPDATE_BANK_ACCOUNT: 'Sửa tài khoản ngân hàng', BACKUP: 'Sao lưu dữ liệu', RESTORE: 'Phục hồi dữ liệu',
};

/** Số tiền thô trong mô tả (vd 3876500.00) đọc không nổi: chèn dấu chấm ngăn cách hàng nghìn. */
function prettyDescription(text: string): string {
  return text.replace(/\b(\d{5,})(?:\.\d+)?\b/g, (_, n: string) => money(n));
}

function prettyIp(ip: string | null): string {
  if (!ip) return '–';
  return ip === '0:0:0:0:0:0:0:1' || ip === '::1' || ip === '127.0.0.1' ? 'Máy này' : ip;
}

export async function activityPage(view: HTMLElement): Promise<void> {
  const r = await api.get<Page<ActivityLog>>('/api/activity-logs?size=50');
  const tbl = table<ActivityLog>([
    { key: 't', label: 'Thời điểm', render: (l) => dateTime(l.createdAt) },
    { key: 'u', label: 'Người thực hiện', render: (l) => l.userFullName },
    { key: 'a', label: 'Hành động', render: (l) => h('span', { class: 'cell-main' }, ACTIONS[l.actionType] ?? l.actionType.replace(/_/g, ' ').toLowerCase()) },
    { key: 'd', label: 'Chi tiết', render: (l) => prettyDescription(l.description) },
    { key: 'i', label: 'Từ máy', render: (l) => prettyIp(l.ipAddress) },
  ], r.items, { empty: emptyState('Chưa có nhật ký', 'Mọi thao tác quan trọng sẽ được ghi lại ở đây.') });
  const dl = (fmt: string) => api.download(`/api/activity-logs/export?format=${fmt}`, `nhat-ky.${fmt}`).catch((e) => toast(errText(e), 'err'));
  view.replaceChildren(
    pageHead('Nhật ký thao tác', 'Ai đã làm gì và lúc nào. Chỉ chủ trọ xem được.', [
      h('button', { class: 'btn secondary', onclick: () => dl('xlsx') }, icon('download', 16), 'Excel'),
      h('button', { class: 'btn secondary', onclick: () => dl('pdf') }, icon('download', 16), 'PDF')]),
    panel(null, tbl, { flush: true }),
  );
}
