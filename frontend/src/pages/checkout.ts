import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, ApiError, errText, Page } from '../lib/api';
import { date, dateTime, money } from '../lib/format';
import { liveContracts } from '../data/live';
import { confirmDialog, dl, emptyState, field, formError, input, openDialog, pageHead, card, plate, select, statusBadge, table, toast } from '../ui/kit';

// Trả phòng, quyết toán, hoàn cọc, thanh lý hợp đồng (UC101 đến 107).
// Luồng: Chờ xác nhận → Kiểm tra phòng → Quyết toán → Hoàn cọc → Thanh lý.

interface Checkout {
  checkoutId: number; contractId: number; contractCode: string; roomId: number; roomCode: string; tenantId: number; tenantName: string;
  expectedCheckoutDate: string; actualCheckoutDate: string | null; status: string; createdAt: string;
}
interface Inspection {
  finalElectricMeter: number; finalWaterMeter: number; damagedEquipmentsDescription: string | null; damageCompensationAmount: number;
  inspectionPhotoUrl: string | null; inspectedAt: string; meterRecorded: boolean;
}
interface CheckinRecord {
  checkinDate: string | null; initialElectricMeter: number | null; initialWaterMeter: number | null; keyHandedCount: number | null;
  furnishingsCondition: string | null; notes: string | null; inspectionPhotoUrl: string | null; tenantConfirmed: boolean | null; inspectedByName: string | null;
}
interface Settlement {
  originalDeposit: number; debtTotal: number; damageTotal: number; deductedDebt: number; deductedDamage: number; refundable: number; tenantMustPay: number;
  unpaidInvoices: Array<{ invoiceId: number; invoiceCode: string; dueDate: string; remainingAmount: number }>;
  contractDeposit: number; depositCollected: number; finalMonthBilled: boolean;
}
interface Voucher { refundId: number; actualRefundAmount: number; refundDate: string | null; status: string }
interface Handover { checkout: Checkout; inspection: Inspection | null; settlement: Settlement; refundVoucher: Voucher | null; checkin: CheckinRecord | null }

const STEPS = ['Yêu cầu', 'Kiểm tra phòng', 'Quyết toán', 'Hoàn cọc', 'Thanh lý'];

/** Bước đang làm (0 đến 4); 5 = xong cả năm bước. */
function stepOf(c: Checkout, ins: Inspection | null, v: Voucher | null): number {
  if (c.status === 'FINALIZED') return 5;
  if (c.status === 'PENDING') return 0;
  if (v) return v.status === 'REFUNDED' ? 4 : 3;
  return ins ? 2 : 1;
}

export async function checkoutPage(view: HTMLElement, _p: Record<string, string> = {}, query: URLSearchParams = new URLSearchParams()): Promise<void> {
  const state = { status: '' };
  const holder = h('div');
  const status = select([['', 'Mọi trạng thái'], ['PENDING', 'Chờ xác nhận'], ['INSPECTING', 'Đang kiểm tra phòng'], ['FINALIZED', 'Đã thanh lý'], ['CANCELLED', 'Đã hủy']], '', { 'aria-label': 'Lọc theo trạng thái trả phòng' });
  const load = async () => {
    const r = await api.get<Page<Checkout>>(`/api/checkout-requests?status=${state.status}&size=100`);
    holder.replaceChildren(table<Checkout>([
      { key: 'r', label: 'Phòng', render: (c) => plate(c.roomCode) },
      { key: 't', label: 'Khách hàng', render: (c) => h('span', { class: 'cell-main' }, c.tenantName), sort: (a, b) => a.tenantName.localeCompare(b.tenantName, 'vi') },
      { key: 'h', label: 'Hợp đồng', render: (c) => c.contractCode },
      { key: 'e', label: 'Ngày trả dự kiến', render: (c) => date(c.expectedCheckoutDate), sort: (a, b) => a.expectedCheckoutDate.localeCompare(b.expectedCheckoutDate) },
      { key: 'a', label: 'Ngày trả thực tế', render: (c) => date(c.actualCheckoutDate) },
      { key: 's', label: 'Trạng thái', render: (c) => statusBadge(c.status) },
    ], r.items, {
      onRow: (c) => void openCheckout(c.checkoutId, () => void load()),
      empty: emptyState('Chưa có yêu cầu trả phòng', 'Khi khách muốn trả phòng, bấm «Tạo yêu cầu trả phòng». Người thuê cũng tự gửi được từ ứng dụng.'),
    }));
  };
  status.addEventListener('change', () => { state.status = status.value; void load(); });
  view.replaceChildren(
    pageHead('Trả phòng', 'Nhận yêu cầu, kiểm tra phòng, quyết toán tiền cọc, hoàn cọc và thanh lý hợp đồng.',
      [h('button', { class: 'btn', onclick: () => void createCheckout((id) => { void load(); void openCheckout(id, () => void load()); }) }, icon('plus', 16), 'Tạo yêu cầu trả phòng')]),
    h('section', { class: 'panel panel-flush' }, h('div', { class: 'toolbar' }, h('span', { class: 'grow' }), status), holder),
  );
  await load();
  const pre = Number(query.get('contract')) || null;
  if (pre) void createCheckout((id) => { void load(); void openCheckout(id, () => void load()); }, pre);
}

async function createCheckout(done: (id: number) => void, preContract: number | null = null): Promise<void> {
  const contracts = await liveContracts().catch(() => []);
  const d = openDialog('Tạo yêu cầu trả phòng', { width: 520 });
  const err = h('div');
  if (!contracts.length) {
    d.body.append(h('p', { class: 'muted' }, 'Chưa có hợp đồng nào còn hiệu lực, hoặc đã hết hạn mà khách còn ở, để trả phòng.'));
    d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'));
    return;
  }
  const contract = select(contracts.map((c) => [String(c.contractId), `${c.roomCode} · ${c.tenantName} · ${c.contractCode}${c.status === 'EXPIRED' ? ' · đã hết hạn, khách còn ở' : ''}`] as [string, string]), preContract ? String(preContract) : '');
  const expected = input({ type: 'date', value: new Date().toISOString().slice(0, 10), min: new Date().toISOString().slice(0, 10) });
  d.body.append(err, field('Hợp đồng', contract), field('Ngày trả phòng dự kiến', expected, 'Không chọn ngày trong quá khứ'));
  const ok = h('button', { class: 'btn' }, 'Tạo yêu cầu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    try {
      const c = await api.post<Checkout>('/api/checkout-requests', { contractId: Number(contract.value), expectedCheckoutDate: expected.value });
      d.close();
      toast('Đã tạo yêu cầu trả phòng');
      done(c.checkoutId);
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function openCheckout(id: number, reload: () => void): Promise<void> {
  const d = openDialog('Trả phòng', { drawer: true });
  const paint = async () => {
    let c: Checkout;
    try {
      c = await api.get<Checkout>(`/api/checkout-requests/${id}`);
    } catch (e) { d.body.replaceChildren(formError(errText(e))); return; }
    const [ins, st, v, report] = await Promise.all([
      api.get<Inspection>(`/api/checkout-requests/${id}/inspection`).catch(() => null),
      api.get<Settlement>(`/api/checkout-requests/${id}/settlement`).catch(() => null),
      api.get<Voucher>(`/api/checkout-requests/${id}/refund-voucher`).catch(() => null),
      api.get<Handover>(`/api/checkout-requests/${id}/handover-report`).catch(() => null),
    ]);
    const checkin = report?.checkin ?? null;
    const open = c.status === 'PENDING' || c.status === 'INSPECTING';
    const step = stepOf(c, ins, v);
    const after = async (fn: () => Promise<unknown>, msg: string) => {
      try { await fn(); toast(msg); reload(); await paint(); } catch (e) { toast(errText(e), 'err'); }
    };

    const body: Node[] = [
      h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(c.roomCode, 'lg'), statusBadge(c.status)),
      dl([['Khách hàng', c.tenantName], ['Hợp đồng', c.contractCode], ['Ngày trả dự kiến', date(c.expectedCheckoutDate)], ['Ngày trả thực tế', date(c.actualCheckoutDate)]]),
    ];
    if (c.status !== 'CANCELLED') {
      body.push(h('ol', { class: 'steps', 'aria-label': 'Các bước trả phòng' },
        ...STEPS.map((s, i) => h('li', { class: i < step ? 'done' : i === step ? 'now' : '', 'aria-current': i === step ? 'step' : null }, h('span', { class: 'n' }, i < step ? icon('check', 12) : String(i + 1)), s))));
    }
    if (checkin) {
      body.push(card('Lúc nhận phòng (để đối chiếu)', dl([
        ['Ngày nhận phòng', date(checkin.checkinDate)], ['Chỉ số điện đầu', checkin.initialElectricMeter ?? '–'], ['Chỉ số nước đầu', checkin.initialWaterMeter ?? '–'],
        ['Số chìa khóa đã giao', checkin.keyHandedCount ?? '–'],
        ['Nội thất lúc nhận', checkin.furnishingsCondition ? h('span', { style: 'white-space:pre-line' }, checkin.furnishingsCondition) : '–'],
        ['Ghi chú', checkin.notes ? h('span', { style: 'white-space:pre-line' }, checkin.notes) : '–'],
        ['Người lập', checkin.inspectedByName ?? '–'], ['Khách đã xác nhận', checkin.tenantConfirmed ? 'Có' : 'Chưa'],
      ])));
    } else if (report) {
      body.push(h('p', { class: 'muted' }, 'Hợp đồng này chưa có biên bản nhận phòng, nên không có số đầu và nội thất để đối chiếu.'));
    }
    if (ins) {
      body.push(card('Hiện trạng phòng', dl([
        ['Chỉ số điện cuối', ins.finalElectricMeter], ['Chỉ số nước cuối', ins.finalWaterMeter],
        ['Đã vào chỉ số điện nước', ins.meterRecorded ? h('span', { class: 'st ok' }, icon('check', 14), 'Đã ghi vào kỳ chỉ số') : h('span', { class: 'st warn' }, icon('warn', 14), 'Chưa ghi (không có kỳ ghi chỉ số đang mở)')],
        ['Thiết bị hư hỏng', ins.damagedEquipmentsDescription ? h('span', { style: 'white-space:pre-line' }, ins.damagedEquipmentsDescription) : 'Không có'],
        ['Tiền bồi thường', `${money(ins.damageCompensationAmount)} ₫`], ['Ghi nhận lúc', dateTime(ins.inspectedAt)],
        ['Ảnh hiện trạng', ins.inspectionPhotoUrl ? h('a', { href: ins.inspectionPhotoUrl, target: '_blank', rel: 'noopener' }, 'Mở ảnh') : '–'],
      ])));
    } else if (c.status === 'INSPECTING') {
      body.push(h('p', { class: 'muted' }, 'Chưa ghi nhận hiện trạng phòng. Bấm «Ghi nhận hiện trạng» sau khi kiểm tra phòng cùng khách.'));
    }
    if (st && (ins || v || c.status === 'FINALIZED')) {
      body.push(card('Quyết toán tiền cọc', [
        st.finalMonthBilled ? null : h('div', { class: 'route-next', style: 'background:var(--warn-bg);border-color:#e8c46a' }, icon('alert', 20),
          h('p', { style: 'flex:1' }, h('b', null, 'Chưa có hóa đơn tháng cuối. '), ins?.meterRecorded
            ? 'Số điện nước cuối đã được ghi vào kỳ chỉ số khi lập biên bản. Hãy tính tiền và phát hành hóa đơn tháng này (tiền phòng tính theo ngày ở) rồi mới lập phiếu hoàn cọc, để khoản đó được trừ vào cọc.'
            : 'Số điện nước cuối chưa vào kỳ chỉ số nào. Tạo kỳ ghi chỉ số của tháng, lưu lại hiện trạng phòng, rồi tính tiền và phát hành hóa đơn tháng cuối trước khi lập phiếu hoàn cọc.'),
          h('a', { class: 'btn secondary sm', href: '#/billing' }, 'Mở Tính tiền')),
        st.depositCollected < st.contractDeposit ? h('div', { class: 'route-next', style: 'background:var(--warn-bg);border-color:#e8c46a' }, icon('alert', 20),
          h('p', null, h('b', null, 'Cọc chưa thu đủ. '), `Hợp đồng ghi ${money(st.contractDeposit)} ₫ nhưng mới thu ${money(st.depositCollected)} ₫. Chỉ hoàn phần đã thu.`)) : null,
        dl([
          ['Cọc theo hợp đồng', `${money(st.contractDeposit)} ₫`], ['Cọc đã thu (dùng để quyết toán)', `${money(st.originalDeposit)} ₫`], ['Công nợ chưa trả', `${money(st.debtTotal)} ₫`], ['Bồi thường hư hỏng', `${money(st.damageTotal)} ₫`],
          ['Trừ vào cọc (công nợ)', `${money(st.deductedDebt)} ₫`], ['Trừ vào cọc (hư hỏng)', `${money(st.deductedDamage)} ₫`],
          ['Hoàn lại cho khách', h('b', null, `${money(st.refundable)} ₫`)],
          ['Khách còn phải trả thêm', st.tenantMustPay > 0 ? h('b', { style: 'color:var(--danger)' }, `${money(st.tenantMustPay)} ₫`) : '0 ₫'],
        ]),
        st.tenantMustPay > 0 ? h('div', { class: 'route-next', style: 'background:var(--warn-bg);border-color:#e8c46a' }, icon('alert', 20), h('p', null, h('b', null, `Khoản vượt cọc: ${money(st.tenantMustPay)} ₫. `), 'Tiền cọc không đủ trừ hết công nợ và bồi thường nên không có tiền hoàn lại. Khoản vượt này khách phải trả thêm; hệ thống chỉ ghi nhận số tiền, hãy thu riêng và ghi nhận thanh toán ở hóa đơn.')) : null,
        st.unpaidInvoices.length ? h('div', null, h('h3', { style: 'margin:10px 0 6px' }, 'Hóa đơn còn nợ'),
          ...st.unpaidInvoices.map((i) => h('div', { class: 'muted' }, `${i.invoiceCode} · hạn ${date(i.dueDate)} · còn ${money(i.remainingAmount)} ₫`))) : null,
      ].filter(Boolean) as Node[]));
    }
    if (v) {
      body.push(card('Phiếu hoàn cọc', dl([
        ['Số tiền hoàn', h('b', null, `${money(v.actualRefundAmount)} ₫`)], ['Trạng thái', statusBadge(v.status)], ['Ngày hoàn', date(v.refundDate)],
      ])));
    }
    d.body.replaceChildren(...body);

    const act = (label: string, fn: () => void, cls = '') => h('button', { class: `btn ${cls}`, onclick: fn }, label);
    const foot: HTMLElement[] = [act('Đóng', d.close, 'secondary')];
    if (open && !v) {
      foot.push(act('Hủy yêu cầu', () => void (async () => {
        if (await confirmDialog('Hủy yêu cầu trả phòng', `Hủy yêu cầu trả phòng của phòng ${c.roomCode}?`, 'Hủy yêu cầu', true)) {
          await after(() => api.patch(`/api/checkout-requests/${id}/cancel`), 'Đã hủy yêu cầu');
        }
      })(), 'secondary'));
    }
    if (c.status === 'PENDING') {
      foot.push(act('Xác nhận lịch kiểm tra', () => confirmSchedule(c, () => after(async () => undefined, 'Đã xác nhận lịch kiểm tra phòng'))));
    }
    if (c.status === 'INSPECTING' && !v) {
      foot.push(act(ins ? 'Sửa hiện trạng' : 'Ghi nhận hiện trạng', () => inspectDialog(c, ins, () => after(async () => undefined, 'Đã ghi nhận hiện trạng phòng')), ins ? 'secondary' : ''));
      if (ins) foot.push(act('Lập phiếu hoàn cọc', () => void after(() => api.post(`/api/checkout-requests/${id}/refund-voucher`), 'Đã lập phiếu hoàn cọc')));
    }
    if (v && v.status === 'PENDING' && c.status === 'INSPECTING') {
      foot.push(act('Ghi nhận đã hoàn cọc', () => void (async () => {
        if (await confirmDialog('Ghi nhận hoàn cọc', `Xác nhận đã hoàn ${money(v.actualRefundAmount)} ₫ cho ${c.tenantName}? Các hóa đơn còn nợ sẽ được gạch bằng tiền cọc.`, 'Đã hoàn tiền')) {
          await after(() => api.post(`/api/refund-vouchers/${v.refundId}/pay`), 'Đã ghi nhận hoàn cọc');
        }
      })()));
    }
    if (v && v.status === 'PENDING' && c.status === 'INSPECTING') {
      foot.push(act('Hủy phiếu hoàn cọc', () => void (async () => {
        if (await confirmDialog('Hủy phiếu hoàn cọc', `Hủy phiếu hoàn ${money(v.actualRefundAmount)} ₫? Sau đó bạn sửa được hiện trạng phòng và lập phiếu mới. Chưa có tiền nào được hoàn.`, 'Hủy phiếu', true)) {
          await after(() => api.del(`/api/checkout-requests/${id}/refund-voucher`), 'Đã hủy phiếu hoàn cọc');
        }
      })(), 'secondary'));
    }
    if (v && v.status === 'REFUNDED' && c.status === 'INSPECTING') {
      foot.push(act('Thanh lý hợp đồng', () => void (async () => {
        if (await confirmDialog('Thanh lý hợp đồng', `Chấm dứt hợp đồng ${c.contractCode} và chuyển phòng ${c.roomCode} về «Trống»?`, 'Thanh lý', true)) {
          await after(() => api.post(`/api/checkout-requests/${id}/finalize`), 'Đã thanh lý hợp đồng, phòng về trạng thái Trống');
        }
      })()));
    }
    if (ins) foot.push(act('In biên bản bàn giao', () => void printHandover(id), 'secondary'));
    d.foot.replaceChildren(...foot);
  };
  await paint();
}

function confirmSchedule(c: Checkout, done: () => void): void {
  const d = openDialog('Xác nhận lịch kiểm tra phòng', { width: 480 });
  const err = h('div');
  const when = input({ type: 'date', value: c.expectedCheckoutDate, min: new Date().toISOString().slice(0, 10) });
  d.body.append(err, field('Ngày kiểm tra và trả phòng', when, 'Có thể đổi so với ngày khách đề nghị'));
  const ok = h('button', { class: 'btn' }, 'Xác nhận');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.patch(`/api/checkout-requests/${c.checkoutId}/confirm`, { expectedCheckoutDate: when.value }); d.close(); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function inspectDialog(c: Checkout, ins: Inspection | null, done: () => void): void {
  const d = openDialog(`Hiện trạng phòng ${c.roomCode}`, { width: 600 });
  const err = h('div');
  const elec = input({ type: 'number', min: '0', value: ins ? String(ins.finalElectricMeter) : '', class: 'input num-in', placeholder: 'Số điện cuối' });
  const water = input({ type: 'number', min: '0', value: ins ? String(ins.finalWaterMeter) : '', class: 'input num-in', placeholder: 'Số nước cuối' });
  const photo = input({ placeholder: 'Đường dẫn ảnh (không bắt buộc)', value: ins?.inspectionPhotoUrl ?? '' });
  const rows: Array<{ name: HTMLInputElement; cost: HTMLInputElement; el: HTMLElement }> = [];
  const list = h('div', { class: 'stack', style: 'gap:8px' });
  const total = h('b', null, '0 ₫');
  const sum = () => { total.textContent = `${money(rows.reduce((s, r) => s + (Number(r.cost.value) || 0), 0))} ₫`; };
  const addRow = (name = '', cost = '') => {
    const n = input({ placeholder: 'Thiết bị hư hỏng', value: name, maxlength: '100' });
    const k = input({ type: 'number', min: '0', step: '10000', value: cost, class: 'input num-in', placeholder: 'Tiền bồi thường' });
    const row = { name: n, cost: k, el: h('div', { style: 'display:grid;grid-template-columns:minmax(0,1fr) minmax(88px,130px) auto;gap:8px;align-items:center' }, n, k,
      h('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Xóa dòng', onclick: () => { rows.splice(rows.indexOf(row), 1); row.el.remove(); sum(); } }, icon('x', 16))) };
    k.addEventListener('input', sum);
    rows.push(row);
    list.appendChild(row.el);
    sum();
  };
  // nạp lại các dòng hư hỏng đã ghi: mỗi dòng có dạng «tên: số tiền»
  for (const line of (ins?.damagedEquipmentsDescription ?? '').split('\n').filter(Boolean)) {
    const i = line.lastIndexOf(': ');
    if (i > 0) addRow(line.slice(0, i), line.slice(i + 2));
  }
  d.body.append(err,
    h('div', { class: 'form-grid' }, field('Chỉ số điện cuối', elec), field('Chỉ số nước cuối', water)),
    h('div', null, h('h3', { style: 'margin:6px 0' }, 'Thiết bị hư hỏng'), list,
      h('div', { style: 'display:flex;justify-content:space-between;align-items:center;margin-top:8px' },
        h('button', { type: 'button', class: 'btn secondary', onclick: () => addRow() }, icon('plus', 16), 'Thêm thiết bị hư hỏng'),
        h('span', null, 'Tổng bồi thường: ', total))),
    field('Ảnh hiện trạng', photo));
  const ok = h('button', { class: 'btn' }, 'Lưu hiện trạng');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    err.replaceChildren();
    clearFieldErr(elec);
    clearFieldErr(water);
    if (elec.value === '' || water.value === '') { err.replaceChildren(formError('Nhập chỉ số điện và nước cuối.')); return; }
    const damages = rows.filter((r) => r.name.value.trim()).map((r) => ({ name: r.name.value.trim(), cost: Number(r.cost.value) || 0 }));
    try {
      await api.put(`/api/checkout-requests/${c.checkoutId}/inspection`, { finalElectricMeter: Number(elec.value), finalWaterMeter: Number(water.value), damages, inspectionPhotoUrl: photo.value.trim() || null });
      d.close();
      done();
    } catch (e) {
      // lỗi 400 «số cuối nhỏ hơn số kỳ trước»: hiện ngay cạnh ô sai thay vì chỉ ở đầu hộp thoại
      const m = e instanceof ApiError && e.status === 400 ? /Chi so (dien|nuoc) cuoi \((\d+)\) nho hon chi so ky truoc \((\d+)\)/.exec(e.message) : null;
      if (m) {
        const target = m[1] === 'dien' ? elec : water;
        fieldErr(target, `Số ${m[1] === 'dien' ? 'điện' : 'nước'} cuối (${m[2]}) nhỏ hơn chỉ số kỳ trước (${m[3]}). Kiểm tra lại số vừa nhập trên công tơ.`);
        target.focus();
      } else err.replaceChildren(formError(errText(e)));
    }
  }));
}

function clearFieldErr(inp: HTMLInputElement): void {
  inp.removeAttribute('aria-invalid');
  inp.parentElement?.querySelectorAll('.field-err').forEach((n) => n.remove());
}

function fieldErr(inp: HTMLInputElement, msg: string): void {
  clearFieldErr(inp);
  inp.setAttribute('aria-invalid', 'true');
  inp.parentElement?.append(h('span', { class: 'hint field-err', role: 'alert', style: 'color:var(--danger)' }, msg));
  inp.addEventListener('input', () => clearFieldErr(inp), { once: true });
}

const esc = (s: string | number | null | undefined) => String(s ?? '').replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch] as string));

/** Biên bản bàn giao phòng (UC107): mở cửa sổ in, người dùng in hoặc lưu thành PDF. */
async function printHandover(id: number): Promise<void> {
  try {
    const r = await api.get<Handover>(`/api/checkout-requests/${id}/handover-report`);
    const c = r.checkout, i = r.inspection, s = r.settlement, v = r.refundVoucher;
    const row = (k: string, val: string) => `<tr><th>${esc(k)}</th><td>${esc(val)}</td></tr>`;
    const html = `<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Biên bản bàn giao phòng ${esc(c.roomCode)}</title>
<style>body{font:15px/1.5 system-ui,Arial,sans-serif;margin:32px;color:#111}h1{font-size:20px;text-align:center;margin:0 0 4px}p.sub{text-align:center;margin:0 0 20px;color:#555}
h2{font-size:15px;margin:20px 0 6px;border-bottom:1px solid #999;padding-bottom:3px}table{border-collapse:collapse;width:100%}th{text-align:left;width:42%;font-weight:600;padding:4px 8px 4px 0;vertical-align:top}td{padding:4px 0}
.sign{display:flex;justify-content:space-around;margin-top:56px;text-align:center}.sign div{width:40%}</style></head><body>
<h1>BIÊN BẢN BÀN GIAO PHÒNG</h1><p class="sub">Phòng ${esc(c.roomCode)} · Hợp đồng ${esc(c.contractCode)}</p>
<h2>Thông tin chung</h2><table>${row('Khách hàng', c.tenantName)}${row('Ngày trả dự kiến', date(c.expectedCheckoutDate))}${row('Ngày trả thực tế', c.actualCheckoutDate ? date(c.actualCheckoutDate) : 'Chưa thanh lý')}</table>
<h2>Hiện trạng phòng</h2><table>${i ? row('Chỉ số điện cuối', String(i.finalElectricMeter)) + row('Chỉ số nước cuối', String(i.finalWaterMeter)) + row('Thiết bị hư hỏng', i.damagedEquipmentsDescription ?? 'Không có') + row('Tiền bồi thường', `${money(i.damageCompensationAmount)} đ`) : row('Hiện trạng', 'Chưa ghi nhận')}</table>
<h2>Quyết toán tiền cọc</h2><table>${row('Tiền cọc', `${money(s.originalDeposit)} đ`)}${row('Công nợ trừ vào cọc', `${money(s.deductedDebt)} đ`)}${row('Bồi thường trừ vào cọc', `${money(s.deductedDamage)} đ`)}${row('Hoàn lại cho khách', `${money(s.refundable)} đ`)}${row('Khách còn phải trả thêm', `${money(s.tenantMustPay)} đ`)}${v ? row('Phiếu hoàn cọc', v.status === 'REFUNDED' ? `Đã hoàn ngày ${date(v.refundDate)}` : 'Chưa hoàn tiền') : ''}</table>
<div class="sign"><div>Người thuê<br><br><br><br>${esc(c.tenantName)}</div><div>Chủ trọ / Quản lý<br><br><br><br></div></div>
<script>window.onload=function(){window.print()}<\/script></body></html>`;
    const w = window.open('', '_blank');
    if (!w) { toast('Trình duyệt chặn cửa sổ in, hãy cho phép cửa sổ bật lên.', 'err'); return; }
    w.document.write(html);
    w.document.close();
  } catch (e) { toast(errText(e), 'err'); }
}
