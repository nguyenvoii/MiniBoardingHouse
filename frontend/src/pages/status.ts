import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText } from '../lib/api';
import { card, confirmDialog, dl, emptyState, pageHead, table, toast } from '../ui/kit';

interface Pool { active: number; idle: number; total: number; waiting: number; max: number }
interface Status {
  overall: 'OK' | 'WARN' | 'DOWN'; warnings: string[];
  app: { name: string; javaVersion: string; springBootVersion: string; startedAt: string; uptimeSeconds: number; serverTime: string };
  jvm: { heapUsedMb: number; heapMaxMb: number; nonHeapUsedMb: number; threads: number; peakThreads: number; processors: number; processCpuPercent: number | null };
  host: { os: string; totalMemoryMb: number; freeMemoryMb: number; systemLoad: number | null };
  database: { up: boolean; latencyMs: number; name: string | null; version: string | null; edition: string | null; sqlProcessMemoryMb: number | null; sqlMemoryNote: string | null; pool: Pool | null; error: string | null };
  disk: { path: string; totalGb: number; usableGb: number; usedPercent: number } | null;
  cacheEnabled: boolean;
  caches: Array<{ name: string; ttlSeconds: number; size: number; hits: number; misses: number; hitPercent: number }>;
}

const CACHE_LABEL: Record<string, string> = {
  dashboard: 'Tổng quan', revenue: 'Báo cáo doanh thu', properties: 'Danh sách khu trọ', servicePlans: 'Bảng giá điện nước', serviceRates: 'Đơn giá dịch vụ',
};
const OVERALL: Record<Status['overall'], [string, string, string]> = {
  OK: ['ok', 'Mọi thứ bình thường', 'check'], WARN: ['warn', 'Cần chú ý', 'alert'], DOWN: ['danger', 'Có thành phần không hoạt động', 'alert'],
};

/** Instant từ server kèm múi giờ (Z): đổi sang giờ máy đang xem. */
const localTime = (iso: string) => new Date(iso).toLocaleString('vi-VN');
const mb = (n: number) => (n >= 1024 ? `${(n / 1024).toFixed(1)} GB` : `${n} MB`);

function uptime(sec: number): string {
  const d = Math.floor(sec / 86400), hr = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60);
  return d ? `${d} ngày ${hr} giờ` : hr ? `${hr} giờ ${m} phút` : `${m} phút ${sec % 60} giây`;
}

/** Thanh đo: xanh dưới 70%, vàng từ 70%, đỏ từ 90%. Có chữ kèm theo nên không chỉ dựa vào màu. */
function meter(percent: number, label: string): HTMLElement {
  const p = Math.max(0, Math.min(100, Math.round(percent)));
  const color = p >= 90 ? 'var(--danger)' : p >= 70 ? 'var(--warn)' : 'var(--ok)';
  return h('div', { style: 'display:grid;gap:4px', role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(p), 'aria-label': label },
    h('div', { style: 'height:8px;border-radius:99px;background:var(--line);overflow:hidden' }, h('div', { style: `height:100%;width:${p}%;background:${color}` })),
    h('span', { class: 'muted', style: 'font-size:12.5px' }, `${label}: ${p}%`));
}

function pill(kind: string, text: string): HTMLElement {
  return h('span', { class: `st ${kind}` }, text);
}

export async function statusPage(view: HTMLElement): Promise<void> {
  const body = h('div', { class: 'stack' });
  const stamp = h('span', { class: 'muted' }, '');
  const refreshBtn = h('button', { class: 'btn secondary', onclick: () => void load() }, icon('refresh', 16), 'Làm mới');
  const clearBtn = h('button', { class: 'btn secondary' }, icon('x', 16), 'Xóa bộ nhớ đệm');
  clearBtn.addEventListener('click', async () => {
    if (!(await confirmDialog('Xóa bộ nhớ đệm', 'Các màn Tổng quan, Báo cáo và Bảng giá sẽ đọc lại dữ liệu mới nhất từ SQL Server ở lần mở kế tiếp. Dùng khi vừa sửa dữ liệu trực tiếp trong SQL.', 'Xóa'))) return;
    await busy(clearBtn, async () => {
      try { await api.post('/api/admin/system/cache/clear'); toast('Đã xóa bộ nhớ đệm'); await load(); } catch (e) { toast(errText(e), 'err'); }
    });
  });

  const paint = (s: Status) => {
    const [kind, label] = OVERALL[s.overall];
    const db = s.database;
    const heapPct = s.jvm.heapMaxMb ? (s.jvm.heapUsedMb * 100) / s.jvm.heapMaxMb : 0;
    const ramPct = s.host.totalMemoryMb ? ((s.host.totalMemoryMb - s.host.freeMemoryMb) * 100) / s.host.totalMemoryMb : 0;
    const totalHits = s.caches.reduce((a, c) => a + c.hits, 0), totalMisses = s.caches.reduce((a, c) => a + c.misses, 0);

    body.replaceChildren(
      card(null, [
        h('div', { style: 'display:flex;gap:12px;align-items:center;flex-wrap:wrap' }, pill(kind, label), h('span', { class: 'muted' }, `Chạy được ${uptime(s.app.uptimeSeconds)}`)),
        ...(s.warnings.length ? [h('ul', { style: 'margin:0;padding-left:18px;display:grid;gap:4px' }, ...s.warnings.map((w) => h('li', null, w)))] : []),
      ]),
      h('div', { class: 'grid-eq' },
        card('SQL Server', [
          dl([
            ['Kết nối', db.up ? pill('ok', 'Hoạt động') : pill('danger', 'Không kết nối được')],
            ['Độ trễ', `${db.latencyMs} ms`],
            ['Database', db.name ?? '–'],
            ['Phiên bản', db.version ? `${db.version}${db.edition ? ` (${db.edition})` : ''}` : '–'],
            ['RAM SQL Server đang dùng', db.sqlProcessMemoryMb != null ? mb(db.sqlProcessMemoryMb) : h('span', { class: 'muted' }, db.sqlMemoryNote ?? '–')],
          ]),
          ...(db.error ? [h('div', { class: 'form-error', role: 'alert' }, icon('alert', 16), h('span', null, db.error))] : []),
          ...(db.pool ? [meter((db.pool.active * 100) / Math.max(1, db.pool.max), `Kết nối đang dùng ${db.pool.active}/${db.pool.max}`),
            h('span', { class: 'muted' }, `Rảnh ${db.pool.idle} · đang chờ ${db.pool.waiting}`)] : []),
        ]),
        card('Bộ nhớ Java và CPU', [
          meter(heapPct, `Heap ${mb(s.jvm.heapUsedMb)} / ${mb(s.jvm.heapMaxMb)}`),
          dl([
            ['Ngoài heap', mb(s.jvm.nonHeapUsedMb)], ['Luồng', `${s.jvm.threads} (cao nhất ${s.jvm.peakThreads})`],
            ['CPU của ứng dụng', s.jvm.processCpuPercent != null ? `${s.jvm.processCpuPercent}%` : '–'], ['Số nhân CPU', s.jvm.processors],
          ]),
        ]),
        card('Máy chủ', [
          s.host.totalMemoryMb ? meter(ramPct, `RAM ${mb(s.host.totalMemoryMb - s.host.freeMemoryMb)} / ${mb(s.host.totalMemoryMb)}`) : h('span', { class: 'muted' }, 'Không đọc được RAM'),
          dl([['Hệ điều hành', s.host.os], ['Tải hệ thống', s.host.systemLoad != null ? String(s.host.systemLoad) : '–']]),
          ...(s.disk ? [meter(s.disk.usedPercent, `Ổ lưu file ${s.disk.usableGb} GB trống / ${s.disk.totalGb} GB`), h('span', { class: 'muted', style: 'overflow-wrap:anywhere' }, s.disk.path)] : []),
        ]),
        card('Ứng dụng', [
          dl([['Khởi động lúc', localTime(s.app.startedAt)], ['Giờ máy chủ', localTime(s.app.serverTime)], ['Java', s.app.javaVersion], ['Spring Boot', s.app.springBootVersion]]),
        ]),
      ),
      card('Bộ nhớ đệm', [
        h('p', { class: 'muted' }, s.cacheEnabled
          ? `Giữ tạm kết quả các truy vấn nặng để giảm tải cho SQL Server. Đã tránh được ${totalHits} lần truy vấn so với ${totalMisses} lần phải đọc DB.`
          : 'Đang tắt (app.cache.enabled=false): mọi yêu cầu đều đọc thẳng SQL Server.'),
        s.caches.length ? table<Status['caches'][number]>([
          { key: 'n', label: 'Nhóm', render: (c) => CACHE_LABEL[c.name] ?? c.name },
          { key: 't', label: 'Sống tối đa', num: true, render: (c) => `${c.ttlSeconds} giây` },
          { key: 's', label: 'Đang giữ', num: true, render: (c) => c.size },
          { key: 'h', label: 'Trúng', num: true, render: (c) => c.hits },
          { key: 'm', label: 'Trượt', num: true, render: (c) => c.misses },
          { key: 'p', label: 'Tỷ lệ trúng', num: true, render: (c) => `${c.hitPercent}%` },
        ], s.caches) : emptyState('Chưa có nhóm nào', 'Cache chưa được bật hoặc chưa có ai gọi.'),
      ]),
    );
    stamp.textContent = `Cập nhật lúc ${new Date().toLocaleTimeString('vi-VN')}`;
  };

  const load = async () => {
    try { paint(await api.get<Status>('/api/admin/system/status')); }
    catch (e) { body.replaceChildren(emptyState('Không đọc được trạng thái server', errText(e))); }
  };

  view.replaceChildren(pageHead('Trạng thái server', 'Tự cập nhật mỗi 10 giây. Chỉ chủ nhà xem được.', [stamp, refreshBtn, clearBtn]), body);
  await load();
  const timer = window.setInterval(() => { if (!view.isConnected) window.clearInterval(timer); else void load(); }, 10000);
}
