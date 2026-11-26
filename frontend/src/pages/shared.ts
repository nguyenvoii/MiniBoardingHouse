// UC81: trang xem hóa đơn qua liên kết chia sẻ, không cần đăng nhập, chỉ đọc.
import { h } from '../lib/dom';
import { date, money, monthLabel } from '../lib/format';
import { dl, statusBadge } from '../ui/kit';

interface Shared { invoiceCode: string; monthYear: string; issueDate: string; dueDate: string; totalAmount: number; paidAmount: number; remainingAmount: number; paymentStatus: string }

export async function sharedInvoicePage(app: HTMLElement, token: string): Promise<void> {
  document.title = 'Hóa đơn · Stayly';
  let body: HTMLElement;
  try {
    const res = await fetch(`/api/invoices/share/${encodeURIComponent(token)}`);
    if (!res.ok) throw new Error(String(res.status));
    const i = (await res.json()) as Shared;
    body = h('div', { class: 'stack' },
      h('h2', null, `Hóa đơn ${i.invoiceCode}`),
      h('div', null, statusBadge(i.paymentStatus)),
      dl([['Tháng', monthLabel(i.monthYear)], ['Ngày phát hành', date(i.issueDate)], ['Hạn thanh toán', date(i.dueDate)],
        ['Tổng tiền', `${money(i.totalAmount)} ₫`], ['Đã trả', `${money(i.paidAmount)} ₫`], ['Còn phải trả', h('b', null, `${money(i.remainingAmount)} ₫`)]]),
      h('p', { class: 'muted' }, 'Để thanh toán hoặc xem chi tiết từng khoản, đăng nhập vào Stayly bằng số điện thoại của bạn.'),
      h('a', { class: 'btn', href: '#/login' }, 'Đăng nhập'));
  } catch {
    body = h('div', { class: 'empty' }, h('h2', { class: 'empty-title' }, 'Liên kết không còn hiệu lực'), h('p', null, 'Hóa đơn này đã hủy hoặc chủ trọ đã thu hồi liên kết. Liên hệ chủ trọ để nhận liên kết mới.'));
  }
  app.replaceChildren(h('main', { style: 'max-width:520px;margin:40px auto;padding:0 16px' }, body));
}
