const vnd = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });

/** 3.755.000 (không đơn vị). */
export function money(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '0';
  return vnd.format(Math.round(Number(v)));
}

/** 3.755.000 ₫ */
export function vnd2(v: number | string | null | undefined): string {
  return `${money(v)} ₫`;
}

/** 2026-11-16 -> 16/11/2026 */
export function date(v: string | null | undefined): string {
  if (!v) return '–';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(v);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : v;
}

export function dateTime(v: string | null | undefined): string {
  if (!v) return '–';
  const m = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(v);
  return m ? `${m[3]}/${m[2]}/${m[1]} ${m[4]}:${m[5]}` : date(v);
}

export function monthLabel(ym: string): string {
  const [y, m] = ym.split('-');
  return `Tháng ${Number(m)}/${y}`;
}

export function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function shiftMonth(ym: string, delta: number): string {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function initials(name: string): string {
  const parts = name.replace(/\([^)]*\)/g, ' ').trim().split(/\s+/);
  return ((parts[parts.length - 2]?.[0] ?? '') + (parts[parts.length - 1]?.[0] ?? '')).toUpperCase() || '?';
}

/** Ngày yyyy-mm-dd theo giờ máy (không lệch 1 ngày trước 7 giờ sáng như toISOString), cộng thêm offset ngày. */
export function isoDay(offsetDays = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
