import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, Page } from '../lib/api';
import { date, dateTime, money } from '../lib/format';
import { SAMPLE_TENANTS } from '../data/sample';
import { loadLive } from '../data/live';
import type { Contract, ContractDetail } from '../data/types';
import { confirmDialog, dl, emptyState, field, formError, input, openDialog, pageHead, plate, sampleNote, select, statusBadge, table, toast } from '../ui/kit';

const ADJ: Record<string, string> = {
  PRICE_CHANGE: 'Đổi giá thuê', DEPOSIT_CHANGE: 'Đổi tiền cọc', PAYMENT_SCHEDULE: 'Đổi chu kỳ và hạn trả', PAYMENT_TERMS: 'Sửa điều khoản thanh toán',
  UTILITY_TERMS: 'Sửa điều khoản điện nước', PDF_GENERATED: 'Tạo bản PDF', SIGNED: 'Ký hợp đồng', EXTENSION: 'Gia hạn', TENANT_CHANGE: 'Đổi người đứng tên',
  ROOM_TRANSFER: 'Chuyển phòng', TERMINATED: 'Chấm dứt hợp đồng',
};

export async function contractsPage(view: HTMLElement, _p: Record<string, string>, query: URLSearchParams): Promise<void> {
  const state = { keyword: '', status: '' };
  const holder = h('div');
  const q = input({ type: 'search', placeholder: 'Mã hợp đồng, tên khách hoặc phòng', 'aria-label': 'Tìm hợp đồng' });
  const status = select([['', 'Mọi trạng thái'], ['ACTIVE', 'Đang hiệu lực'], ['EXPIRING_SOON', 'Sắp hết hạn'], ['EXPIRED', 'Đã hết hạn'], ['TERMINATED', 'Đã chấm dứt']], '', { 'aria-label': 'Lọc theo trạng thái hợp đồng' });
  const load = async () => {
    const r = await api.get<Page<Contract>>(`/api/contracts?keyword=${encodeURIComponent(state.keyword)}&status=${state.status}&size=50`);
    holder.replaceChildren(table<Contract>([
      { key: 'c', label: 'Mã hợp đồng', render: (c) => h('span', { class: 'cell-main' }, c.contractCode) },
      { key: 'r', label: 'Phòng', render: (c) => plate(c.roomCode), sort: (a, b) => a.roomCode.localeCompare(b.roomCode) },
      { key: 't', label: 'Người thuê', render: (c) => c.tenantName },
      { key: 'p', label: 'Thời hạn', render: (c) => `${date(c.startDate)} – ${date(c.endDate)}`, sort: (a, b) => a.endDate.localeCompare(b.endDate) },
      { key: 'm', label: 'Giá thuê (₫)', num: true, render: (c) => money(c.monthlyRentPrice), sort: (a, b) => a.monthlyRentPrice - b.monthlyRentPrice },
      { key: 's', label: 'Trạng thái', render: (c) => statusBadge(c.status) },
      { key: 'g', label: 'Ký', render: (c) => c.digitalSignatureUrl ? h('span', { class: 'st ok' }, icon('check', 14), 'Đã ký') : h('span', { class: 'st wait' }, icon('clock', 14), 'Chưa ký') },
    ], r.items, { onRow: (c) => openContract(c.contractId, load),
      empty: emptyState('Chưa có hợp đồng', state.keyword || state.status ? 'Không hợp đồng nào khớp bộ lọc.' : 'Lập hợp đồng đầu tiên từ một phòng đang trống.') }));
  };
  let timer: number | undefined;
  q.addEventListener('input', () => { clearTimeout(timer); timer = window.setTimeout(() => { state.keyword = q.value.trim(); void load(); }, 300); });
  status.addEventListener('change', () => { state.status = status.value; void load(); });

  view.replaceChildren(
    pageHead('Hợp đồng', 'Lập, gia hạn, đổi phòng và chấm dứt hợp đồng thuê.', [h('button', { class: 'btn', onclick: () => createContract(null, load) }, icon('plus', 16), 'Lập hợp đồng')]),
    h('section', { class: 'panel panel-flush' }, h('div', { class: 'toolbar' }, h('div', { class: 'grow' }, q), status), holder),
  );
  await load();
  const open = query.get('open');
  const nw = query.get('new');
  if (open) void openContract(Number(open), load);
  else if (nw) createContract(Number(nw), load);
}

async function createContract(roomId: number | null, done: () => void): Promise<void> {
  const { rooms: liveRooms } = await loadLive();
  const d = openDialog('Lập hợp đồng thuê', { width: 640 });
  const err = h('div');
  const vacant = liveRooms.filter((r) => r.status === 'VACANT');
  const room = select(vacant.map((r) => [String(r.roomId), `${r.code} · ${money(r.price)} ₫/tháng`] as [string, string]), roomId ? String(roomId) : '');
  const tenant = select(SAMPLE_TENANTS.map((t) => [String(t.tenantId), `${t.name} · ${t.phone}`] as [string, string]));
  const start = input({ type: 'date', value: new Date().toISOString().slice(0, 10) });
  const end = input({ type: 'date' });
  const rent = input({ type: 'number', min: '0', step: '10000', placeholder: 'Mặc định theo phòng', class: 'input num-in' });
  const dep = input({ type: 'number', min: '0', step: '10000', placeholder: 'Mặc định theo phòng', class: 'input num-in' });
  const cycle = select([['1', 'Mỗi tháng'], ['2', '2 tháng một lần'], ['3', '3 tháng một lần'], ['6', '6 tháng một lần']]);
  const due = input({ type: 'number', min: '1', max: '28', value: '5', class: 'input num-in' });
  const late = input({ type: 'number', min: '0', step: '1000', value: '10000', class: 'input num-in' });
  d.body.append(err, sampleNote('Danh sách khách là dữ liệu mẫu. Phòng trống lấy theo hợp đồng thật'),
    h('div', { class: 'form-grid' }, field('Phòng (chỉ phòng trống)', room), field('Người thuê', tenant),
      field('Ngày bắt đầu', start), field('Ngày kết thúc', end),
      field('Giá thuê (₫/tháng)', rent), field('Tiền cọc (₫)', dep),
      field('Chu kỳ thanh toán', cycle), field('Hạn trả mỗi kỳ (ngày trong tháng)', due, 'Từ 1 đến 28'),
      field('Phạt trễ hạn (₫/ngày)', late)));
  const ok = h('button', { class: 'btn' }, 'Lập hợp đồng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    if (!room.value || !end.value) { err.replaceChildren(formError('Chọn phòng và ngày kết thúc.')); return; }
    try {
      const c = await api.post<Contract>('/api/contracts', {
        roomId: Number(room.value), tenantId: Number(tenant.value), startDate: start.value, endDate: end.value,
        monthlyRentPrice: rent.value ? Number(rent.value) : null, depositAmount: dep.value ? Number(dep.value) : null,
        paymentCycleMonths: Number(cycle.value), paymentDueDay: Number(due.value), lateFeePerDay: Number(late.value),
      });
      d.close();
      toast(`Đã lập hợp đồng ${c.contractCode}`);
      done();
      void openContract(c.contractId, done);
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function openContract(id: number, reload: () => void): Promise<void> {
  const detail = await api.get<ContractDetail>(`/api/contracts/${id}`).catch((e) => { toast(errText(e), 'err'); return null; });
  if (!detail) return;
  const c = detail.contract;
  const history = detail.adjustments.filter((a) => !(isNum(a.oldValue) && isNum(a.newValue) && Number(a.oldValue) === Number(a.newValue)));
  const live = c.status === 'ACTIVE' || c.status === 'EXPIRING_SOON';
  const d = openDialog(`Hợp đồng ${c.contractCode}`, { drawer: true, width: 620 });
  const refresh = () => { d.close(); reload(); void openContract(id, reload); };

  d.body.append(
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(c.roomCode, 'lg'), h('b', { style: 'font-size:16px' }, c.tenantName), statusBadge(c.status)),
    dl([
      ['Thời hạn', `${date(c.startDate)} – ${date(c.endDate)}`], ['Giá thuê', `${money(c.monthlyRentPrice)} ₫/tháng`], ['Tiền cọc', `${money(c.depositAmount)} ₫`],
      ['Thanh toán', `${c.paymentCycleMonths} tháng một lần, hạn ngày ${c.paymentDueDay}`], ['Phạt trễ hạn', `${money(c.lateFeePerDay ?? 0)} ₫/ngày`],
      ['Điều khoản điện', c.electricTerms ?? '–'], ['Điều khoản nước', c.waterTerms ?? '–'], ['Điều kiện hoàn cọc', c.depositRefundConditions ?? '–'],
      ['Chữ ký', c.digitalSignatureUrl ? h('img', { src: c.digitalSignatureUrl, alt: 'Chữ ký', style: 'height:56px;border:1px solid var(--line);border-radius:6px;background:#fff' }) : 'Chưa ký'],
    ]),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Lịch sử thay đổi'),
      history.length
        ? h('ol', { class: 'timeline' }, ...history.map((a) => h('li', null, h('span', { class: 'pip' }),
            h('div', null, h('b', null, ADJ[a.adjustmentType] ?? a.adjustmentType), h('span', null, `${a.oldValue ? fmtVal(a.oldValue) + ' → ' : ''}${fmtVal(a.newValue ?? '')} · ${dateTime(a.adjustedAt)}`)))))
        : h('p', { class: 'muted' }, 'Chưa có thay đổi nào.')),
  );

  const act = (label: string, ic: Parameters<typeof icon>[0], fn: () => void, cls = 'secondary') =>
    h('button', { class: `btn ${cls} sm`, disabled: !live, onclick: fn }, icon(ic, 14), label);
  const pdf = h('button', { class: 'btn secondary sm', onclick: () => api.download(`/api/contracts/${id}/pdf`, `hop-dong-${c.contractCode}.pdf`).catch((e) => toast(errText(e), 'err')) }, icon('download', 14), 'Tải PDF');
  d.foot.append(
    h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap;margin-right:auto' },
      pdf, act('Gửi khách', 'send', async () => { try { toast(await sendMsg(id)); } catch (e) { toast(errText(e), 'err'); } }),
      act('Ký', 'sign', () => signPad(id, refresh))),
    h('div', { style: 'display:flex;gap:6px;flex-wrap:wrap' },
      act('Sửa điều khoản', 'edit', () => editTerms(c, refresh)), act('Gia hạn', 'calendar', () => extend(c, refresh)),
      act('Đổi phòng', 'key', () => transfer(c, refresh))),
  );
  if (live) {
    d.body.append(h('div', { class: 'danger-zone' }, h('p', null, 'Chấm dứt hợp đồng sẽ trả phòng về trạng thái trống và không hoàn tác được.'),
      h('button', { class: 'btn danger', onclick: () => void terminate(c, refresh) }, icon('x', 16), 'Chấm dứt hợp đồng')));
  }
}

const isNum = (v: string | null): v is string => v !== null && /^-?\d+(\.\d+)?$/.test(v);
/** Số trong lịch sử (3000000.00) hiện dạng 3.000.000; chữ và ngày giữ nguyên. */
const fmtVal = (v: string): string => (isNum(v) && v.replace(/[-.]/g, '').length >= 5 ? money(v) : v);

async function sendMsg(id: number): Promise<string> {
  const r = await api.post<unknown>(`/api/contracts/${id}/send`);
  return typeof r === 'string' ? r : 'Đã gửi';
}

function editTerms(c: Contract, done: () => void): void {
  const d = openDialog('Sửa điều khoản hợp đồng', { width: 640 });
  const err = h('div');
  const rent = input({ type: 'number', value: String(c.monthlyRentPrice), class: 'input num-in' });
  const dep = input({ type: 'number', value: String(c.depositAmount), class: 'input num-in' });
  const due = input({ type: 'number', min: '1', max: '28', value: String(c.paymentDueDay), class: 'input num-in' });
  const late = input({ type: 'number', value: String(c.lateFeePerDay ?? 0), class: 'input num-in' });
  const el = input({ value: c.electricTerms ?? '' });
  const wa = input({ value: c.waterTerms ?? '' });
  const refund = h('textarea', { class: 'textarea' }, c.depositRefundConditions ?? '') as HTMLTextAreaElement;
  d.body.append(err, h('div', { class: 'form-grid' }, field('Giá thuê (₫)', rent), field('Tiền cọc (₫)', dep), field('Hạn trả (ngày)', due), field('Phạt trễ hạn (₫/ngày)', late),
    field('Điều khoản điện', el), field('Điều khoản nước', wa), h('div', { class: 'full' }, field('Điều kiện hoàn cọc', refund))));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      await api.put(`/api/contracts/${c.contractId}/pricing`, { monthlyRentPrice: Number(rent.value), depositAmount: Number(dep.value), paymentDueDay: Number(due.value) });
      await api.put(`/api/contracts/${c.contractId}/payment-terms`, { lateFeePerDay: Number(late.value), depositRefundConditions: refund.value });
      await api.put(`/api/contracts/${c.contractId}/utility-terms`, { electricTerms: el.value, waterTerms: wa.value });
      d.close(); toast('Đã lưu điều khoản'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function extend(c: Contract, done: () => void): void {
  const d = openDialog('Gia hạn hợp đồng');
  const err = h('div');
  const end = input({ type: 'date', min: c.endDate });
  d.body.append(err, h('p', null, `Hiện hết hạn ngày ${date(c.endDate)}.`), field('Ngày hết hạn mới', end));
  const ok = h('button', { class: 'btn' }, 'Gia hạn');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.put(`/api/contracts/${c.contractId}/extend`, { newEndDate: end.value }); d.close(); toast('Đã gia hạn'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function transfer(c: Contract, done: () => void): Promise<void> {
  const { rooms: liveRooms } = await loadLive();
  const d = openDialog('Đổi phòng cho khách');
  const err = h('div');
  const room = select(liveRooms.filter((r) => r.status === 'VACANT' && r.roomId !== c.roomId).map((r) => [String(r.roomId), `${r.code} · ${money(r.price)} ₫`] as [string, string]));
  const period = input({ type: 'number', placeholder: 'Mã kỳ ghi chỉ số (tùy chọn)' });
  const e = input({ type: 'number', placeholder: 'Số điện chốt phòng cũ', class: 'input num-in' });
  const w = input({ type: 'number', placeholder: 'Số nước chốt phòng cũ', class: 'input num-in' });
  d.body.append(err, sampleNote('Phòng trống lấy theo hợp đồng thật'), field('Phòng mới', room),
    h('p', { class: 'muted' }, 'Muốn chốt chỉ số phòng cũ thì nhập đủ cả ba ô dưới.'),
    h('div', { class: 'form-grid' }, field('Kỳ ghi chỉ số', period), field('Số điện chốt', e), field('Số nước chốt', w)));
  const ok = h('button', { class: 'btn' }, 'Đổi phòng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      await api.post(`/api/contracts/${c.contractId}/transfer`, { newRoomId: Number(room.value), periodId: period.value ? Number(period.value) : null, closingElectric: e.value ? Number(e.value) : null, closingWater: w.value ? Number(w.value) : null });
      d.close(); toast('Đã đổi phòng'); done();
    } catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}

async function terminate(c: Contract, done: () => void): Promise<void> {
  const d = openDialog('Chấm dứt hợp đồng');
  const err = h('div');
  const reason = h('textarea', { class: 'textarea', placeholder: 'Lý do chấm dứt (bắt buộc)' }) as HTMLTextAreaElement;
  d.body.append(err, h('p', null, `Hợp đồng ${c.contractCode} chấm dứt thì phòng ${c.roomCode} trở về trống và không hoàn tác được.`), field('Lý do', reason));
  const ok = h('button', { class: 'btn danger' }, 'Chấm dứt hợp đồng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Giữ hợp đồng'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!(await confirmDialog('Xác nhận chấm dứt', 'Bạn chắc chắn chấm dứt hợp đồng này?', 'Chấm dứt', true))) return;
    try { await api.post(`/api/contracts/${c.contractId}/terminate`, { reason: reason.value }); d.close(); toast('Đã chấm dứt hợp đồng'); done(); }
    catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}

/** Bảng vẽ chữ ký bằng chuột hoặc ngón tay, gửi lên dạng ảnh PNG. */
export function signPad(id: number, done: () => void, mine = false): void {
  const d = openDialog('Ký hợp đồng');
  const err = h('div');
  const canvas = h('canvas', { width: 520, height: 200, style: 'width:100%;height:auto;touch-action:none;border:1.5px dashed var(--line-strong);border-radius:8px;background:#fff;cursor:crosshair', 'aria-label': 'Vùng ký tên' }) as HTMLCanvasElement;
  const ctx = canvas.getContext('2d')!;
  ctx.lineWidth = 3; ctx.lineCap = 'round'; ctx.strokeStyle = '#14231c';
  let drawing = false; let dirty = false;
  const pos = (e: PointerEvent) => { const r = canvas.getBoundingClientRect(); return [(e.clientX - r.left) * (canvas.width / r.width), (e.clientY - r.top) * (canvas.height / r.height)]; };
  canvas.addEventListener('pointerdown', (e) => { drawing = true; canvas.setPointerCapture(e.pointerId); const [x, y] = pos(e); ctx.beginPath(); ctx.moveTo(x, y); });
  canvas.addEventListener('pointermove', (e) => { if (!drawing) return; const [x, y] = pos(e); ctx.lineTo(x, y); ctx.stroke(); dirty = true; });
  canvas.addEventListener('pointerup', () => (drawing = false));
  d.body.append(err, h('p', { class: 'muted' }, 'Ký vào khung bên dưới. Chữ ký chỉ lưu được một lần cho mỗi hợp đồng.'), canvas);
  const clearBtn = h('button', { class: 'btn ghost', onclick: () => { ctx.clearRect(0, 0, canvas.width, canvas.height); dirty = false; } }, 'Xóa để ký lại');
  const ok = h('button', { class: 'btn' }, 'Lưu chữ ký');
  d.foot.append(clearBtn, h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    if (!dirty) { err.replaceChildren(formError('Hãy ký vào khung trước khi lưu.')); return; }
    const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res, 'image/png'));
    if (!blob) return;
    try { await api.upload(mine ? `/api/me/contracts/${id}/sign` : `/api/contracts/${id}/sign`, blob); d.close(); toast('Đã lưu chữ ký'); done(); }
    catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}
