import { meterConsumption } from './meterConsumption';
import { h, busy, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, ApiError, errText, loadSession, PROPERTY_ID } from '../lib/api';
import { getMonth } from '../lib/ctx';
import { date, monthLabel } from '../lib/format';
import { emptyState, field, formError, input, openDialog, pageHead, panel, plate, select, statusBadge, table, toast } from '../ui/kit';

interface MeterPeriod { periodId: number; propertyId: number; periodName: string; startDate: string; endDate: string; closed: boolean }
interface MissingRoom { roomId: number; roomCode: string; floorNumber: number | null; contractId: number; tenantName: string; contractStatus: string }
interface Reading {
  readingId: number; periodId: number; roomId: number; roomCode: string; oldElectric: number; newElectric: number; usedElectric: number;
  oldWater: number; newWater: number; usedWater: number; readingDate: string; verified: boolean;
}
interface Alert { alertId: number; readingId: number; roomId: number; roomCode: string; utilityType: string; increasePercentage: number; alertMessage: string; resolved: boolean }

export async function metersPage(view: HTMLElement): Promise<void> {
  const month = getMonth();
  const periods = await api.get<MeterPeriod[]>(`/api/meter-periods?propertyId=${PROPERTY_ID}`);
  const sorted = [...periods].sort((a, b) => b.startDate.localeCompare(a.startDate));
  const sel = sorted.find((p) => p.startDate.startsWith(month)) ?? null;

  const consumption = h('button', { class: 'btn secondary', onclick: meterConsumption }, 'Lịch sử tiêu thụ');
  const createBtn = h('button', { class: 'btn', disabled: !!sel, title: sel ? `${monthLabel(month)} đã có kỳ ghi chỉ số` : null, onclick: () => createPeriod(month, () => void metersPage(view)) }, icon('plus', 16), `Tạo kỳ ${monthLabel(month)}`);
  if (!sel) {
    mount(view,
      pageHead('Chỉ số điện nước', `${monthLabel(month)} chưa có kỳ ghi chỉ số. Tạo kỳ rồi ghi chỉ số từng phòng để tính tiền.`, [consumption, createBtn]),
      sorted.length ? panel('Các kỳ đã có', table<MeterPeriod>([
        { key: 'n', label: 'Kỳ', render: (p) => h('b', null, p.periodName) },
        { key: 'd', label: 'Từ ngày – đến ngày', render: (p) => `${date(p.startDate)} – ${date(p.endDate)}` },
        { key: 's', label: 'Trạng thái', render: (p) => (p.closed ? statusBadge('CLOSED') : h('span', { class: 'st ok' }, icon('check', 14), 'Đang mở')) },
      ], sorted), { sub: 'Đổi tháng ở thanh trên để mở kỳ khác', flush: true }) : null,
    );
    return;
  }

  let missingError: string | null = null;
  const [readings, missing, alerts] = await Promise.all([
    api.get<Reading[]>(`/api/meter-periods/${sel.periodId}/readings`),
    api.get<MissingRoom[]>(`/api/meter-periods/${sel.periodId}/missing-rooms`).catch((e) => {
      missingError = e instanceof ApiError && e.status === 403 ? 'Tài khoản này không có quyền xem danh sách phòng cần ghi chỉ số.' : errText(e);
      return [] as MissingRoom[];
    }),
    api.get<Alert[]>(`/api/meter-alerts?propertyId=${PROPERTY_ID}&resolved=false`).catch(() => [] as Alert[]),
  ]);
  const reload = () => void metersPage(view);
  // số mới nhất của kỳ trước, để hiện số cũ và cảnh báo khi nhập thấp hơn
  const prevPeriod = sorted.find((p) => p.startDate < sel.startDate);
  const prevReadings = prevPeriod ? await api.get<Reading[]>(`/api/meter-periods/${prevPeriod.periodId}/readings`).catch(() => [] as Reading[]) : [];
  const olds = new Map(prevReadings.map((r) => [r.roomId, { e: r.newElectric, w: r.newWater }]));

  const closeBtn = h('button', { class: 'btn secondary' }, icon('lock', 16), sel.closed ? 'Mở lại kỳ' : 'Đóng kỳ');
  closeBtn.addEventListener('click', () => busy(closeBtn, async () => {
    try { await api.patch(`/api/meter-periods/${sel.periodId}/closed?value=${!sel.closed}`); toast(sel.closed ? 'Đã mở lại kỳ' : 'Đã đóng kỳ'); reload(); }
    catch (e) { toast(errText(e), 'err'); }
  }));
  const recordBtn = h('button', { class: 'btn', disabled: sel.closed || !missing.length, title: sel.closed ? 'Kỳ đã đóng' : missingError ? missingError : !missing.length ? 'Mọi phòng đã có chỉ số' : null, onclick: () => record(sel, missing, null, reload, olds) }, icon('plus', 16), 'Ghi chỉ số');

  mount(view,
    pageHead(`Chỉ số điện nước · ${sel.periodName}`, `${date(sel.startDate)} – ${date(sel.endDate)}`, [sel.closed ? statusBadge('CLOSED') : h('span', { class: 'st ok' }, icon('check', 14), 'Đang mở'), consumption, closeBtn, recordBtn]),
    h('div', { class: 'stack' },
      missingError ? formError(missingError) : null,
      missing.length && !sel.closed ? panel(`Còn ${missing.length} phòng chưa ghi chỉ số`, h('div', { class: 'panel-body', style: 'display:flex;gap:8px;flex-wrap:wrap' },
        ...missing.map((c) => h('button', { class: 'btn secondary sm', onclick: () => record(sel, missing, c, reload, olds) }, plate(c.roomCode), 'Ghi'))), { sub: 'Phòng có hợp đồng còn hiệu lực nhưng chưa có chỉ số trong kỳ. Thiếu chỉ số thì tiền điện nước của phòng đó tính bằng 0.' }) : null,
      alerts.length ? alertPanel(alerts, reload) : null,
      panel('Chỉ số từng phòng', table<Reading>([
        { key: 'r', label: 'Phòng', render: (r) => plate(r.roomCode), sort: (a, b) => a.roomCode.localeCompare(b.roomCode) },
        { key: 'oe', label: 'Điện cũ', num: true, render: (r) => r.oldElectric },
        { key: 'ne', label: 'Điện mới', num: true, render: (r) => r.newElectric },
        { key: 'ue', label: 'Dùng (kWh)', num: true, render: (r) => h('b', null, r.usedElectric), sort: (a, b) => a.usedElectric - b.usedElectric },
        { key: 'ow', label: 'Nước cũ', num: true, render: (r) => r.oldWater },
        { key: 'nw', label: 'Nước mới', num: true, render: (r) => r.newWater },
        { key: 'uw', label: 'Dùng (m³)', num: true, render: (r) => h('b', null, r.usedWater), sort: (a, b) => a.usedWater - b.usedWater },
        { key: 'v', label: 'Xác nhận', render: (r) => (r.verified ? h('span', { class: 'st ok' }, icon('check', 14), 'Đã xác nhận') : h('span', { class: 'st warn' }, icon('clock', 14), 'Chưa xác nhận')) },
        { key: 'a', label: '', render: (r) => rowActions(r, sel.closed, reload) },
      ], readings, { empty: emptyState('Chưa có chỉ số nào', 'Bấm Ghi chỉ số để nhập số điện nước từng phòng.') }), { flush: true }),
    ),
  );
}

function rowActions(r: Reading, closed: boolean, reload: () => void): HTMLElement {
  const box = h('div', { class: 'row-actions' });
  if (!r.verified && !closed) {
    const ok = h('button', { class: 'btn sm' }, icon('check', 14), 'Xác nhận');
    ok.addEventListener('click', () => busy(ok, async () => {
      try { await api.post(`/api/meter-readings/${r.readingId}/confirm`); toast(`Đã xác nhận chỉ số ${r.roomCode}`); reload(); }
      catch (e) { toast(errText(e), 'err'); }
    }));
    box.append(ok);
  }
  if (!closed) box.append(h('button', { class: 'btn secondary sm', onclick: () => adjust(r, reload) }, icon('edit', 14), 'Điều chỉnh'));
  return box;
}

function record(period: MeterPeriod, missing: MissingRoom[], pre: MissingRoom | null, done: () => void, olds: Map<number, { e: number; w: number }>): void {
  const d = openDialog('Ghi chỉ số điện nước');
  const err = h('div');
  const room = select(missing.map((c) => [String(c.roomId), `${c.roomCode} · ${c.tenantName}`] as [string, string]), pre ? String(pre.roomId) : '');
  const ne = input({ type: 'number', min: '0', class: 'input num-in', placeholder: 'Số điện hiện tại' });
  const nw = input({ type: 'number', min: '0', class: 'input num-in', placeholder: 'Số nước hiện tại' });
  const oldHint = h('p', { class: 'muted', 'aria-live': 'polite' });
  const showOld = () => {
    const o = olds.get(Number(room.value));
    oldHint.textContent = o ? `Số cũ của phòng: điện ${o.e}, nước ${o.w}. Số mới không được nhỏ hơn.` : 'Chưa có số kỳ trước: hệ thống lấy số ban đầu của phòng làm số cũ.';
  };
  room.addEventListener('change', showOld); showOld();
  d.body.append(err,
    h('p', { class: 'muted' }, `Kỳ ${period.periodName}. Nhập số công tơ hiện tại của phòng.`), oldHint,
    field('Phòng', room), h('div', { class: 'form-grid' }, field('Số điện mới (kWh)', ne), field('Số nước mới (m³)', nw)));
  const ok = h('button', { class: 'btn' }, 'Lưu chỉ số');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    const s = loadSession();
    if (!s) { err.replaceChildren(formError('Cần đăng nhập để ghi nhận người nhập chỉ số.')); return; }
    if (ne.value === '' || nw.value === '') { err.replaceChildren(formError('Nhập đủ số điện và số nước mới.')); return; }
    const o = olds.get(Number(room.value));
    if (o && Number(ne.value) < o.e) { err.replaceChildren(formError(`Số điện mới (${ne.value}) nhỏ hơn số cũ (${o.e}). Kiểm tra lại công tơ.`)); ne.focus(); return; }
    if (o && Number(nw.value) < o.w) { err.replaceChildren(formError(`Số nước mới (${nw.value}) nhỏ hơn số cũ (${o.w}). Kiểm tra lại công tơ.`)); nw.focus(); return; }
    try {
      await api.post('/api/meter-readings', { periodId: period.periodId, roomId: Number(room.value), newElectric: Number(ne.value), newWater: Number(nw.value), recordedByUserId: s.userId });
      d.close(); toast('Đã lưu chỉ số'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function adjust(r: Reading, done: () => void): void {
  const d = openDialog(`Điều chỉnh chỉ số ${r.roomCode}`);
  const err = h('div');
  const field1 = select([['NEW_ELECTRIC', `Số điện mới (đang ${r.newElectric})`], ['NEW_WATER', `Số nước mới (đang ${r.newWater})`]]);
  const val = input({ type: 'number', min: '0', class: 'input num-in' });
  const reason = h('textarea', { class: 'textarea', placeholder: 'Lý do điều chỉnh (bắt buộc)' }) as HTMLTextAreaElement;
  d.body.append(err, field('Chỉ số cần sửa', field1), field('Giá trị mới', val, 'Không được nhỏ hơn chỉ số cũ'), field('Lý do', reason));
  const ok = h('button', { class: 'btn' }, 'Lưu điều chỉnh');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const s = loadSession();
    if (!s) { err.replaceChildren(formError('Cần đăng nhập.')); return; }
    try {
      await api.put(`/api/meter-readings/${r.readingId}/adjust`, { adjustedField: field1.value, newValue: Number(val.value), reason: reason.value.trim(), adjustedByUserId: s.userId });
      d.close(); toast('Đã điều chỉnh, có ghi lại lịch sử'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function alertPanel(alerts: Alert[], reload: () => void): HTMLElement {
  return panel('Cảnh báo tiêu thụ bất thường', table<Alert>([
    { key: 'r', label: 'Phòng', render: (a) => plate(a.roomCode) },
    { key: 't', label: 'Loại', render: (a) => (a.utilityType === 'ELECTRIC' ? 'Điện' : 'Nước') },
    { key: 'p', label: 'Tăng', num: true, render: (a) => `+${Math.round(Number(a.increasePercentage))}%` },
    { key: 'm', label: 'Chi tiết', render: (a) => a.alertMessage },
    { key: 'x', label: '', render: (a) => h('button', { class: 'btn secondary sm', onclick: async () => {
      try { await api.post(`/api/meter-alerts/${a.alertId}/resolve`); toast('Đã đánh dấu đã xử lý'); reload(); } catch (e) { toast(errText(e), 'err'); }
    } }, 'Đã xử lý') },
  ], alerts), { sub: 'So với kỳ trước. Kiểm tra lại công tơ rồi đánh dấu đã xử lý.', flush: true });
}

function createPeriod(month: string, done: () => void): void {
  const d = openDialog(`Tạo kỳ ghi chỉ số ${monthLabel(month)}`);
  const err = h('div');
  const [y, m] = month.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  const name = input({ value: monthLabel(month) });
  const start = input({ type: 'date', value: `${month}-01` });
  const end = input({ type: 'date', value: `${month}-${String(last).padStart(2, '0')}` });
  d.body.append(err, field('Tên kỳ', name), h('div', { class: 'form-grid' }, field('Từ ngày', start), field('Đến ngày', end)),
    h('p', { class: 'muted' }, 'Kỳ ghi chỉ số phải bắt đầu trong tháng cần tính tiền thì kỳ tính tiền mới nhận được chỉ số.'));
  const ok = h('button', { class: 'btn' }, 'Tạo kỳ');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      await api.post('/api/meter-periods', { propertyId: PROPERTY_ID, periodName: name.value.trim(), startDate: start.value, endDate: end.value });
      d.close(); toast('Đã tạo kỳ ghi chỉ số'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}
