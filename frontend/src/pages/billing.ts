import { h, busy, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, Page, PROPERTY_ID } from '../lib/api';
import { getMonth } from '../lib/ctx';
import { date, money, monthLabel } from '../lib/format';
import type { Anomaly, Calculation, EligibleContract, Fee, Invoice, Issue, Period } from '../data/types';
import { confirmDialog, dl, emptyState, field, formError, input, openDialog, pageHead, panel, plate, statusBadge, table, toast } from '../ui/kit';
import { routeLine, Stop } from '../ui/route';

void ({} as Page<unknown>);

export async function billingPage(view: HTMLElement): Promise<void> {
  const month = getMonth();
  const periods = await api.get<Period[]>(`/api/billing/periods?propertyId=${PROPERTY_ID}`);
  const has = periods.some((p) => p.monthYear === month);
  const create = h('button', { class: 'btn', disabled: has, title: has ? `${monthLabel(month)} đã có kỳ tính tiền, đổi tháng ở thanh trên để tạo kỳ mới` : null, onclick: () => createPeriod(month) }, icon('plus', 16), `Tạo kỳ ${monthLabel(month)}`);

  const mine = periods.find((p) => p.monthYear === month);
  const callout = mine
    ? h('div', { class: 'route-next', style: 'margin:0' }, icon('route', 20),
        h('p', null, h('b', null, `${monthLabel(month)}: `), `kỳ đã có, ${mine.roomCount ? `${mine.roomCount} phòng, ${money(mine.totalAmount)} ₫` : 'chưa tính tiền'}.`),
        statusBadge(mine.status), h('a', { class: 'btn plate', href: `#/billing/${mine.billingPeriodId}` }, 'Mở kỳ này', icon('arrowRight', 16)))
    : null;
  mount(view,
    pageHead('Tính tiền tháng', has ? `${monthLabel(month)} đã có kỳ tính tiền (mở ở bảng dưới). Đổi tháng ở thanh trên để tạo kỳ tháng khác.` : 'Mỗi tháng một kỳ: kiểm tra chỉ số, tính tiền, duyệt, rồi phát hành hóa đơn.', [create]),
    callout,
    panel('Các kỳ tính tiền', table<Period>([
      { key: 'm', label: 'Tháng', render: (p) => h('b', null, monthLabel(p.monthYear)), sort: (a, b) => a.monthYear.localeCompare(b.monthYear) },
      { key: 's', label: 'Trạng thái', render: (p) => statusBadge(p.status) },
      { key: 'r', label: 'Số phòng', num: true, render: (p) => p.roomCount },
      { key: 't', label: 'Tổng phải thu (₫)', num: true, render: (p) => money(p.totalAmount) },
      { key: 'c', label: 'Tạo lúc', render: (p) => date(p.creationDate) },
      { key: 'go', label: '', render: (p) => h('a', { class: 'btn secondary sm', href: `#/billing/${p.billingPeriodId}` }, 'Mở', icon('right', 14)) },
    ], periods, {
      onRow: (p) => (location.hash = `#/billing/${p.billingPeriodId}`),
      empty: emptyState('Chưa có kỳ tính tiền nào', `Tạo kỳ ${monthLabel(month)} để bắt đầu: hệ thống lấy các hợp đồng đang hiệu lực trong tháng.`, create.cloneNode(true) as HTMLElement),
    }), { flush: true }),
  );
  const emptyBtn = view.querySelector('.empty .btn');
  emptyBtn?.addEventListener('click', () => createPeriod(month));
}

async function createPeriod(month: string): Promise<void> {
  const d = openDialog(`Tạo kỳ ${monthLabel(month)}`);
  const err = h('div');
  d.body.append(err, h('p', null, 'Kỳ mới ở trạng thái Nháp. Hệ thống lấy các hợp đồng có hiệu lực trong tháng, kể cả vào ở hoặc trả phòng giữa tháng (tính theo số ngày thực tế).'));
  const go = h('button', { class: 'btn' }, 'Tạo kỳ');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), go);
  go.addEventListener('click', () => busy(go, async () => {
    try {
      const r = await api.post<{ period: Period; contracts: EligibleContract[] }>('/api/billing/periods', { propertyId: PROPERTY_ID, monthYear: month });
      d.close();
      toast(`Đã tạo kỳ với ${r.contracts.length} hợp đồng`);
      location.hash = `#/billing/${r.period.billingPeriodId}`;
    } catch (e) {
      err.replaceChildren(formError(errText(e)));
    }
  }));
}

export async function billingDetailPage(view: HTMLElement, params: Record<string, string>): Promise<void> {
  const id = params.id;
  const [calc, issues, anomalies] = await Promise.all([
    api.get<Calculation>(`/api/billing/periods/${id}`),
    api.get<Issue[]>(`/api/billing/periods/${id}/validate`),
    api.get<Anomaly[]>(`/api/billing/periods/${id}/anomalies`),
  ]);
  const p = calc.period;
  const invoices = await api.get<Invoice[]>(`/api/invoices?propertyId=${PROPERTY_ID}&monthYear=${p.monthYear}`);
  const draft = p.status === 'DRAFT';
  const reload = () => billingDetailPage(view, params);

  const calcBtn = h('button', { class: draft && p.roomCount ? 'btn secondary' : 'btn', disabled: !draft }, icon('calc', 16), p.roomCount ? 'Tính lại' : 'Tính tiền');
  calcBtn.addEventListener('click', async () => {
    if (p.roomCount && !(await confirmDialog('Tính lại kỳ', 'Các dòng tự tính sẽ được tính lại theo dữ liệu mới nhất, mọi chỉnh sửa tay trên dòng tự tính sẽ mất. Khoản bạn đã «Thêm khoản» thì được giữ lại. Tiếp tục?', 'Tính lại'))) return;
    await calcNow();
  });
  const calcNow = () => busy(calcBtn, async () => {
    try {
      const r = await api.post<Calculation>(`/api/billing/periods/${id}/calculate`);
      toast(`Đã tính ${r.fees.length} phòng${r.warnings.length ? `, ${r.warnings.length} cảnh báo` : ''}`);
      await reload();
    } catch (e) { toast(errText(e), 'err'); }
  });
  const approveBtn = h('button', { class: 'btn', disabled: !draft || !p.roomCount }, icon('check', 16), 'Duyệt kỳ');
  approveBtn.addEventListener('click', async () => {
    if (!(await confirmDialog('Duyệt kỳ tính tiền', `Sau khi duyệt, ${monthLabel(p.monthYear)} bị khóa, không sửa hoặc tính lại được. Tiếp tục?`, 'Duyệt kỳ'))) return;
    try {
      await api.post(`/api/billing/periods/${id}/approve`);
      toast('Đã duyệt kỳ');
      await reload();
    } catch (e) { toast(errText(e), 'err'); }
  });
  const issueBtn = h('button', { class: 'btn plate', disabled: p.status === 'DRAFT' || invoices.length >= calc.fees.length && calc.fees.length > 0 }, icon('send', 16), 'Phát hành hóa đơn');
  issueBtn.addEventListener('click', () => busy(issueBtn, async () => {
    try {
      const list = await api.post<Invoice[]>('/api/invoices/batch', { billingPeriodId: Number(id) });
      toast(`Đã phát hành ${list.length} hóa đơn`);
      await reload();
    } catch (e) { toast(errText(e), 'err'); }
  }));

  const paidCount = invoices.filter((i) => i.paymentStatus === 'PAID').length;
  const flags = [true, p.roomCount > 0, !draft, invoices.length > 0, invoices.length > 0 && paidCount === invoices.length];
  const cur = flags.findIndex((f) => !f);
  const st = (i: number): Stop['state'] => (cur === -1 || i < cur ? 'done' : i === cur ? 'current' : 'todo');
  const stuckAt = [0, 0, p.roomCount, Math.max(p.roomCount - invoices.length, 0), invoices.length - paidCount];
  const stops: Stop[] = [
    { name: 'Tạo kỳ', sub: 'Đã tạo', state: st(0) },
    { name: 'Tính tiền', sub: p.roomCount ? `${p.roomCount} phòng` : 'Chưa tính', state: st(1) },
    { name: 'Duyệt kỳ', sub: draft ? 'Chờ duyệt' : 'Đã duyệt', state: st(2) },
    { name: 'Phát hành', sub: invoices.length ? `${invoices.length} hóa đơn` : 'Chưa phát hành', state: st(3) },
    { name: 'Thu tiền', sub: invoices.length ? `${paidCount}/${invoices.length} đã trả` : '–', state: st(4), href: '#/payments' },
  ];
  if (cur >= 0) stops[cur].stuck = stuckAt[cur];
  const why = !draft ? 'Kỳ đã duyệt nên bị khóa: không tính lại hoặc duyệt lại được.' : !p.roomCount ? 'Bấm Tính tiền trước, rồi mới duyệt được.' : '';

  view.replaceChildren(
    h('p', null, h('a', { href: '#/billing' }, '← Các kỳ tính tiền')),
    pageHead(`Kỳ ${monthLabel(p.monthYear).toLowerCase()}`, why, [statusBadge(p.status), calcBtn, approveBtn, issueBtn]),
    h('div', { class: 'stack' },
      h('section', { class: 'panel route' }, routeLine(stops)),
      issues.length || calc.warnings.length ? issuesPanel(issues, calc.warnings) : null,
      feesPanel(calc.fees, draft, reload),
      anomalies.length ? panel('Phòng có tiền cao bất thường', table<Anomaly>([
        { key: 'r', label: 'Phòng', render: (a) => plate(a.roomCode) },
        { key: 't', label: 'Kỳ này (₫)', num: true, render: (a) => money(a.total) },
        { key: 'a', label: 'Trung bình các kỳ trước (₫)', num: true, render: (a) => money(a.average) },
        { key: 'x', label: 'Mức tăng', num: true, render: (a) => `+${Math.round((a.total / a.average - 1) * 100)}%` },
      ], anomalies), { sub: 'Vượt 150% trung bình của chính phòng đó. Kiểm tra chỉ số trước khi duyệt.', flush: true }) : null,
    ),
  );
}

function issuesPanel(issues: Issue[], warnings: Issue[]): HTMLElement {
  const all = [...issues.map((i) => ({ ...i, kind: 'Kiểm tra' })), ...warnings.map((w) => ({ ...w, kind: 'Khi tính' }))];
  return panel('Cần xem lại trước khi duyệt', h('ul', null, ...all.map((i) => h('li', { class: 'list-row' },
    h('span', { class: 'st warn' }, icon('warn', 14), i.kind), i.roomCode ? plate(i.roomCode) : null, h('div', { class: 'grow' }, i.message)))), { flush: true });
}

function feesPanel(fees: Fee[], draft: boolean, reload: () => void): HTMLElement {
  if (!fees.length) return panel('Bảng tính tiền', emptyState('Chưa tính tiền', 'Bấm Tính tiền để tạo bảng tính cho từng phòng có hợp đồng trong tháng.'));
  const sum = (f: (x: Fee) => number) => fees.reduce((a, x) => a + f(x), 0);
  const tdn = (v: number) => h('td', { class: 'num' }, money(v));
  return panel('Bảng tính tiền từng phòng', table<Fee>([
    { key: 'r', label: 'Phòng', render: (f) => plate(f.roomCode), sort: (a, b) => a.roomCode.localeCompare(b.roomCode) },
    { key: 'p', label: 'Người thuê', render: (f) => f.tenantName },
    { key: 'rent', label: 'Tiền phòng', num: true, render: (f) => money(f.rentAmount) },
    { key: 'e', label: 'Điện', num: true, render: (f) => money(f.electricAmount) },
    { key: 'w', label: 'Nước', num: true, render: (f) => money(f.waterAmount) },
    { key: 's', label: 'Dịch vụ', num: true, render: (f) => money(f.serviceAmount) },
    { key: 'x', label: 'Phụ thu/giảm', num: true, render: (f) => money(f.surchargeAmount - f.discountAmount + f.latePenaltyAmount) },
    { key: 't', label: 'Tổng (₫)', num: true, render: (f) => h('b', null, money(f.totalCalculatedAmount)), sort: (a, b) => a.totalCalculatedAmount - b.totalCalculatedAmount },
  ], fees, {
    onRow: (f) => openFee(f.calculatedFeeId, draft, reload),
    footer: [h('td', { colspan: 2 }, 'Tổng cộng'), tdn(sum((f) => f.rentAmount)), tdn(sum((f) => f.electricAmount)), tdn(sum((f) => f.waterAmount)), tdn(sum((f) => f.serviceAmount)),
      tdn(sum((f) => f.surchargeAmount - f.discountAmount + f.latePenaltyAmount)), tdn(sum((f) => f.totalCalculatedAmount))],
  }), { sub: draft ? 'Bấm một phòng để xem và sửa từng dòng phí' : 'Kỳ đã khóa, chỉ xem', flush: true });
}

async function openFee(feeId: number, draft: boolean, reload: () => void): Promise<void> {
  const fee = await api.get<Fee>(`/api/billing/fees/${feeId}`).catch((e) => { toast(errText(e), 'err'); return null; });
  if (!fee) return;
  const d = openDialog(`Phòng ${fee.roomCode} · ${fee.tenantName}`, { drawer: true });
  const err = h('div');
  const render = (f: Fee) => {
    mount(d.body, err, table<Fee['items'][number]>([
      { key: 'n', label: 'Khoản', render: (i) => i.itemName.startsWith('✎ ') ? h('span', null, i.itemName.slice(2), ' ', h('span', { class: 'st info' }, 'Nhập tay')) : i.itemName },
      { key: 'q', label: 'Số lượng', num: true, render: (i) => draft ? qtyInput(i, f) : i.quantity },
      { key: 'u', label: 'Đơn giá', num: true, render: (i) => draft ? priceInput(i, f) : money(i.unitPrice) },
      { key: 's', label: 'Thành tiền', num: true, render: (i) => money(i.subtotal) },
      { key: 'x', label: '', render: (i) => (draft ? h('button', { class: 'btn secondary sm', 'aria-label': `Xóa dòng ${i.itemName}`, onclick: () => void removeItem(i.detailId) }, 'Xóa') : '') },
    ], f.items, { footer: [h('td', { colspan: 3 }, 'Tổng'), h('td', { class: 'num' }, money(f.totalCalculatedAmount)), h('td')] }),
    draft ? h('div', { class: 'stack', style: 'gap:10px' },
      h('button', { class: 'btn secondary', style: 'align-self:flex-start', onclick: () => addItemDialog(f.calculatedFeeId, (nf) => { render(nf); toast('Đã thêm khoản'); reload(); }) }, icon('plus', 16), 'Thêm khoản'),
      h('p', { class: 'muted' }, 'Sửa số lượng hoặc đơn giá rồi bấm ra ngoài ô để lưu. Dòng giảm trừ có đơn giá âm. Khoản «Nhập tay» được giữ lại khi tính lại kỳ; chỉnh sửa trên dòng tự tính thì không. Số lượng gửi xe và theo đầu người lấy từ hồ sơ ở Khách thuê và Phòng.')) : null);
  };
  const removeItem = async (detailId: number) => {
    try {
      const f = await api.del<Fee>(`/api/billing/fee-items/${detailId}`);
      render(f);
      toast('Đã xóa dòng');
      reload();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  };
  const save = async (detailId: number, body: { quantity?: number; unitPrice?: number }) => {
    try {
      const f = await api.put<Fee>(`/api/billing/fee-items/${detailId}`, body);
      render(f);
      toast('Đã lưu');
      reload();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  };
  const qtyInput = (i: Fee['items'][number], _f: Fee) => numInput(i.quantity, (v) => save(i.detailId, { quantity: v }));
  const priceInput = (i: Fee['items'][number], _f: Fee) => numInput(i.unitPrice, (v) => save(i.detailId, { unitPrice: v }));
  render(fee);
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'));
}

function addItemDialog(feeId: number, done: (f: Fee) => void): void {
  const d = openDialog('Thêm khoản vào phòng này');
  const err = h('div');
  const name = input({ placeholder: 'Ví dụ: Sửa máy lạnh, Khuyến mãi tháng 12' });
  const qty = input({ type: 'number', min: '0', step: 'any', value: '1', class: 'input num-in' });
  const price = input({ type: 'number', step: '1000', class: 'input num-in', placeholder: 'Số âm là giảm trừ' });
  d.body.append(err, field('Tên khoản', name), h('div', { class: 'form-grid' }, field('Số lượng', qty), field('Đơn giá (₫)', price, 'Số dương là phụ thu, số âm là giảm trừ')));
  const ok = h('button', { class: 'btn' }, 'Thêm');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { const f = await api.post<Fee>(`/api/billing/fees/${feeId}/items`, { itemName: name.value, quantity: Number(qty.value), unitPrice: Number(price.value) }); d.close(); done(f); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

function numInput(v: number, onSave: (n: number) => void): HTMLElement {
  const i = input({ type: 'number', step: 'any', value: String(v), class: 'input num-in', style: 'width:130px;min-height:32px;padding:4px 8px', 'aria-label': 'Giá trị' });
  i.addEventListener('change', () => {
    const n = Number(i.value);
    if (Number.isFinite(n) && n !== v) onSave(n);
  });
  return i;
}

void field; void dl;
