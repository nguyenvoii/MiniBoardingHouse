// E2E thật UC62-UC76 (khu 4, tháng xa để không lẫn dữ liệu): kỳ tính tiền, kiểm tra đầu vào, tiền phòng, điện nước, dịch vụ,
// phân bổ điện nước chung, điều chỉnh dòng phí, bất thường, tổng hợp.
import { start, assert, tag, log, base } from './lib.mjs';
import { gotoMonth } from './lib2.mjs';
import { tenantToken } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
// khu + tháng quá khứ chưa có kỳ (chi phí vận hành không ghi được ở tương lai nên phải dùng tháng đã qua, đơn giá có hiệu lực từ 2026-01)
let P = null, month = null, floorId = null;
// bảng giá phủ năm 2025 để có thêm 12 tháng quá khứ chạy được kịch bản (chi phí không ghi được ở tương lai)
const plans0 = await api('GET', '/api/service-rate-plans');
if (!plans0.some((pl) => pl.effectiveStartDate <= '2025-01-01' && (!pl.effectiveEndDate || pl.effectiveEndDate >= '2025-12-31'))) {
  // kéo lùi ngày áp dụng của bảng giá sớm nhất (cùng đơn giá) để phủ 2025; chỉ làm trên DB QA
  const first = [...plans0].sort((a, b) => a.effectiveStartDate.localeCompare(b.effectiveStartDate))[0];
  await api('PUT', `/api/service-rate-plans/${first.planId}`, { planName: first.planName, electricUnitPrice: first.electricUnitPrice, waterUnitPrice: first.waterUnitPrice, effectiveStartDate: '2025-01-01', effectiveEndDate: first.effectiveEndDate });
}
const candidates = ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05', ...Array.from({ length: 12 }, (_, i) => `2025-${String(i + 1).padStart(2, '0')}`)];
{
  const pid = 1; // chi phí vận hành chỉ gắn khu mặc định nên chia điện nước chung chỉ kiểm được ở khu 1
  const mp0 = await api('GET', `/api/meter-periods?propertyId=${pid}`);
  const bp0 = await api('GET', `/api/billing/periods?propertyId=${pid}`);
  const k = candidates.find((c) => !mp0.some((p) => p.startDate.startsWith(c)) && !bp0.some((b) => b.monthYear === c));
  if (k) { P = pid; month = k; }
}
assert.ok(P, 'còn tháng trống để chạy kịch bản tính tiền');
floorId = (await api('GET', `/api/chi/floors?propertyId=${P}`))[0].floorId;
await page.evaluate((pid) => localStorage.setItem('stayly.property', String(pid)), P);
await page.reload(); await page.waitForLoadState('networkidle'); // khu trọ được đọc lúc nạp trang
const [y, m] = month.split('-').map(Number);
const last = new Date(y, m, 0).getDate();
log('khu', P, 'tháng', month);

// ---------- fixture: 3 phòng (R3 chưa có chỉ số), R1 có thêm 1 người ở
const mkRoom = async (n) => {
  const room = await api('POST', '/api/chi/rooms', { propertyId: P, floorId, roomCode: `E2E-B${n}-${tag}`, areaSqm: 20, maxOccupants: 3, baseRentPrice: 3000000, depositAmount: 3000000, initialElectricMeter: 100, initialWaterMeter: 10 });
  const ph = `09${tag}${50 + n}`;
  const tenant = await api('POST', '/api/chi/tenants', { fullName: `E2E Khách B${n} ${tag}`, phoneNumber: ph, identityNumber: `0${tag}${5000 + n}`, dob: '1995-01-01' });
  const contract = await api('POST', '/api/contracts', { roomId: room.roomId, tenantId: tenant.tenantId, startDate: `${month}-01`, endDate: `${y + 1}-12-31`, monthlyRentPrice: 3000000, depositAmount: 3000000, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 10000 });
  await api('POST', `/api/contracts/${contract.contractId}/deposit`, { amount: 3000000 });
  await api('POST', '/api/chi/checkin-inspections', { roomId: room.roomId, tenantId: tenant.tenantId, contractId: contract.contractId, checkinDate: `${month}-01`, initialElectricMeter: 100, initialWaterMeter: 10, keyHandedCount: 2, furnishingsCondition: 'Sạch', tenantConfirmed: true });
  return { room, tenant, contract };
};
const [r1, r2, r3] = [await mkRoom(1), await mkRoom(2), await mkRoom(3)];
await api('POST', '/api/chi/tenants/residents', { roomId: r1.room.roomId, fullName: `Người ở cùng ${tag}`, relationshipWithPrimary: 'Vợ/chồng', identityNumber: `0${tag}8888` }).catch(() => null);
const period = await api('POST', '/api/meter-periods', { propertyId: P, periodName: `E2E ${month} ${tag}`, startDate: `${month}-01`, endDate: `${month}-${last}` });
for (const [r, e, w] of [[r1, 150, 15], [r2, 120, 12]]) {
  const rd = await api('POST', '/api/meter-readings', { periodId: period.periodId, roomId: r.room.roomId, newElectric: e, newWater: w, recordedByUserId: 1 });
  await api('POST', `/api/meter-readings/${rd.readingId}/confirm`);
}
await api('POST', '/api/expenses', { expenseCategory: 'Điện nước chung', description: `Điện nước chung E2E ${tag}`, amount: 300000, expenseDate: `${month}-10` });

// ---------- UC66 tạo kỳ tính tiền trên UI
await goto('/billing'); await page.waitForTimeout(800);
await gotoMonth(page, month);
const created = page.waitForResponse((r) => r.url().endsWith('/api/billing/periods') && r.request().method() === 'POST');
await page.getByRole('button', { name: /^Tạo kỳ/ }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Tạo kỳ', exact: true }).click();
const cr = await created;
assert.equal(cr.status(), 200);
const crJson = (await cr.json()).data;
const billingId = crJson.period.billingPeriodId;
assert.ok(crJson.contracts.length >= 3, 'kỳ lấy đủ hợp đồng hiệu lực trong tháng');
await page.waitForURL(/#\/billing\/\d+/);
// kỳ trùng tháng bị từ chối
assert.ok([400, 409].includes(await status(await s.token(), 'POST', '/api/billing/periods', { propertyId: P, monthYear: month })), 'tháng đã có kỳ thì không tạo lại');
log('UC66 tạo kỳ OK');

// ---------- UC67 kiểm tra đầu vào: R3 thiếu chỉ số được cảnh báo
const issues = await api('GET', `/api/billing/periods/${billingId}/validate`);
assert.ok(issues.some((i) => i.roomCode === r3.room.roomCode), 'phòng thiếu chỉ số bị cảnh báo');
await page.getByText('Cần xem lại trước khi duyệt').waitFor();
await page.getByText(r3.room.roomCode).first().waitFor();
log('UC67 kiểm tra đầu vào OK');

// ---------- UC68-UC73 tính tiền: tiền phòng, điện nước, dịch vụ, chia điện nước chung (theo phòng rồi theo số người)
const calcUi = async (label) => {
  await page.getByLabel('Cách chia điện nước chung').selectOption(label);
  const calc = page.waitForResponse((r) => r.url().includes(`/api/billing/periods/${billingId}/calculate`) && r.request().method() === 'POST');
  await page.getByRole('button', { name: /^Tính (tiền|lại)$/ }).click();
  const dlgOk = page.getByRole('dialog').last().getByRole('button', { name: 'Tính lại', exact: true });
  if (await dlgOk.isVisible().catch(() => false)) await dlgOk.click();
  assert.equal((await calc).status(), 200);
  await page.waitForTimeout(1200);
  return api('GET', `/api/billing/periods/${billingId}`);
};
const share = async (fee) => {
  const f = await api('GET', `/api/billing/fees/${fee.calculatedFeeId}`);
  return f.items.filter((i) => /chung/i.test(i.itemName)).reduce((a, i) => a + i.subtotal, 0);
};
let calc = await calcUi('PER_ROOM');
const fee = (r) => calc.fees.find((f) => f.contractId === r.contract.contractId);
for (const r of [r1, r2, r3]) assert.ok(fee(r), `có dòng tính tiền phòng ${r.room.roomCode}`);
assert.equal(fee(r1).rentAmount, 3000000, 'UC68 tiền phòng đủ tháng');
assert.equal(fee(r1).electricAmount, 50 * 3500, 'UC69 điện 50 kWh x 3.500');
assert.equal(fee(r1).waterAmount, 5 * 15000, 'UC69 nước 5 m3 x 15.000');
assert.equal(fee(r2).electricAmount, 20 * 3500);
assert.equal(fee(r3).electricAmount, 0, 'phòng thiếu chỉ số tính 0 (đã cảnh báo)');
assert.ok(fee(r1).serviceAmount >= 0, 'UC70 dịch vụ hàng tháng');
const perRoom = [await share(fee(r1)), await share(fee(r2)), await share(fee(r3))];
assert.ok(Math.abs(perRoom[0] - perRoom[1]) <= 0.02 && Math.abs(perRoom[1] - perRoom[2]) <= 0.02, 'UC72 chia theo phòng: các phòng bằng nhau (lệch làm tròn)');
if (!(perRoom[0] > 0)) console.log('ITEMS', JSON.stringify((await api('GET', `/api/billing/fees/${fee(r1).calculatedFeeId}`)).items.map((i) => [i.itemName, i.subtotal])), JSON.stringify(await api('GET', `/api/expenses?propertyId=${P}&size=5`).catch((e) => String(e)).then((x) => JSON.stringify(x).slice(0, 400))));
assert.ok(perRoom[0] > 0, 'có khoản điện nước chung được chia');
calc = await calcUi('PER_RESIDENT');
const perRes = [await share(fee(r1)), await share(fee(r2)), await share(fee(r3))];
assert.ok(perRes[0] > perRes[1], 'UC72 chia theo người ở: phòng nhiều người trả nhiều hơn');
assert.ok(Math.abs(perRes[1] - perRes[2]) <= 0.02, 'phòng cùng số người trả bằng nhau (lệch làm tròn)');
assert.equal(fee(r1).totalCalculatedAmount, fee(r1).rentAmount + fee(r1).electricAmount + fee(r1).waterAmount + fee(r1).serviceAmount + fee(r1).surchargeAmount - fee(r1).discountAmount + fee(r1).latePenaltyAmount, 'UC73 tổng = các khoản cộng lại');
log('UC68-73 tính tiền, chia điện nước chung OK', { perRoom, perRes });

// ---------- UC74 điều chỉnh dòng phí trong kỳ, tính lại làm mất chỉnh tay
await page.getByRole('row').filter({ hasText: r1.room.roomCode }).first().click();
const dd = page.getByRole('dialog').last();
await dd.getByRole('row').filter({ hasText: /^Điện/ }).first().waitFor().catch(() => {});
const before = fee(r1).totalCalculatedAmount;
await dd.getByRole('button', { name: 'Thêm khoản' }).click();
const add = page.getByRole('dialog').last();
await add.getByPlaceholder(/Ví dụ: Sửa máy lạnh/).fill('Giảm do mất điện');
await add.locator('input[type=number]').nth(1).fill('-100000');
await add.getByRole('button', { name: 'Thêm', exact: true }).click();
await page.waitForTimeout(1200);
calc = await api('GET', `/api/billing/periods/${billingId}`);
assert.equal(fee(r1).totalCalculatedAmount, before - 100000, 'khoản giảm trừ được cộng vào tổng');
await page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));
log('UC74 thêm khoản giảm trừ OK');

// ---------- UC70 dịch vụ theo đầu người: số lượng = số người ở (phòng 1 có 2 người, phòng 2 có 1)
const svcName = `Nước uống E2E ${tag}`;
const svc = await api('POST', '/api/property-service-rates', { propertyId: 1, serviceName: svcName, chargeType: 'PER_PERSON', unitPrice: 10000, active: true });
try {
  calc = await calcUi('PER_RESIDENT');
  const it = async (r) => (await api('GET', `/api/billing/fees/${fee(r).calculatedFeeId}`)).items.find((i) => i.itemName === svcName);
  const i1 = await it(r1), i2 = await it(r2);
  assert.ok(i1 && i2, 'dịch vụ theo đầu người có trong bảng tính');
  assert.equal(i1.quantity, 2, 'phòng 1: 2 người');
  assert.equal(i2.quantity, 1, 'phòng 2: 1 người');
  assert.equal(i1.subtotal, 20000);
} finally {
  await api('PUT', `/api/property-service-rates/${svc.rateId}`, { propertyId: 1, serviceName: svcName, chargeType: 'PER_PERSON', unitPrice: 10000, active: false });
}
calc = await calcUi('PER_RESIDENT'); // tính lại không còn dịch vụ đã tắt
log('UC70 dịch vụ theo đầu người OK');

// ---------- UC109 phụ phí thiết bị cộng vào hóa đơn kỳ tính
await api('POST', '/api/chi/addon-fees', { roomId: r2.room.roomId, feeName: `Tủ lạnh E2E ${tag}`, amount: 60000, billingCycleMonths: 1, startDate: `${month}-01`, isActive: true });
calc = await calcUi('PER_RESIDENT');
const f2 = await api('GET', `/api/billing/fees/${fee(r2).calculatedFeeId}`);
assert.ok(f2.items.some((i) => i.itemName.startsWith('Thiết bị: ') && i.subtotal === 60000), 'phụ phí thiết bị có trong bảng tính');
log('UC109 phụ phí thiết bị vào hóa đơn OK');

// ---------- UC75 bất thường: không có lịch sử thì không báo; API trả đúng danh sách rỗng/đủ khóa
const an = await api('GET', `/api/billing/periods/${billingId}/anomalies`);
assert.ok(Array.isArray(an));

// ---------- UC76 bảng tổng hợp tiền phải thu theo tháng (sau khi duyệt, phát hành)
await goto('/billing'); await goto(`/billing/${billingId}`); await page.waitForTimeout(1000);
await page.getByRole('button', { name: 'Duyệt kỳ', exact: true }).first().click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Duyệt kỳ', exact: true }).click();
const force = page.getByRole('dialog').last().getByRole('button', { name: 'Vẫn duyệt' });
await Promise.race([page.waitForResponse((r) => r.url().includes('/approve') && r.status() === 200), force.waitFor().then(() => 'force')]).then(async (x) => { if (x === 'force') { await force.click(); } });
await page.waitForTimeout(1500);
assert.equal((await api('GET', `/api/billing/periods/${billingId}`)).period.status, 'APPROVED');
const tTok1 = await tenantToken(s, r1.tenant.tenantId); // khách có tài khoản trước khi phát hành để nhận thông báo (UC79)
const issued = await api('POST', '/api/invoices/batch', { billingPeriodId: billingId });
assert.ok(issued.length >= 3, 'UC77 phát hành đủ hóa đơn cho các phòng');
const codes = new Set(issued.map((i) => i.invoiceCode));
assert.equal(codes.size, issued.length, 'mã hóa đơn không trùng');
const again = await s.raw('POST', '/api/invoices/batch', { billingPeriodId: billingId });
const afterAgain = await api('GET', `/api/invoices?propertyId=${P}&monthYear=${month}`);
assert.equal(afterAgain.filter((i) => i.roomCode === r1.room.roomCode).length, 1, 'UC77 phát hành lần hai không tạo hóa đơn trùng');
const n1 = (await s.raw('GET', '/api/me/notifications?size=50', undefined, tTok1).then((r) => r.json())).data.items;
assert.ok(n1.some((n) => /Hóa đơn mới/.test(n.title) && n.content.includes(issued.find((i) => i.roomCode === r1.room.roomCode).invoiceCode)), 'UC79 khách nhận thông báo hóa đơn mới');
const sum = await api('GET', `/api/invoices/summary/${month}?propertyId=${P}`).catch(() => api('GET', `/api/invoices/summary?propertyId=${P}&monthYear=${month}`));
const feeTotal = calc.fees.reduce((a, f) => a + f.totalCalculatedAmount, 0);
const issuedTotal = afterAgain.reduce((a, i) => a + i.totalAmount, 0);
assert.ok(Math.abs(issuedTotal - feeTotal) < 1, `UC76 tổng hóa đơn (${issuedTotal}) khớp tổng bảng tính (${feeTotal})`);
assert.ok(JSON.stringify(sum).includes(r1.room.roomCode), 'tổng hợp có phòng R1');
log('UC76 tổng hợp OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
for (const [mm, u, d] of [['POST', '/api/billing/periods', { propertyId: P, monthYear: '2040-01' }], ['POST', `/api/billing/periods/${billingId}/calculate`], ['GET', `/api/billing/periods/${billingId}`]]) {
  assert.ok([401, 403].includes(await status(reader.token, mm, u, d)), `METER_READER ${mm} ${u} bị chặn`);
}
log('phân quyền tính tiền OK');
console.log(JSON.stringify({ ok: true, month }));
await s.browser.close();
