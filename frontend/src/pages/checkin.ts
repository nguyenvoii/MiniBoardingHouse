import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText } from '../lib/api';
import { money } from '../lib/format';
import { loadLive } from '../data/live';
import type { Contract } from '../data/types';
import { card, dl, field, formError, input, pageHead, plate, select, toast, openDialog } from '../ui/kit';
import { addTenant } from './tenants';
import { signPad } from './contracts';

// Nhận khách thuê phòng: một luồng liền mạch từ lúc khách đến cho tới khi nhận phòng.
// 1 Khách hàng → 2 Phòng và hợp đồng → 3 Bàn giao phòng → 4 Thu cọc, ký và cấp tài khoản.

const STEPS = ['Khách hàng', 'Phòng và hợp đồng', 'Bàn giao phòng', 'Thu cọc và tài khoản'];
const today = () => new Date().toISOString().slice(0, 10);
const addMonths = (iso: string, m: number): string => {
  const d = new Date(iso);
  d.setMonth(d.getMonth() + m);
  return d.toISOString().slice(0, 10);
};

interface State {
  step: number;
  tenantId: number | null; tenantName: string; tenantPhone: string;
  roomId: number | null; roomCode: string;
  contract: Contract | null;
  handedOver: boolean;
  depositPaid: boolean; signed: boolean; accountDone: boolean;
}

export async function checkinPage(view: HTMLElement, _p: Record<string, string>, query: URLSearchParams): Promise<void> {
  const live = await loadLive();
  const st: State = {
    step: 0, tenantId: null, tenantName: '', tenantPhone: '', roomId: null, roomCode: '', contract: null,
    handedOver: false, depositPaid: false, signed: false, accountDone: false,
  };
  const preTenant = Number(query.get('tenant')) || null;
  const preRoom = Number(query.get('room')) || null;
  const preContract = Number(query.get('contract')) || null;

  const pick = (id: number) => {
    const t = live.tenants.find((x) => x.tenantId === id);
    if (t) { st.tenantId = t.tenantId; st.tenantName = t.name; st.tenantPhone = t.phone; }
  };
  if (preTenant) pick(preTenant);
  if (preRoom) { const r = live.rooms.find((x) => x.roomId === preRoom); if (r) { st.roomId = r.roomId; st.roomCode = r.code; } }
  if (preContract) {
    // Tiếp tục một hợp đồng đã lập nhưng chưa bàn giao phòng
    const c = live.contracts.find((x) => x.contractId === preContract) ?? await api.get<{ contract: Contract }>(`/api/contracts/${preContract}`).then((r) => r.contract).catch(() => null);
    if (c) { st.contract = c; st.tenantId = c.tenantId; st.tenantName = c.tenantName; st.roomId = c.roomId; st.roomCode = c.roomCode; st.step = 2; }
  } else if (st.tenantId) st.step = 1;

  const body = h('div', { class: 'stack', style: 'gap:16px' });
  const side = h('aside', { class: 'sum' });
  const stepsEl = h('ol', { class: 'steps', 'aria-label': 'Các bước nhận khách' });

  const paint = () => {
    stepsEl.replaceChildren(...STEPS.map((s, i) => h('li', { class: i < st.step ? 'done' : i === st.step ? 'now' : '', 'aria-current': i === st.step ? 'step' : null },
      h('span', { class: 'n' }, i < st.step ? icon('check', 12) : String(i + 1)), s)));
    body.replaceChildren([step1, step2, step3, step4][st.step]());
    side.replaceChildren(summary());
  };
  const go = (n: number) => { st.step = n; paint(); window.scrollTo({ top: 0 }); };

  // ---- Tóm tắt bên phải: luôn cho thấy "khách A đang vào phòng nào, đã làm gì" ----
  const summary = () => card('Hồ sơ nhận phòng', [
    dl([
      ['Khách hàng', st.tenantId ? h('b', null, st.tenantName) : '–'],
      ['Điện thoại', st.tenantPhone || '–'],
      ['Phòng', st.roomCode ? plate(st.roomCode) : '–'],
      ['Hợp đồng', st.contract ? st.contract.contractCode : 'Chưa lập'],
      ['Tiền cọc', st.contract ? `${money(st.contract.depositAmount)} ₫` : '–'],
      ['Giá thuê', st.contract ? `${money(st.contract.monthlyRentPrice)} ₫/tháng` : '–'],
    ]),
    h('ul', { class: 'checklist', style: 'list-style:none;margin:12px 0 0;padding:0;display:grid;gap:6px' },
      ...[['Đã lập hợp đồng', !!st.contract], ['Đã bàn giao phòng', st.handedOver], ['Đã thu cọc', st.depositPaid], ['Khách đã ký', st.signed], ['Đã cấp tài khoản', st.accountDone]]
        .map(([l, ok]) => h('li', { style: `display:flex;gap:8px;align-items:center;color:${ok ? 'var(--primary)' : 'var(--ink-3)'}` }, icon(ok ? 'check' : 'clock', 14), l as string))),
  ]);

  // ---- Bước 1: chọn hoặc thêm khách ----
  function step1(): HTMLElement {
    const available = live.tenants.filter((t) => !t.roomCode);
    const sel = select([['', '— Chọn khách chưa thuê —'], ...available.map((t) => [String(t.tenantId), `${t.name} · ${t.phone}`] as [string, string])], st.tenantId ? String(st.tenantId) : '');
    const next = h('button', { class: 'btn' }, 'Tiếp tục', icon('right', 16));
    next.addEventListener('click', () => {
      if (!sel.value) { toast('Hãy chọn khách hoặc thêm khách mới', 'err'); return; }
      pick(Number(sel.value)); go(1);
    });
    return card('1. Khách đến thuê phòng là ai?', [
      h('p', { class: 'muted' }, 'Chọn khách đã có hồ sơ (người đến hỏi thuê trước đó). Nếu là khách mới, thêm hồ sơ rồi tiếp tục. Chỉ hiện khách chưa đang thuê phòng nào.'),
      field('Khách hàng', sel),
      h('div', { class: 'row', style: 'display:flex;gap:10px;flex-wrap:wrap' },
        h('button', { class: 'btn secondary', onclick: () => addTenant((c) => {
          if (!c) return;
          void loadLive().then((l) => { live.tenants.splice(0, live.tenants.length, ...l.tenants); pick(c.tenantId); go(1); });
        }) }, icon('plus', 16), 'Thêm khách mới'), next),
    ]);
  }

  // ---- Bước 2: phòng và hợp đồng ----
  function step2(): HTMLElement {
    if (st.contract) return doneBox('Đã lập hợp đồng ' + st.contract.contractCode, 'Hợp đồng đã tạo, không lập lại. Tiếp tục bàn giao phòng.', () => go(2));
    const err = h('div');
    const vacant = live.rooms.filter((r) => r.status === 'VACANT');
    const room = select([['', '— Chọn phòng trống —'], ...vacant.map((r) => [String(r.roomId), `${r.code} · tầng ${r.floor} · ${money(r.price)} ₫/tháng`] as [string, string]).sort()], st.roomId ? String(st.roomId) : '');
    const start = input({ type: 'date', value: today() });
    const months = select([['6', '6 tháng'], ['12', '12 tháng'], ['24', '24 tháng']], '12');
    const rent = input({ type: 'number', min: '0', step: '10000', class: 'input num-in' });
    const dep = input({ type: 'number', min: '0', step: '10000', class: 'input num-in' });
    const cycle = select([['1', 'Mỗi tháng'], ['2', '2 tháng một lần'], ['3', '3 tháng một lần']]);
    const due = input({ type: 'number', min: '1', max: '28', value: '5', class: 'input num-in' });
    const late = input({ type: 'number', min: '0', step: '1000', value: '10000', class: 'input num-in' });
    const info = h('p', { class: 'muted' });
    const onRoom = () => {
      const r = live.rooms.find((x) => String(x.roomId) === room.value);
      if (r) { rent.value = String(r.price); dep.value = String(r.deposit); info.textContent = `Phòng ${r.code}: ${r.area} m², tối đa ${r.maxPeople} người. Có thể điều chỉnh giá và cọc cho hợp đồng này.`; } else info.textContent = '';
    };
    room.addEventListener('change', onRoom); onRoom();
    const ok = h('button', { class: 'btn' }, 'Lập hợp đồng và tiếp tục');
    ok.addEventListener('click', () => busy(ok, async () => {
      err.replaceChildren();
      if (!room.value) { err.replaceChildren(formError('Chọn phòng cho khách.')); return; }
      try {
        const c = await api.post<Contract>('/api/contracts', {
          roomId: Number(room.value), tenantId: st.tenantId, startDate: start.value, endDate: addMonths(start.value, Number(months.value)),
          monthlyRentPrice: Number(rent.value), depositAmount: Number(dep.value),
          paymentCycleMonths: Number(cycle.value), paymentDueDay: Number(due.value), lateFeePerDay: Number(late.value),
        });
        st.contract = c; st.roomId = c.roomId; st.roomCode = c.roomCode;
        toast(`Đã lập hợp đồng ${c.contractCode}`);
        go(2);
      } catch (e) { err.replaceChildren(formError(errText(e))); }
    }));
    return card(`2. ${st.tenantName} thuê phòng nào, điều khoản ra sao?`, [
      err,
      h('div', { class: 'form-grid' }, field('Phòng (chỉ phòng trống)', room), field('Ngày bắt đầu thuê', start), field('Thời hạn', months),
        field('Giá thuê (₫/tháng)', rent), field('Tiền cọc (₫)', dep), field('Chu kỳ thanh toán', cycle),
        field('Hạn trả mỗi kỳ (ngày trong tháng)', due, 'Hóa đơn phát hành sau khi chốt điện nước, hạn trả vào ngày này'), field('Phạt trễ hạn (₫/ngày)', late)),
      info,
      h('div', { style: 'display:flex;gap:10px;flex-wrap:wrap' }, h('button', { class: 'btn secondary', onclick: () => go(0) }, icon('left', 16), 'Quay lại'), ok),
    ]);
  }

  // ---- Bước 3: biên bản bàn giao phòng ----
  function step3(): HTMLElement {
    if (st.handedOver) return doneBox('Đã bàn giao phòng ' + st.roomCode, 'Biên bản nhận phòng đã lưu. Khi trả phòng hệ thống sẽ đối chiếu với biên bản này.', () => go(3));
    const err = h('div');
    const el = input({ type: 'number', min: '0', class: 'input num-in' });
    const wa = input({ type: 'number', min: '0', class: 'input num-in' });
    void api.get<{ initialElectricMeter?: number; initialWaterMeter?: number }>(`/api/chi/rooms/${st.roomId}`).then((r) => {
      el.value = String(r.initialElectricMeter ?? 0); wa.value = String(r.initialWaterMeter ?? 0);
    }).catch(() => { el.value = '0'; wa.value = '0'; });
    const keys = input({ type: 'number', min: '0', value: '2', class: 'input num-in' });
    const cond = h('textarea', { class: 'textarea', placeholder: 'Ví dụ: 1 giường, 1 tủ, 1 điều hòa, 1 bình nóng lạnh; tường sạch, không hư hỏng' }) as HTMLTextAreaElement;
    const notes = h('textarea', { class: 'textarea', placeholder: 'Không bắt buộc' }) as HTMLTextAreaElement;
    const confirmed = h('input', { type: 'checkbox', checked: true, id: 'ck' }) as HTMLInputElement;
    const ok = h('button', { class: 'btn' }, 'Lưu biên bản và tiếp tục');
    ok.addEventListener('click', () => busy(ok, async () => {
      err.replaceChildren();
      if (!cond.value.trim()) { err.replaceChildren(formError('Ghi tình trạng phòng và đồ đạc lúc bàn giao để sau này đối chiếu khi trả phòng.')); return; }
      try {
        await api.post('/api/chi/checkin-inspections', {
          roomId: st.roomId, tenantId: st.tenantId, contractId: st.contract!.contractId, checkinDate: st.contract!.startDate,
          initialElectricMeter: Number(el.value), initialWaterMeter: Number(wa.value), keyHandedCount: Number(keys.value),
          furnishingsCondition: cond.value.trim(), notes: notes.value.trim() || null, tenantConfirmed: confirmed.checked,
        });
        st.handedOver = true; toast('Đã lưu biên bản bàn giao'); go(3);
      } catch (e) { err.replaceChildren(formError(errText(e))); }
    }));
    return card(`3. Bàn giao phòng ${st.roomCode} cho ${st.tenantName}`, [
      err,
      h('p', { class: 'muted' }, 'Cùng khách đọc chỉ số công tơ và kiểm tra đồ đạc. Đây là căn cứ tính điện nước tháng đầu và đối chiếu khi trả phòng.'),
      h('div', { class: 'form-grid' }, field('Chỉ số điện lúc nhận (kWh)', el), field('Chỉ số nước lúc nhận (m³)', wa), field('Số chìa khóa bàn giao', keys)),
      field('Tình trạng phòng và đồ đạc', cond), field('Ghi chú', notes),
      h('label', { style: 'display:flex;gap:8px;align-items:center' }, confirmed, 'Khách đã xem và đồng ý với biên bản'),
      h('div', { style: 'display:flex;gap:10px;flex-wrap:wrap' }, ok),
    ]);
  }

  // ---- Bước 4: thu cọc, ký hợp đồng, cấp tài khoản ----
  function step4(): HTMLElement {
    const c = st.contract!;
    const todo = (title: string, text: string, done: boolean, action: HTMLElement) =>
      h('div', { class: 'panel', style: 'padding:14px;display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap' },
        h('div', null, h('b', null, title), h('p', { class: 'muted', style: 'margin:2px 0 0' }, text)),
        done ? h('span', { class: 'st ok' }, icon('check', 14), 'Xong') : action);
    const deposit = h('button', { class: 'btn' }, icon('wallet', 16), `Đã nhận ${money(c.depositAmount)} ₫`);
    deposit.addEventListener('click', () => busy(deposit, async () => {
      try {
        await api.post('/api/receipts', { voucherType: 'DEPOSIT', amount: c.depositAmount, note: `Thu cọc hợp đồng ${c.contractCode} phòng ${c.roomCode}` });
        st.depositPaid = true; toast('Đã lập phiếu thu cọc'); paint();
      } catch (e) { toast(errText(e), 'err'); }
    }));
    const sign = h('button', { class: 'btn secondary' }, icon('sign', 16), 'Khách ký');
    sign.addEventListener('click', () => signPad(c.contractId, () => { st.signed = true; paint(); }));
    const acct = h('button', { class: 'btn secondary' }, icon('key', 16), 'Cấp tài khoản');
    acct.addEventListener('click', () => busy(acct, async () => {
      try {
        const r = await api.post<{ phoneNumber: string; temporaryPassword: string }>(`/api/tenants/${st.tenantId}/account`);
        st.accountDone = true; showPassword(r.phoneNumber, r.temporaryPassword); paint();
      } catch (e) {
        // Khách đã có tài khoản từ trước thì coi như xong
        if (/da co|đã có|exist/i.test(errText(e))) { st.accountDone = true; paint(); } else toast(errText(e), 'err');
      }
    }));
    const finish = h('a', { class: 'btn', href: `#/contracts?open=${c.contractId}` }, 'Hoàn tất nhận phòng', icon('right', 16));
    return card(`4. Hoàn tất nhận phòng ${st.roomCode}`, [
      h('p', { class: 'muted' }, 'Ba việc cuối. Tiền phòng từng tháng không thu lúc này: mỗi tháng hệ thống chốt điện nước, tính tiền và phát hành hóa đơn; khách trả bằng QR trên điện thoại hoặc nộp tiền mặt tại quầy.'),
      todo('Thu tiền cọc', `${money(c.depositAmount)} ₫ nhận tiền mặt hoặc chuyển khoản; lập phiếu thu để đối chiếu khi hoàn cọc.`, st.depositPaid, deposit),
      todo('Khách ký hợp đồng', 'Ký trên màn hình, chỉ ký được một lần cho mỗi hợp đồng.', st.signed, sign),
      todo('Cấp tài khoản cho khách', `Khách đăng nhập bằng số điện thoại ${st.tenantPhone} để xem hóa đơn, trả tiền QR và báo sự cố.`, st.accountDone, acct),
      h('div', { style: 'display:flex;gap:10px;flex-wrap:wrap' }, finish),
    ]);
  }

  function doneBox(title: string, text: string, next: () => void): HTMLElement {
    return card(title, [h('p', null, text), h('button', { class: 'btn', onclick: next }, 'Tiếp tục', icon('right', 16))]);
  }

  view.replaceChildren(
    pageHead('Nhận khách thuê phòng', 'Làm lần lượt từ trên xuống: hồ sơ khách, hợp đồng, bàn giao phòng, thu cọc. Xong là khách đã ở được và đăng nhập được.'),
    stepsEl,
    h('div', { class: 'wizard' }, body, side),
  );
  paint();
}

function showPassword(phone: string, password: string): void {
  const d = openDialog('Đã cấp tài khoản', { width: 440 });
  d.body.append(
    h('p', null, 'Đưa thông tin này cho khách. Mật khẩu tạm chỉ hiện một lần.'),
    dl([['Đăng nhập bằng', phone], ['Mật khẩu tạm', h('span', { class: 'plate lg', style: 'font-size:20px;letter-spacing:0.08em' }, password)]]),
  );
  d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Xong'));
}

