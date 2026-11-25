import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, loadSession, PROPERTY_ID } from '../lib/api';
import { date, money, isoDay } from '../lib/format';
import { loadLive } from '../data/live';
import { confirmDialog, emptyState, field, formError, input, openDialog, pageHead, select, table, toast } from '../ui/kit';

interface Plan { planId: number; planName: string; electricUnitPrice: number; waterUnitPrice: number; effectiveStartDate: string; effectiveEndDate: string | null }
interface Svc { rateId: number; propertyId: number; serviceName: string; chargeType: string; unitPrice: number; active: boolean }
interface Custom { customRateId: number; roomId: number; rateName: string; customPrice: number; notes: string | null; kind: string }
interface Bank { bankAccountId: number; propertyId: number; bankName: string; accountNumber: string; accountHolder: string; qrImageUrl: string | null; isDefault: boolean; isActive: boolean }
interface RoomType { roomTypeId: number; propertyId: number; typeName: string; description: string | null; defaultRentPrice: number; defaultDepositAmount: number }

const CHARGE: Record<string, string> = {
  FIXED_ROOM: 'Cố định mỗi phòng', PER_PERSON: 'Theo đầu người', PER_VEHICLE: 'Theo số xe', PER_METER_UNIT: 'Theo đơn vị công tơ (chưa tự tính)',
};
const TABS = [['rates', 'Đơn giá điện nước'], ['services', 'Dịch vụ hàng tháng'], ['custom', 'Phụ thu và giảm trừ'], ['room-types', 'Loại phòng'], ['bank', 'Ngân hàng và mã QR']] as const;

export async function settingsPage(view: HTMLElement, _p: Record<string, string>, query: URLSearchParams): Promise<void> {
  let tab = query.get('tab') ?? 'rates';
  const body = h('div');
  const tabs = h('div', { class: 'tabs', role: 'tablist' });
  const paint = async () => {
    tabs.replaceChildren(...TABS.map(([k, l]) => h('button', { role: 'tab', 'aria-selected': String(k === tab), onclick: () => { tab = k; void paint(); } }, l)));
    body.replaceChildren(h('div', { class: 'panel-body' }, h('div', { class: 'skel', style: 'height:80px' })));
    try {
      body.replaceChildren(tab === 'rates' ? await ratesTab(paint) : tab === 'services' ? await servicesTab(paint) : tab === 'custom' ? await customTab() : tab === 'room-types' ? await roomTypesTab(paint) : await bankTab(paint));
    } catch (e) { body.replaceChildren(emptyState('Chưa tải được', errText(e))); }
  };
  view.replaceChildren(pageHead('Cài đặt', 'Đơn giá, dịch vụ và tài khoản nhận tiền. Tính tiền hàng tháng đọc trực tiếp các mục này.'), h('section', { class: 'panel panel-flush' }, tabs, body));
  await paint();
}

// ------------------------------------------------------------------ đơn giá điện nước
async function ratesTab(reload: () => void): Promise<HTMLElement> {
  const plans = await api.get<Plan[]>('/api/service-rate-plans');
  const today = isoDay();
  const eff = plans.filter((p) => p.effectiveStartDate <= today && (!p.effectiveEndDate || p.effectiveEndDate >= today)).sort((a, b) => b.effectiveStartDate.localeCompare(a.effectiveStartDate))[0];
  return h('div', null,
    h('div', { class: 'toolbar' }, h('p', { class: 'grow' }, eff ? `Đang áp dụng: ${eff.planName}, điện ${money(eff.electricUnitPrice)} ₫/kWh, nước ${money(eff.waterUnitPrice)} ₫/m³.` : 'Chưa có bảng giá nào đang áp dụng, tiền điện nước sẽ bằng 0.'),
      h('button', { class: 'btn', onclick: () => planDialog(null, reload) }, icon('plus', 16), 'Thêm bảng giá')),
    table<Plan>([
      { key: 'n', label: 'Bảng giá', render: (p) => h('b', null, p.planName) },
      { key: 'e', label: 'Điện (₫/kWh)', num: true, render: (p) => money(p.electricUnitPrice) },
      { key: 'w', label: 'Nước (₫/m³)', num: true, render: (p) => money(p.waterUnitPrice) },
      { key: 's', label: 'Áp dụng từ', render: (p) => date(p.effectiveStartDate) },
      { key: 'x', label: 'Đến', render: (p) => (p.effectiveEndDate ? date(p.effectiveEndDate) : 'Không giới hạn') },
      { key: 'a', label: '', render: (p) => h('button', { class: 'btn secondary sm', onclick: () => planDialog(p, reload) }, icon('edit', 14), 'Sửa') },
    ], plans, { empty: emptyState('Chưa có bảng giá', 'Thêm bảng giá điện nước để tính tiền hàng tháng.') }),
  );
}

function planDialog(p: Plan | null, done: () => void): void {
  const d = openDialog(p ? 'Sửa bảng giá' : 'Thêm bảng giá');
  const err = h('div');
  const name = input({ value: p?.planName ?? '', placeholder: 'Ví dụ: Bảng giá 2027' });
  const e = input({ type: 'number', min: '0', step: '100', value: String(p?.electricUnitPrice ?? ''), class: 'input num-in' });
  const w = input({ type: 'number', min: '0', step: '500', value: String(p?.waterUnitPrice ?? ''), class: 'input num-in' });
  const from = input({ type: 'date', value: p?.effectiveStartDate ?? isoDay() });
  const to = input({ type: 'date', value: p?.effectiveEndDate ?? '' });
  d.body.append(err, field('Tên bảng giá', name), h('div', { class: 'form-grid' }, field('Giá điện (₫/kWh)', e), field('Giá nước (₫/m³)', w), field('Áp dụng từ', from), field('Đến ngày (bỏ trống = không giới hạn)', to)));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const body = { planName: name.value.trim(), electricUnitPrice: Number(e.value), waterUnitPrice: Number(w.value), effectiveStartDate: from.value, effectiveEndDate: to.value || null };
    try { await (p ? api.put(`/api/service-rate-plans/${p.planId}`, body) : api.post('/api/service-rate-plans', body)); d.close(); toast('Đã lưu bảng giá'); done(); }
    catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}

// ------------------------------------------------------------------ dịch vụ hàng tháng
async function servicesTab(reload: () => void): Promise<HTMLElement> {
  const list = await api.get<Svc[]>(`/api/property-service-rates?propertyId=${PROPERTY_ID}`);
  return h('div', null,
    h('div', { class: 'toolbar' }, h('p', { class: 'grow muted' }, 'Mỗi dịch vụ cộng vào tiền hàng tháng của từng phòng: cố định mỗi phòng, theo đầu người hoặc theo số xe.'),
      h('button', { class: 'btn', onclick: () => svcDialog(null, reload) }, icon('plus', 16), 'Thêm dịch vụ')),
    table<Svc>([
      { key: 'n', label: 'Dịch vụ', render: (s) => h('b', null, s.serviceName) },
      { key: 't', label: 'Cách tính', render: (s) => CHARGE[s.chargeType] ?? s.chargeType },
      { key: 'p', label: 'Đơn giá (₫)', num: true, render: (s) => money(s.unitPrice) },
      { key: 's', label: 'Trạng thái', render: (s) => (s.active ? h('span', { class: 'st ok' }, icon('check', 14), 'Đang áp dụng') : h('span', { class: 'st off' }, icon('x', 14), 'Đã tắt')) },
      { key: 'a', label: '', render: (s) => h('div', { class: 'row-actions' },
        h('button', { class: 'btn secondary sm', onclick: () => svcDialog(s, reload) }, icon('edit', 14), 'Sửa'),
        h('button', { class: 'btn secondary sm', onclick: async () => {
          if (!(await confirmDialog('Xóa dịch vụ', `Xóa «${s.serviceName}»? Các kỳ đã tính không bị ảnh hưởng.`, 'Xóa', true))) return;
          try { await api.del(`/api/property-service-rates/${s.rateId}`); toast('Đã xóa'); reload(); } catch (x) { toast(errText(x), 'err'); }
        } }, 'Xóa')) },
    ], list, { empty: emptyState('Chưa có dịch vụ', 'Thêm Internet, vệ sinh, gửi xe... để cộng vào tiền hàng tháng.') }),
  );
}

function svcDialog(s: Svc | null, done: () => void): void {
  const d = openDialog(s ? 'Sửa dịch vụ' : 'Thêm dịch vụ');
  const err = h('div');
  const name = input({ value: s?.serviceName ?? '', placeholder: 'Ví dụ: Internet' });
  const type = select(Object.entries(CHARGE).filter(([k]) => k !== 'PER_METER_UNIT').map(([k, l]) => [k, l] as [string, string]), s?.chargeType ?? 'FIXED_ROOM');
  const price = input({ type: 'number', min: '0', step: '1000', value: String(s?.unitPrice ?? ''), class: 'input num-in' });
  const active = h('input', { type: 'checkbox', checked: s ? s.active : true }) as HTMLInputElement;
  const impact = h('div', { class: 'route-next', style: 'margin:0' }, icon('info', 18), h('p', { style: 'flex:1' }, 'Nhập đơn giá để xem dịch vụ này cộng thêm bao nhiêu mỗi tháng.'));
  const UNIT: Record<string, string> = { FIXED_ROOM: 'phòng', PER_PERSON: 'người (1 người đứng tên + người ở cùng đã khai)', PER_VEHICLE: 'xe đã đăng ký' };
  let timer: number | undefined;
  const preview = () => {
    clearTimeout(timer);
    timer = window.setTimeout(async () => {
      const p = Number(price.value);
      if (!(p >= 0) || price.value === '') return;
      try {
        const r = await api.get<{ rooms: number; units: number; monthlyAmount: number }>(`/api/billing/service-preview?chargeType=${type.value}&unitPrice=${p}`);
        impact.querySelector('p')!.textContent = `Áp dụng cho ${r.units} ${UNIT[type.value] ?? 'đơn vị'} ở ${r.rooms} phòng đang thuê: thêm ${money(r.monthlyAmount)} ₫ mỗi tháng cho cả khu. Chỉ áp dụng cho kỳ tính tiền chưa duyệt (Nháp), kỳ đã duyệt giữ nguyên.`;
      } catch { /* không xem trước được thì thôi */ }
    }, 250);
  };
  type.addEventListener('change', preview);
  price.addEventListener('input', preview);
  if (s) preview();
  d.body.append(err, field('Tên dịch vụ', name), field('Cách tính', type), field('Đơn giá (₫)', price), impact, h('label', { style: 'display:flex;gap:8px;align-items:center;font-weight:500' }, active, 'Đang áp dụng'));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const body = { propertyId: PROPERTY_ID, serviceName: name.value.trim(), chargeType: type.value, unitPrice: Number(price.value), active: active.checked, changedByUserId: loadSession()?.userId ?? null };
    try { await (s ? api.put(`/api/property-service-rates/${s.rateId}`, body) : api.post('/api/property-service-rates', body)); d.close(); toast('Đã lưu dịch vụ'); done(); }
    catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}

// ------------------------------------------------------------------ phụ thu / giảm trừ từng phòng
async function customTab(): Promise<HTMLElement> {
  const { rooms } = await loadLive();
  const holder = h('div');
  const sel = select(rooms.map((r) => [String(r.roomId), `${r.code}${r.tenantName ? ' · ' + r.tenantName : ''}`] as [string, string]), String(rooms[0]?.roomId ?? ''));
  const load = async () => {
    const list = await api.get<Custom[]>(`/api/room-custom-rates?roomId=${sel.value}`);
    holder.replaceChildren(table<Custom>([
      { key: 'n', label: 'Khoản', render: (c) => h('b', null, c.rateName) },
      { key: 'k', label: 'Loại', render: (c) => (c.customPrice < 0 ? h('span', { class: 'st ok' }, 'Giảm trừ') : h('span', { class: 'st warn' }, 'Phụ thu')) },
      { key: 'p', label: 'Số tiền (₫)', num: true, render: (c) => money(c.customPrice) },
      { key: 'o', label: 'Ghi chú', render: (c) => c.notes ?? '–' },
      { key: 'a', label: '', render: (c) => h('div', { class: 'row-actions' },
        h('button', { class: 'btn secondary sm', onclick: () => customDialog(Number(sel.value), c, load) }, icon('edit', 14), 'Sửa'),
        h('button', { class: 'btn secondary sm', onclick: async () => { try { await api.del(`/api/room-custom-rates/${c.customRateId}`); toast('Đã xóa'); void load(); } catch (x) { toast(errText(x), 'err'); } } }, 'Xóa')) },
    ], list, { empty: emptyState('Phòng này không có phụ thu hay giảm trừ', 'Khoản dương là phụ thu (ví dụ máy lạnh), khoản âm là giảm trừ (ví dụ khuyến mãi). Cộng vào tiền hàng tháng của phòng.') }));
  };
  sel.addEventListener('change', () => void load());
  void load();
  return h('div', null,
    h('div', { class: 'toolbar' }, h('div', { style: 'min-width:240px' }, sel), h('span', { class: 'grow' }), h('button', { class: 'btn', onclick: () => customDialog(Number(sel.value), null, load) }, icon('plus', 16), 'Thêm khoản')),
    holder);
}

function customDialog(roomId: number, c: Custom | null, done: () => void): void {
  const d = openDialog(c ? 'Sửa khoản' : 'Thêm khoản cho phòng');
  const err = h('div');
  const name = input({ value: c?.rateName ?? '', placeholder: 'Ví dụ: Máy lạnh riêng, Khuyến mãi tháng 12' });
  const price = input({ type: 'number', step: '1000', value: String(c?.customPrice ?? ''), class: 'input num-in' });
  const notes = input({ value: c?.notes ?? '' });
  d.body.append(err, field('Tên khoản', name), field('Số tiền (₫)', price, 'Số dương là phụ thu, số âm là giảm trừ'), field('Ghi chú', notes));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const body = { roomId, rateName: name.value.trim(), customPrice: Number(price.value), notes: notes.value.trim() || null };
    try { await (c ? api.put(`/api/room-custom-rates/${c.customRateId}`, body) : api.post('/api/room-custom-rates', body)); d.close(); toast('Đã lưu'); done(); }
    catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}

// ------------------------------------------------------------------ loại phòng
async function roomTypesTab(reload: () => void): Promise<HTMLElement> {
  const list = await api.get<RoomType[]>(`/api/chi/room-types?propertyId=${PROPERTY_ID}`);
  return h('div', null,
    h('div', { class: 'toolbar' }, h('p', { class: 'grow muted' }, 'Giá và tiền cọc mặc định dùng khi lập phòng/hợp đồng mới.'),
      h('button', { class: 'btn', onclick: () => roomTypeDialog(null, reload) }, icon('plus', 16), 'Thêm loại phòng')),
    table<RoomType>([
      { key: 'n', label: 'Tên loại', render: (x) => h('b', null, x.typeName) },
      { key: 'r', label: 'Giá thuê mặc định', num: true, render: (x) => `${money(x.defaultRentPrice)} ₫` },
      { key: 'd', label: 'Cọc mặc định', num: true, render: (x) => `${money(x.defaultDepositAmount)} ₫` },
      { key: 'x', label: 'Mô tả', render: (x) => x.description ?? '–' },
      { key: 'a', label: '', render: (x) => h('div', { class: 'row-actions' },
        h('button', { class: 'btn secondary sm', onclick: () => roomTypeDialog(x, reload) }, icon('edit', 14), 'Sửa'),
        h('button', { class: 'btn secondary sm', onclick: async () => { try { await api.del(`/api/chi/room-types/${x.roomTypeId}`); toast('Đã xóa loại phòng'); reload(); } catch (e) { toast(errText(e), 'err'); } } }, 'Xóa')) },
    ], list, { empty: emptyState('Chưa có loại phòng', 'Thêm loại phòng để quản lý giá mặc định theo từng kiểu phòng.') }),
  );
}

function roomTypeDialog(x: RoomType | null, done: () => void): void {
  const d = openDialog(x ? 'Sửa loại phòng' : 'Thêm loại phòng');
  const err = h('div');
  const name = input({ value: x?.typeName ?? '', placeholder: 'Ví dụ: Phòng đơn' });
  const rent = input({ type: 'number', min: '0', step: '10000', value: String(x?.defaultRentPrice ?? ''), class: 'input num-in' });
  const dep = input({ type: 'number', min: '0', step: '10000', value: String(x?.defaultDepositAmount ?? ''), class: 'input num-in' });
  const desc = input({ value: x?.description ?? '', placeholder: 'Mô tả ngắn' });
  d.body.append(err, h('div', { class: 'form-grid' }, field('Tên loại phòng', name), field('Giá thuê mặc định (₫)', rent), field('Tiền cọc mặc định (₫)', dep)), field('Mô tả', desc));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try {
      const body = { propertyId: PROPERTY_ID, typeName: name.value.trim(), description: desc.value.trim() || null, defaultRentPrice: Number(rent.value), defaultDepositAmount: Number(dep.value) };
      await (x ? api.put(`/api/chi/room-types/${x.roomTypeId}`, body) : api.post('/api/chi/room-types', body));
      d.close(); toast('Đã lưu loại phòng'); done();
    } catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

async function bankTab(reload: () => void): Promise<HTMLElement> {
  const list = await api.get<Bank[]>(`/api/properties/${PROPERTY_ID}/bank-accounts`);
  return h('div', null,
    h('div', { class: 'toolbar' }, h('p', { class: 'grow muted' }, 'Tài khoản mặc định được dùng khi tạo mã QR cho người thuê. Tải ảnh QR của ngân hàng để người thuê quét.'),
      h('button', { class: 'btn', onclick: () => bankDialog(null, reload) }, icon('plus', 16), 'Thêm tài khoản')),
    table<Bank>([
      { key: 'b', label: 'Ngân hàng', render: (b) => h('b', null, b.bankName) },
      { key: 'n', label: 'Số tài khoản', render: (b) => b.accountNumber },
      { key: 'h', label: 'Chủ tài khoản', render: (b) => b.accountHolder },
      { key: 'q', label: 'Ảnh QR', render: (b) => (b.qrImageUrl ? h('img', { src: b.qrImageUrl, alt: 'QR', style: 'height:48px;border:1px solid var(--line);border-radius:6px' }) : h('span', { class: 'muted' }, 'Chưa có')) },
      { key: 'd', label: 'Mặc định', render: (b) => (b.isDefault ? h('span', { class: 'st ok' }, icon('check', 14), 'Mặc định') : '') },
      { key: 'a', label: '', render: (b) => bankActions(b, reload) },
    ], list, { empty: emptyState('Chưa có tài khoản ngân hàng', 'Thêm tài khoản để người thuê có nơi chuyển tiền và quét mã QR.') }),
  );
}

function bankActions(b: Bank, reload: () => void): HTMLElement {
  const file = h('input', { type: 'file', accept: 'image/png,image/jpeg,image/webp', hidden: true }) as HTMLInputElement;
  file.addEventListener('change', async () => {
    const f = file.files?.[0];
    if (!f) return;
    try { await api.upload(`/api/bank-accounts/${b.bankAccountId}/qr`, f, 'PUT'); toast('Đã tải ảnh QR'); reload(); } catch (e) { toast(errText(e), 'err'); }
  });
  return h('div', { class: 'row-actions' }, file,
    h('button', { class: 'btn secondary sm', onclick: () => file.click() }, icon('qr', 14), 'Ảnh QR'),
    b.isDefault ? null : h('button', { class: 'btn secondary sm', onclick: async () => { try { await api.put(`/api/bank-accounts/${b.bankAccountId}/default`); toast('Đã đặt mặc định'); reload(); } catch (e) { toast(errText(e), 'err'); } } }, 'Đặt mặc định'),
    h('button', { class: 'btn secondary sm', onclick: () => bankDialog(b, reload) }, icon('edit', 14), 'Sửa'),
    h('button', { class: 'btn secondary sm', onclick: async () => {
      if (!(await confirmDialog('Ngừng dùng tài khoản', `Ngừng dùng ${b.bankName} ${b.accountNumber}?`, 'Ngừng dùng', true))) return;
      try { await api.del(`/api/bank-accounts/${b.bankAccountId}`); toast('Đã ngừng dùng'); reload(); } catch (e) { toast(errText(e), 'err'); }
    } }, 'Ngừng'));
}

function bankDialog(b: Bank | null, done: () => void): void {
  const d = openDialog(b ? 'Sửa tài khoản ngân hàng' : 'Thêm tài khoản ngân hàng');
  const err = h('div');
  const bank = input({ value: b?.bankName ?? '', placeholder: 'Ví dụ: Vietcombank' });
  const num = input({ value: b?.accountNumber ?? '', inputmode: 'numeric', placeholder: '6 đến 30 chữ số' });
  const holder = input({ value: b?.accountHolder ?? '', placeholder: 'Tên chủ tài khoản, viết hoa không dấu' });
  d.body.append(err, field('Ngân hàng', bank), field('Số tài khoản', num), field('Chủ tài khoản', holder));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const body = { bankName: bank.value.trim(), accountNumber: num.value.trim(), accountHolder: holder.value.trim() };
    try { await (b ? api.put(`/api/bank-accounts/${b.bankAccountId}`, body) : api.post(`/api/properties/${PROPERTY_ID}/bank-accounts`, body)); d.close(); toast('Đã lưu'); done(); }
    catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}

