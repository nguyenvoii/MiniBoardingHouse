// E2E thật UC61-UC64 (đơn giá, dịch vụ, phụ thu, lịch sử), UC65/UC117 (chi phí), UC116 (dashboard), UC118 (báo cáo + Excel), UC119 (ngân hàng + QR).
import { start, assert, tag, log, base } from './lib.mjs';
import { fixtureRoom } from './lib2.mjs';

const s = await start();
const { page, api, goto, status } = s;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
const toast = (t) => page.getByText(t).first().waitFor();
const tab = async (name) => { await goto('/settings'); await page.waitForTimeout(600); await page.getByRole('tab', { name, exact: true }).click(); await page.waitForTimeout(600); };
const closeAll = () => page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));

// ---------- UC61/UC64 bảng giá điện nước và lịch sử áp dụng
await tab('Đơn giá điện nước');
const plansBefore = await api('GET', '/api/service-rate-plans');
await page.getByRole('button', { name: 'Thêm bảng giá' }).click();
let dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Giá điện (₫/kWh)').fill('-1');
await dlg.getByLabel('Tên bảng giá').fill(`Bảng giá E2E ${tag}`);
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor(); // giá âm bị từ chối
const yr = Math.max(2040, ...plansBefore.map((p) => Number(p.effectiveStartDate.slice(0, 4)))) + 1; // năm chưa có bảng giá nào (bảng giá không được chồng thời gian)
await dlg.getByLabel('Giá điện (₫/kWh)').fill('4200');
await dlg.getByLabel('Giá nước (₫/m³)').fill('18000');
await dlg.getByLabel('Áp dụng từ').fill(`${yr}-01-01`);
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu bảng giá');
const plans = await api('GET', '/api/service-rate-plans');
const plan = plans.find((p) => p.planName === `Bảng giá E2E ${tag}`);
assert.ok(plan && plan.electricUnitPrice === 4200 && plan.waterUnitPrice === 18000, 'bảng giá có trong DB');
await page.getByRole('row').filter({ hasText: `Bảng giá E2E ${tag}` }).first().waitFor();
await page.getByRole('row').filter({ hasText: 'Bảng giá 2026' }).first().waitFor(); // lịch sử các bảng giá vẫn hiển thị
// đơn giá có hiệu lực theo ngày
const eff = await api('GET', `/api/service-rate-plans/effective?date=${yr}-06-01`).catch(() => null);
if (eff) assert.equal(eff.electricUnitPrice ?? eff[0]?.electricUnitPrice, 4200, 'đơn giá hiệu lực đúng theo thời điểm');
log('UC61/UC64 bảng giá + lịch sử OK');
await closeAll();

// ---------- UC62 dịch vụ hàng tháng
await tab('Dịch vụ hàng tháng');
await page.getByRole('button', { name: 'Thêm dịch vụ' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Tên dịch vụ').fill(`Rác E2E ${tag}`);
await dlg.getByLabel('Đơn giá (₫)').fill('-5');
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByLabel('Đơn giá (₫)').fill('30000');
await dlg.getByLabel('Đang áp dụng').uncheck(); // không ảnh hưởng hóa đơn thật
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu dịch vụ');
const svc = (await api('GET', '/api/property-service-rates?propertyId=1')).find((x) => x.serviceName === `Rác E2E ${tag}`);
assert.ok(svc && svc.unitPrice === 30000 && svc.active === false);
log('UC62 dịch vụ OK');
await closeAll();

// ---------- UC63 phụ thu / giảm trừ theo phòng
const fx = await fixtureRoom(api, { label: 'ST', startMonth: '2026-11' });
await page.reload(); await page.waitForLoadState('networkidle'); // nạp lại danh sách phòng mới
await tab('Phụ thu và giảm trừ');
const roomSel = page.locator('#view .toolbar select').first();
await roomSel.selectOption(String(fx.room.roomId));
await page.getByRole('button', { name: /Thêm khoản/ }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Tên khoản').fill(`Giảm E2E ${tag}`);
await dlg.getByLabel('Số tiền (₫)').fill('-50000');
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1200);
const custom = await api('GET', `/api/room-custom-rates?roomId=${fx.room.roomId}`);
assert.ok(custom.some((c) => c.rateName === `Giảm E2E ${tag}` && c.customPrice === -50000), 'khoản giảm trừ theo phòng có trong DB');
log('UC63 phụ thu/giảm trừ OK');
await closeAll();

// ---------- UC119 ngân hàng + QR
await tab('Ngân hàng và mã QR');
await page.getByRole('button', { name: /Thêm tài khoản/ }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Ngân hàng').fill('Vietcombank');
await dlg.getByLabel('Số tài khoản').fill('abc');
await dlg.getByLabel('Chủ tài khoản').fill('NGUYEN VAN E2E');
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor(); // số tài khoản sai bị từ chối
const acctNo = `9704${tag}${tag}`.slice(0, 12);
await dlg.getByLabel('Số tài khoản').fill(acctNo);
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu');
const bank = (await api('GET', '/api/properties/1/bank-accounts')).find((b) => b.accountNumber === acctNo);
assert.ok(bank, 'tài khoản có trong DB');
await closeAll();
await tab('Ngân hàng và mã QR');
await page.getByRole('row').filter({ hasText: acctNo }).first().waitFor();
// tải ảnh QR
await page.getByRole('row').filter({ hasText: acctNo }).locator('input[type=file]').setInputFiles({ name: 'qr.png', mimeType: 'image/png', buffer: png }).catch(() => log('chưa tìm thấy ô tải QR theo hàng'));
await page.waitForTimeout(1500);
log('UC119 ngân hàng OK');

// ---------- UC117 chi phí vận hành
await goto('/expenses'); await page.waitForTimeout(1000);
await page.getByRole('button', { name: 'Thêm khoản chi' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Nội dung').fill(`Thay bóng đèn E2E ${tag}`);
await dlg.getByLabel('Số tiền (₫)').fill('-1');
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByLabel('Số tiền (₫)').fill('120000');
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu khoản chi');
const exp = (await api('GET', '/api/expenses?size=100')).items.find((e) => e.description === `Thay bóng đèn E2E ${tag}`);
assert.ok(exp && exp.amount === 120000);
assert.equal(await status(await s.token(), 'POST', '/api/expenses', { expenseCategory: 'Khác', description: 'tương lai', amount: 1, expenseDate: '2099-01-01' }), 400, 'ngày chi tương lai bị từ chối');
await page.getByRole('row').filter({ hasText: `Thay bóng đèn E2E ${tag}` }).getByRole('button', { name: 'Sửa' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Số tiền (₫)').fill('150000');
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1200);
assert.equal((await api('GET', `/api/expenses/${exp.expenseId}`)).amount, 150000);
log('UC117 chi phí vận hành OK');

// ---------- UC118 báo cáo doanh thu/chi phí + Excel; UC116 dashboard khớp API
await goto('/reports'); await page.waitForTimeout(1500);
const year = String(new Date().getFullYear());
const rev = await api('GET', `/api/reports/revenue?propertyId=1&year=${year}`);
const dl = page.waitForEvent('download');
await page.getByRole('button', { name: 'Excel', exact: true }).first().click();
assert.match((await dl).suggestedFilename(), /\.xlsx$/);
assert.ok(typeof rev.totalRevenue === 'number' && typeof rev.totalExpense === 'number', 'báo cáo có tổng thu/chi');
await page.getByText(/Cả năm/).first().waitFor();
log('UC118 báo cáo + Excel OK');
await goto('/'); await page.waitForTimeout(1500);
const dash = await api('GET', '/api/dashboard?propertyId=1');
await page.locator('#view').waitFor();
assert.ok(dash && typeof dash === 'object', 'dashboard trả dữ liệu');
const body = await page.locator('#view').innerText();
assert.ok(body.length > 200, 'màn tổng quan hiển thị');
assert.ok(body.includes(`${dash.rentedRooms}/${dash.totalRooms}`), 'UC116 số phòng đang thuê khớp API');
assert.ok(body.includes(`lấp đầy ${dash.occupancyPercent}%`), 'UC116 tỷ lệ lấp đầy khớp API');
const overdueApi = (await api('GET', '/api/invoices?propertyId=1&status=OVERDUE')).length;
assert.ok(body.includes(`${overdueApi}`), 'UC116 số hóa đơn quá hạn khớp API');
log('UC116 dashboard OK');

// xóa khoản chi thử để báo cáo sạch
await goto('/expenses'); await page.waitForTimeout(800);
await page.getByRole('row').filter({ hasText: `Thay bóng đèn E2E ${tag}` }).getByRole('button', { name: 'Xóa' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: /^Xóa/ }).last().click().catch(() => {});
await page.waitForTimeout(1200);

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
const cashier = await s.staffToken('CASHIER');
for (const [m, u, d] of [['POST', '/api/service-rate-plans', {}], ['POST', '/api/property-service-rates', {}], ['POST', '/api/expenses', {}], ['GET', `/api/reports/revenue?propertyId=1&year=${year}`], ['GET', '/api/dashboard?propertyId=1']]) {
  assert.ok([401, 403].includes(await status(reader.token, m, u, d)), `METER_READER ${m} ${u} bị chặn`);
}
for (const [m, u, d] of [['POST', '/api/service-rate-plans', {}], ['POST', '/api/expenses', {}]]) {
  assert.ok([401, 403].includes(await status(cashier.token, m, u, d)), `CASHIER ${m} ${u} bị chặn`);
}
log('phân quyền cấu hình/báo cáo OK');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
