// E2E thật UC71 (phạt trễ hạn + phụ thu + giảm trừ vào bảng tính) và UC75 (hóa đơn bất thường so với trung bình các kỳ trước).
import { start, assert, tag, log, base, fixtureInvoice } from './lib.mjs';
import { fixtureRoom } from './lib2.mjs';

const s = await start();
const { page, api, goto, status } = s;

// ---------- UC75: hai tháng liên tiếp, tháng 2 dùng điện nhiều gấp ~29 lần
const periods = (await api('GET', '/api/meter-periods?propertyId=1')).map((p) => p.startDate.slice(0, 7));
const billing = (await api('GET', '/api/billing/periods?propertyId=1')).map((b) => b.monthYear);
const taken = new Set([...periods, ...billing]);
const months = [];
for (let n = 0; n < 480 && months.length < 2; n++) { const k = `${2045 + Math.floor(n / 12)}-${String((n % 12) + 1).padStart(2, '0')}`; if (!taken.has(k)) months.push(k); }
const [m1, m2] = months;
const fx = await fixtureRoom(api, { label: 'AN', startMonth: m1 });
const mkPeriod = (m) => api('POST', '/api/meter-periods', { propertyId: 1, periodName: `E2E ${m} ${tag}`, startDate: `${m}-01`, endDate: `${m}-${new Date(Number(m.slice(0, 4)), Number(m.slice(5)), 0).getDate()}` });
const [p1, p2] = [await mkPeriod(m1), await mkPeriod(m2)];
const reading = async (p, e, w) => { const r = await api('POST', '/api/meter-readings', { periodId: p.periodId, roomId: fx.room.roomId, newElectric: e, newWater: w, recordedByUserId: 1 }); await api('POST', `/api/meter-readings/${r.readingId}/confirm`); };
await reading(p1, 110, 11);
await reading(p2, 3110, 40); // 3.000 kWh: tổng tiền vượt 150% trung bình (tiền phòng chiếm phần lớn nên phải tăng mạnh)
const calcPeriod = async (m) => {
  const bp = (await api('POST', '/api/billing/periods', { propertyId: 1, monthYear: m })).period;
  await api('POST', `/api/billing/periods/${bp.billingPeriodId}/calculate`);
  return bp.billingPeriodId;
};
const b1 = await calcPeriod(m1);
await api('POST', `/api/billing/periods/${b1}/approve?force=true`);
const b2 = await calcPeriod(m2);
const an = await api('GET', `/api/billing/periods/${b2}/anomalies`);
assert.ok(an.some((a) => a.roomCode === fx.room.roomCode), 'UC75 phòng dùng điện tăng vọt bị đánh dấu bất thường');
assert.equal((await api('GET', `/api/billing/periods/${b1}/anomalies`)).some((a) => a.roomCode === fx.room.roomCode), false, 'kỳ đầu không bị đánh dấu');
await goto('/billing'); await goto(`/billing/${b2}`); await page.waitForTimeout(1500);
// kỳ nằm ở khu 1; màn chi tiết kỳ mở bằng đường dẫn nên không cần chọn tháng
await page.getByText('Phòng có tiền cao bất thường').waitFor();
await page.getByRole('row').filter({ hasText: fx.room.roomCode }).first().waitFor();
await page.getByText(/Vượt 150% trung bình/).first().waitFor();
log('UC75 hóa đơn bất thường OK');

// ---------- UC71: tháng sau có phạt trễ hạn khi hóa đơn cũ chưa trả; phụ thu + giảm trừ vào bảng tính
const f1 = await fixtureInvoice(api, { label: 'PN', issueDate: '2026-08-01', dueDate: '2026-08-10' }); // quá hạn từ 10/8
await api('POST', '/api/room-custom-rates', { roomId: f1.room.roomId, rateName: `Phụ thu E2E ${tag}`, customPrice: 40000 }).catch(() => null);
await api('POST', '/api/room-custom-rates', { roomId: f1.room.roomId, rateName: `Giảm E2E ${tag}`, customPrice: -10000 }).catch(() => null);
const f2 = await fixtureInvoice(api, { label: 'PN2', tenant: f1.tenant }); // kỳ sau của cùng khách: cùng đợt phát hành lại tính cả hợp đồng đầu
const month2 = f2.month;
const invs = await api('GET', `/api/invoices?propertyId=1&monthYear=${month2}`);
const first = invs.find((i) => i.roomCode === f1.room.roomCode);
assert.ok(first, 'hóa đơn kỳ sau của hợp đồng đầu');
const detail = await api('GET', `/api/invoices/${first.invoiceId}/detail`);
const names = detail.items.map((i) => i.itemName);
assert.ok(names.some((n) => n.startsWith('Phạt trễ hạn')), `có dòng phạt trễ hạn (${names.join(' | ')})`);
assert.ok(names.some((n) => n.includes(`Phụ thu: Phụ thu E2E ${tag}`)), 'có dòng phụ thu');
assert.ok(names.some((n) => n.includes(`Giảm trừ: Giảm E2E ${tag}`)), 'có dòng giảm trừ');
const pen = detail.items.find((i) => i.itemName.startsWith('Phạt trễ hạn'));
assert.ok(pen.quantity > 0 && pen.unitPrice === 10000 && pen.subtotal === pen.quantity * 10000, 'phạt = số ngày quá hạn × 10.000/ngày');
log('UC71 phạt trễ hạn + phụ thu + giảm trừ OK', { days: pen.quantity, penalty: pen.subtotal });

const reader = await s.staffToken('METER_READER');
assert.ok([401, 403].includes(await status(reader.token, 'GET', `/api/billing/periods/${b2}/anomalies`)), 'METER_READER không xem bất thường');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
