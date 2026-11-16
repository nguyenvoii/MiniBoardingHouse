// Chuyển động bằng script: đếm số, nhịp vào của trang. Mọi thứ có đường lui khi người dùng chọn giảm chuyển động.
/** Hiệu ứng luôn bật, không phụ thuộc cài đặt "giảm chuyển động" của hệ điều hành (theo yêu cầu của chủ dự án). */
export function initMotionPref(): void {
  document.documentElement.dataset.motion = 'full';
}

export function reducedMotion(): boolean {
  return false;
}
const reduced = reducedMotion;

const NUMBER_TARGETS = '.figure .value, .bill-card .amount';

/** Đếm số tiền/số lượng lên giá trị cuối trong 700ms (giảm dần tốc độ). Chữ số tabular nên không nhảy bố cục. */
export function countUp(root: ParentNode): void {
  if (reduced()) return;
  root.querySelectorAll<HTMLElement>(NUMBER_TARGETS).forEach((el) => {
    const node = Array.from(el.childNodes).find((n) => n.nodeType === Node.TEXT_NODE && /\d/.test(n.textContent ?? ''));
    if (!node) return;
    const text = node.textContent ?? '';
    const m = /(-?\d[\d.]*)/.exec(text);
    if (!m) return;
    const target = Number(m[1].replace(/\./g, ''));
    if (!Number.isFinite(target) || target < 10) return;
    const start = performance.now();
    const dur = Math.min(900, 420 + String(target).length * 50);
    const fmt = new Intl.NumberFormat('vi-VN');
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / dur);
      const eased = 1 - Math.pow(1 - p, 4);
      node.textContent = text.replace(m[1], fmt.format(Math.round(target * eased)));
      if (p < 1) requestAnimationFrame(tick);
    };
    node.textContent = text.replace(m[1], '0');
    requestAnimationFrame(tick);
  });
}

/** Cho các khối cấp một của trang vào lần lượt (trần 5 bậc), và hàng bảng đầu tiên vào theo nhịp. */
export function enterView(view: HTMLElement): void {
  Array.from(view.children).forEach((c, i) => (c as HTMLElement).style.setProperty('--vi', String(i)));
  view.classList.remove('view-enter');
  void view.offsetWidth; // chạy lại hoạt họa khi đổi trang
  view.classList.add('view-enter');
  view.querySelectorAll<HTMLElement>('.tbl').forEach((t) => {
    t.classList.add('enter');
    t.querySelectorAll<HTMLElement>('tbody tr').forEach((r, i) => r.style.setProperty('--ri', String(i)));
    setTimeout(() => t.classList.remove('enter'), 900);
  });
  countUp(view);
  // biển vàng chạy từ trạm trước tới: quãng đường bằng đúng khoảng cách một trạm
  view.querySelectorAll<HTMLElement>('.route-line').forEach((line) => {
    const cur = line.querySelector<HTMLElement>('.stop.current');
    const prev = cur?.previousElementSibling as HTMLElement | null;
    const node = cur?.querySelector<HTMLElement>('.node');
    if (!cur || !prev || !node) return;
    const vertical = getComputedStyle(line).gridTemplateColumns.split(' ').length === 1;
    const dist = vertical ? cur.offsetTop - prev.offsetTop : cur.offsetLeft - prev.offsetLeft;
    node.style.setProperty('--arrive', `${Math.max(dist, 60)}px`);
    if (vertical) node.style.setProperty('animation-name', 'plate-arrive-y');
  });
}

/** Ô trạng thái bật nhẹ khi vừa đổi (dùng sau khi ghi nhận thanh toán, duyệt kỳ...). */
export function popStatus(el: Element | null): void {
  if (!el || reduced()) return;
  el.classList.remove('pop');
  void (el as HTMLElement).offsetWidth;
  el.classList.add('pop');
}
