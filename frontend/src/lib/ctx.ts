import { currentMonth } from './format';

// Tháng đang làm việc, dùng chung cho các màn hình nhân viên. Đổi tháng thì màn hình đang mở tự tải lại.
let month = currentMonth();
const listeners = new Set<() => void>();

export function getMonth(): string {
  return month;
}

export function setMonth(m: string): void {
  if (m === month) return;
  month = m;
  listeners.forEach((l) => l());
}

export function onMonth(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Số hóa đơn quá hạn hiện ở thanh bên, do màn hình tổng quan cập nhật. */
let overdue = 0;
export function getOverdue(): number {
  return overdue;
}
export function setOverdue(n: number): void {
  overdue = n;
  document.querySelectorAll('[data-overdue-count]').forEach((el) => {
    el.textContent = String(n);
    (el as HTMLElement).hidden = n === 0;
  });
}
