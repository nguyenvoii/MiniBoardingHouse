import { h, append } from '../lib/dom';
import { icon, IconName } from '../lib/icons';
import { money } from '../lib/format';
import { reducedMotion } from '../lib/motion';

// ---------- Thông báo nổi ----------
let toastHost: HTMLElement | null = null;
export function toast(message: string, kind: 'ok' | 'err' = 'ok'): void {
  if (!toastHost) {
    toastHost = h('div', { class: 'toasts', role: 'status', 'aria-live': 'polite' });
    document.body.appendChild(toastHost);
  }
  const t = h('div', { class: `toast ${kind === 'err' ? 'err' : ''}` }, icon(kind === 'err' ? 'warn' : 'check'), h('span', null, message));
  toastHost.appendChild(t);
  const life = kind === 'err' ? 7000 : 4000;
  setTimeout(() => t.classList.add('leaving'), life - 180);
  setTimeout(() => t.remove(), life);
}

// ---------- Hộp thoại / ngăn kéo (dùng <dialog>, không bị cắt bởi overflow) ----------
export interface DialogHandle {
  close(): void;
  body: HTMLElement;
  foot: HTMLElement;
}

let dialogSequence = 0;
const activeDialogs = new Set<() => void>();

export function closeAppDialogs(): void {
  activeDialogs.forEach((close) => close());
}

export function openDialog(title: string, opts: { drawer?: boolean; width?: number; locked?: boolean; onClose?: () => void } = {}): DialogHandle {
  const titleId = 'dlg-title-' + ++dialogSequence;
  const dlg = h('dialog', { class: `dlg ${opts.drawer ? 'drawer' : ''}`, 'aria-labelledby': titleId }) as HTMLDialogElement;
  if (opts.width) dlg.style.width = `min(${opts.width}px, calc(100vw - 16px))`;
  const body = h('div', { class: 'dlg-body' });
  const foot = h('div', { class: 'dlg-foot' });
  let closing = false;
  const close = () => {
    if (closing) return;
    closing = true;
    const done = () => {
      dlg.close();
      dlg.remove();
      activeDialogs.delete(close);
      opts.onClose?.();
    };
    if (reducedMotion()) return done();
    dlg.classList.add('closing');
    setTimeout(done, 170);
  };
  dlg.append(
    h('div', { class: 'dlg-head' }, h('h2', { id: titleId }, title), opts.locked ? null : h('button', { class: 'icon-btn', 'aria-label': 'Đóng', onclick: close }, icon('x'))),
    body,
    foot,
  );
  dlg.addEventListener('cancel', (e) => {
    e.preventDefault();
    if (!opts.locked) close();
  });
  dlg.addEventListener('click', (e) => {
    if (e.target === dlg && !opts.locked) close();
  });
  document.body.appendChild(dlg);
  dlg.showModal();
  activeDialogs.add(close);
  return { close, body, foot };
}

export function confirmDialog(title: string, message: string, okLabel: string, danger = false): Promise<boolean> {
  return new Promise((resolve) => {
    const d = openDialog(title, { onClose: () => resolve(false) });
    d.body.append(h('p', null, message));
    d.foot.append(
      h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'),
      h('button', { class: `btn ${danger ? 'danger' : ''}`, onclick: () => { resolve(true); d.close(); } }, okLabel),
    );
  });
}

// ---------- Trạng thái ----------
type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'wait' | 'off';
interface StatusDef { label: string; tone: Tone; icon: IconName }

const STATUS: Record<string, StatusDef> = {
  // hợp đồng
  ACTIVE: { label: 'Đang hiệu lực', tone: 'ok', icon: 'check' },
  EXPIRING_SOON: { label: 'Sắp hết hạn', tone: 'warn', icon: 'clock' },
  EXPIRED: { label: 'Đã hết hạn', tone: 'off', icon: 'clock' },
  TERMINATED: { label: 'Đã chấm dứt', tone: 'off', icon: 'x' },
  // hóa đơn
  UNPAID: { label: 'Chưa trả', tone: 'wait', icon: 'clock' },
  PARTIALLY_PAID: { label: 'Trả một phần', tone: 'info', icon: 'wallet' },
  PAID: { label: 'Đã trả', tone: 'ok', icon: 'check' },
  OVERDUE: { label: 'Quá hạn', tone: 'danger', icon: 'alert' },
  CANCELLED: { label: 'Đã hủy', tone: 'off', icon: 'x' },
  // kỳ tính tiền
  DRAFT: { label: 'Nháp', tone: 'wait', icon: 'edit' },
  APPROVED: { label: 'Đã duyệt', tone: 'info', icon: 'check' },
  PUBLISHED: { label: 'Đã phát hành', tone: 'ok', icon: 'send' },
  CLOSED: { label: 'Đã đóng', tone: 'off', icon: 'lock' },
  // giao dịch
  SUCCESS: { label: 'Thành công', tone: 'ok', icon: 'check' },
  FAILED: { label: 'Chờ xác nhận', tone: 'warn', icon: 'clock' },
  REVERSED: { label: 'Đã hủy giao dịch', tone: 'off', icon: 'x' },
  // phòng, tài khoản
  RENTED: { label: 'Đang thuê', tone: 'ok', icon: 'key' },
  VACANT: { label: 'Trống', tone: 'wait', icon: 'bed' },
  REPAIRING: { label: 'Đang sửa', tone: 'warn', icon: 'settings' },
  SUSPENDED: { label: 'Tạm ngưng', tone: 'off', icon: 'lock' },
  CLEANING: { label: 'Chờ dọn dẹp', tone: 'warn', icon: 'clock' },
  LOCKED: { label: 'Đã khóa', tone: 'danger', icon: 'lock' },
  INACTIVE: { label: 'Chưa kích hoạt', tone: 'wait', icon: 'clock' },
  // trả phòng, thiết bị
  PENDING: { label: 'Chờ xác nhận', tone: 'wait', icon: 'clock' },
  INSPECTING: { label: 'Đang kiểm tra phòng', tone: 'info', icon: 'search' },
  FINALIZED: { label: 'Đã thanh lý', tone: 'ok', icon: 'check' },
  REJECTED: { label: 'Từ chối', tone: 'off', icon: 'x' },
  REFUNDED: { label: 'Đã hoàn cọc', tone: 'ok', icon: 'check' },
  INSTALLED: { label: 'Đã lắp', tone: 'ok', icon: 'check' },
  REMOVED: { label: 'Đã gỡ', tone: 'off', icon: 'x' },
};

export function statusBadge(code: string | null | undefined): HTMLElement {
  const d = (code && STATUS[code]) || { label: code ?? '–', tone: 'off' as Tone, icon: 'info' as IconName };
  return h('span', { class: `st ${d.tone}` }, icon(d.icon, 14), d.label);
}

export function statusLabel(code: string): string {
  return STATUS[code]?.label ?? code;
}

export function plate(code: string, cls = ''): HTMLElement {
  return h('span', { class: `plate ${cls}` }, code);
}

// ---------- Khung chờ, rỗng, lỗi ----------
export function skeletonRows(n = 5): HTMLElement {
  const box = h('div', { class: 'panel-body', 'aria-busy': 'true', 'aria-label': 'Đang tải' });
  for (let i = 0; i < n; i++) box.appendChild(h('div', { class: 'skel', style: `height:18px;margin:14px 0;width:${96 - ((i * 13) % 40)}%` }));
  return box;
}

export function emptyState(title: string, text: string, action?: HTMLElement): HTMLElement {
  return h('div', { class: 'empty' }, h('h2', { class: 'empty-title' }, title), h('p', null, text), action);
}

export function errorState(message: string, retry?: () => void): HTMLElement {
  return h(
    'div',
    { class: 'empty' },
    h('h2', { class: 'empty-title' }, 'Chưa tải được dữ liệu'),
    h('p', null, message),
    retry ? h('button', { class: 'btn secondary', onclick: retry }, icon('refresh', 16), 'Thử lại') : null,
  );
}

export function sampleNote(text = 'Dữ liệu mẫu'): HTMLElement {
  return h('span', { class: 'sample-note' }, icon('info', 14), text);
}

// ---------- Biểu mẫu ----------
export function field(label: string, input: HTMLElement, hint?: string): HTMLElement {
  const id = input.id || `f${Math.random().toString(36).slice(2, 8)}`;
  input.id = id;
  return h('div', { class: 'field' }, h('label', { for: id }, label), input, hint ? h('span', { class: 'hint' }, hint) : null);
}

export function input(attrs: Record<string, unknown> = {}): HTMLInputElement {
  return h('input', { class: 'input', ...attrs }) as HTMLInputElement;
}

export function select(options: Array<[string, string]>, value = '', attrs: Record<string, unknown> = {}): HTMLSelectElement {
  const s = h('select', { class: 'select', ...attrs }) as HTMLSelectElement;
  for (const [v, l] of options) s.appendChild(h('option', { value: v, selected: v === value }, l));
  return s;
}

export function searchablePicker(options: Array<[string, string]>, value = '', attrs: { label?: string; placeholder?: string } = {}): { root: HTMLElement; input: HTMLInputElement; value: () => string } {
  let selected = options.find(([v]) => v === value) ?? ['', ''];
  const root = h('div', { class: 'search-picker' });
  const search = input({ type: 'search', class: 'input', placeholder: attrs.placeholder ?? 'Tìm theo tên, số điện thoại hoặc mã…', 'aria-label': attrs.label ?? 'Tìm kiếm' });
  const list = h('div', { class: 'search-picker-list', role: 'listbox' });
  const paint = () => {
    const q = search.value.trim().toLocaleLowerCase('vi');
    const found = options.filter(([, text]) => !q || text.toLocaleLowerCase('vi').includes(q));
    const items = found.length
      ? found.map(([v, text]) => h('button', { type: 'button', class: `search-picker-option${v === selected[0] ? ' selected' : ''}`, role: 'option', 'aria-selected': v === selected[0] ? 'true' : 'false' }, text, v === selected[0] ? ' ✓' : null))
      : [h('p', { class: 'search-picker-empty' }, 'Không tìm thấy kết quả')];
    list.replaceChildren(...items);
    list.querySelectorAll('button').forEach((b, i) => b.addEventListener('click', () => { selected = found[i]; search.value = selected[1]; list.hidden = true; search.dispatchEvent(new Event('input')); }));
  };
  search.value = selected[1];
  search.addEventListener('focus', () => { list.hidden = false; paint(); });
  search.addEventListener('input', () => { list.hidden = false; paint(); });
  search.addEventListener('keydown', (e) => { if (e.key === 'Escape') { list.hidden = true; search.blur(); } });
  document.addEventListener('click', (e) => { if (!root.contains(e.target as Node)) list.hidden = true; });
  list.hidden = true;
  root.append(search, list);
  return { root, input: search, value: () => selected[0] };
}

/**
 * Ô chọn tìm trên server: chỉ tải vài dòng khớp từ khóa (gõ xong 250ms mới hỏi), không đổ cả danh sách ra.
 * `search` trả về [giá trị, nhãn]; kết quả cũ về muộn hơn kết quả mới thì bị bỏ.
 */
export function remotePicker(search: (q: string) => Promise<Array<[string, string]>>, attrs: { label?: string; placeholder?: string } = {}): { root: HTMLElement; input: HTMLInputElement; value: () => string; onChange: (cb: () => void) => void } {
  let selected: [string, string] = ['', ''];
  let seq = 0;
  let timer: number | undefined;
  const listeners: Array<() => void> = [];
  const root = h('div', { class: 'search-picker' });
  const box = input({ type: 'search', class: 'input', placeholder: attrs.placeholder ?? 'Gõ tên, số điện thoại hoặc CCCD để tìm…', 'aria-label': attrs.label ?? 'Tìm kiếm', autocomplete: 'off' });
  const list = h('div', { class: 'search-picker-list', role: 'listbox' });
  const choose = (item: [string, string]) => { selected = item; box.value = item[1]; list.hidden = true; listeners.forEach((cb) => cb()); };
  const run = async () => {
    const mine = ++seq;
    list.hidden = false;
    list.replaceChildren(h('p', { class: 'search-picker-empty' }, 'Đang tìm…'));
    let found: Array<[string, string]>;
    try { found = await search(box.value.trim()); } catch { if (mine === seq) list.replaceChildren(h('p', { class: 'search-picker-empty' }, 'Không tải được kết quả, thử lại.')); return; }
    if (mine !== seq) return;
    if (!found.length) { list.replaceChildren(h('p', { class: 'search-picker-empty' }, 'Không tìm thấy kết quả')); return; }
    list.replaceChildren(...found.map((item) => {
      const b = h('button', { type: 'button', class: `search-picker-option${item[0] === selected[0] ? ' selected' : ''}`, role: 'option', 'aria-selected': item[0] === selected[0] ? 'true' : 'false' }, item[1], item[0] === selected[0] ? ' ✓' : null);
      b.addEventListener('click', () => choose(item));
      return b;
    }));
  };
  box.addEventListener('focus', () => { void run(); });
  box.addEventListener('input', () => {
    if (selected[0]) { selected = ['', '']; listeners.forEach((cb) => cb()); } // gõ lại nghĩa là bỏ lựa chọn cũ
    window.clearTimeout(timer);
    timer = window.setTimeout(() => { void run(); }, 250);
  });
  box.addEventListener('keydown', (e) => { if (e.key === 'Escape') { list.hidden = true; box.blur(); } });
  document.addEventListener('click', (e) => { if (!root.contains(e.target as Node)) list.hidden = true; });
  list.hidden = true;
  root.append(box, list);
  return { root, input: box, value: () => selected[0], onChange: (cb) => { listeners.push(cb); } };
}

export function formError(msg: string): HTMLElement {
  return h('div', { class: 'form-error', role: 'alert' }, icon('alert'), h('span', null, msg));
}

// ---------- Bảng ----------
export interface Column<T> {
  key: string;
  label: string;
  num?: boolean;
  render: (row: T) => Node | string | number;
  sort?: (a: T, b: T) => number;
}

export function table<T>(cols: Column<T>[], rows: T[], opts: { onRow?: (r: T) => void; footer?: Node[]; empty?: HTMLElement } = {}): HTMLElement {
  if (!rows.length && opts.empty) return opts.empty;
  let sortKey: string | null = null;
  let dir = 1;
  const tbody = h('tbody');
  const headRow = h('tr');
  const paint = () => {
    tbody.replaceChildren();
    const data = [...rows];
    const col = cols.find((c) => c.key === sortKey);
    if (col?.sort) data.sort((a, b) => dir * col.sort!(a, b));
    for (const r of data) {
      const tr = h('tr', { class: opts.onRow ? 'clickable' : '', tabindex: opts.onRow ? 0 : null });
      if (opts.onRow) {
        tr.addEventListener('click', () => opts.onRow!(r));
        tr.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            opts.onRow!(r);
          }
        });
      }
      for (const c of cols) tr.appendChild(h('td', { class: c.num ? 'num' : '', 'data-label': c.label }, c.render(r) as Node));
      tbody.appendChild(tr);
    }
    if (opts.footer) tbody.appendChild(h('tr', { class: 'total-row' }, ...opts.footer));
  };
  for (const c of cols) {
    const th = h('th', { class: c.num ? 'num' : '', scope: 'col', 'aria-sort': 'none' });
    if (c.sort) {
      const b = h('button', { type: 'button', onclick: () => {
        dir = sortKey === c.key ? -dir : 1;
        sortKey = c.key;
        headRow.querySelectorAll('th').forEach((x) => x.setAttribute('aria-sort', 'none'));
        th.setAttribute('aria-sort', dir === 1 ? 'ascending' : 'descending');
        paint();
      } }, c.label, icon('arrowRight', 12));
      b.querySelector('svg')!.style.transform = 'rotate(90deg)';
      th.appendChild(b);
    } else th.textContent = c.label;
    headRow.appendChild(th);
  }
  paint();
  return h('div', { class: 'tbl-wrap' }, h('table', { class: 'tbl' }, h('thead', null, headRow), tbody));
}

export function panel(title: string | null, body: Node | Node[], opts: { sub?: string; actions?: Node[]; flush?: boolean } = {}): HTMLElement {
  return h(
    'section',
    { class: `panel ${opts.flush ? 'panel-flush' : ''}` },
    title ? h('div', { class: 'panel-head' }, h('div', null, h('h2', null, title), opts.sub ? h('p', null, opts.sub) : null), opts.actions ? h('div', { class: 'page-actions' }, ...opts.actions) : null) : null,
    ...(Array.isArray(body) ? body : [body]),
  );
}

/** Khung có tiêu đề và phần thân có đệm: dùng cho biểu mẫu và nội dung rời, khác panel() dành cho bảng sát mép. */
export function card(title: string | null, body: Node | Node[], opts: { sub?: string; actions?: Node[] } = {}): HTMLElement {
  return panel(title, h('div', { class: 'panel-body stack', style: 'gap:14px' }, ...(Array.isArray(body) ? body : [body])), opts);
}

export function pageHead(title: string, sub?: string, actions: Node[] = []): HTMLElement {
  return h('div', { class: 'page-head' }, h('div', null, h('h1', null, title), sub ? h('p', null, sub) : null), actions.length ? h('div', { class: 'page-actions' }, ...actions) : null);
}

export function dl(pairs: Array<[string, Node | string | number | null | undefined]>): HTMLElement {
  const el = h('dl', { class: 'dl' });
  for (const [k, v] of pairs) {
    el.appendChild(h('dt', null, k));
    const dd = h('dd');
    append(dd, [v ?? '–']);
    el.appendChild(dd);
  }
  return el;
}

export function moneyCell(v: number | string | null | undefined, strong = false): HTMLElement {
  return h('span', { class: 'money', style: strong ? 'font-weight:700' : '' }, money(v));
}
