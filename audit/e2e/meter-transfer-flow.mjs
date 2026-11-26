// E2E thật: UI -> API -> SQL Server QA. Chuyển phòng chốt chỉ số -> khách mới ghi chỉ số cùng kỳ -> tính tiền.
// Chạy: BASE_URL=http://127.0.0.1:5178 node meter-transfer-flow.mjs (backend QA 18091, jobs tắt).
import { chromium } from 'playwright';
import assert from 'node:assert/strict';

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5178';
const tag = String(Date.now()).slice(-6);
const browser = await chromium.launch({ headless: true });
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage();
const posts = [];
page.on('request', (r) => { if (r.method() === 'POST' && r.url().includes('/api/meter-readings')) posts.push(r.url()); });
const log = (...a) => console.log('•', ...a);

const token = () => page.evaluate(() => sessionStorage.getItem('stayly.session'));
async function api(method, url, data) {
  const res = await page.request.fetch(`${base}${url}`, { method, headers: { Authorization: `Bearer ${await token()}`, 'Content-Type': 'application/json' }, data });
  const body = await res.json().catch(() => ({}));
  assert.ok(res.ok(), `${method} ${url} -> ${res.status()} ${JSON.stringify(body).slice(0, 300)}`);
  return body.data ?? body;
}

await page.goto(`${base}/#/login`, { waitUntil: 'networkidle' });
await page.getByLabel(/email|số điện thoại|tài khoản/i).fill('admin@minihouse.local');
await page.getByLabel(/mật khẩu/i).fill('Admin@123');
await page.getByRole('button', { name: /đăng nhập/i }).click();
await page.waitForFunction(() => !!sessionStorage.getItem('stayly.session'));

// --- fixture (API): chọn tháng chưa có kỳ ghi số, 2 phòng, 2 khách, hợp đồng cho khách 1 ở phòng A
const periods = await api('GET', '/api/meter-periods?propertyId=1');
let y = 2029, m = 1;
const key = () => `${y}-${String(m).padStart(2, '0')}`;
while (periods.some((p) => p.startDate.startsWith(key()))) { m++; if (m > 12) { m = 1; y++; } }
const month = key();
const room = (n) => api('POST', '/api/chi/rooms', { propertyId: 1, floorId: 1, roomCode: `E2E-${n}-${tag}`, areaSqm: 20, maxOccupants: 2, baseRentPrice: 3000000, depositAmount: 3000000, initialElectricMeter: 100, initialWaterMeter: 10 });
const tenant = (n) => api('POST', '/api/chi/tenants', { fullName: `E2E Khach ${n} ${tag}`, phoneNumber: `09${tag}${String(n).repeat(2)}`, identityNumber: `0${tag}${String(n).repeat(4)}`, dob: '1995-01-01' });
const [roomA, roomB, t1, t2] = [await room('A'), await room('B'), await tenant(1), await tenant(2)];
const startMonth = `${month}-01`;
const c1 = await api('POST', '/api/contracts', { roomId: roomA.roomId, tenantId: t1.tenantId, startDate: startMonth, endDate: `${y + 2}-12-31`, monthlyRentPrice: 3000000, depositAmount: 3000000, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 10000 });
log('fixture', { month, roomA: roomA.roomCode, roomB: roomB.roomCode, contract: c1.contractCode });

// --- đổi tháng làm việc trên giao diện
const goto = async (hash) => { await page.goto(`${base}/#${hash}`); await page.waitForLoadState('networkidle'); };
// nhận phòng thật trên UI: biên bản bàn giao + thu đủ cọc (điều kiện để được tính tiền)
async function checkinUi(contractId) {
  await goto(`/checkin?contract=${contractId}`);
  await page.getByRole('button', { name: 'Lưu biên bản và tiếp tục' }).waitFor();
  await page.waitForTimeout(500); // form còn nạp chỉ số công tơ của phòng
  await page.getByPlaceholder(/Ví dụ: 1 giường/).fill('1 giường, 1 tủ; tường sạch');
  await page.getByRole('button', { name: 'Lưu biên bản và tiếp tục' }).click();
  const saved = page.waitForResponse((r) => r.url().includes(`/api/contracts/${contractId}/deposit`) && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Ghi nhận thu cọc' }).click();
  assert.equal((await saved).status(), 200);
  const dep = await api('GET', `/api/contracts/${contractId}/deposit`);
  assert.ok(dep.paidInFull ?? dep.remaining <= 0, 'cọc phải thu đủ trong DB');
}

await checkinUi(c1.contractId);
await goto('/meters');
const label = await page.getByRole('button', { name: 'Tháng sau' }).locator('xpath=..').innerText();
let [cy, cm] = [2026, 11];
for (let i = (y - cy) * 12 + (m - cm); i > 0; i--) await page.getByRole('button', { name: 'Tháng sau' }).click();
await page.waitForLoadState('networkidle');
log('đã chọn tháng', month, '(thanh tháng:', label.replace(/\s+/g, ' ').trim(), ')');

// --- UC kỳ ghi chỉ số: tạo kỳ trên UI
await page.getByRole('button', { name: /^Tạo kỳ/ }).click({ timeout: 8000 }).catch(async (e) => { console.log('PAGE TEXT:', (await page.locator('body').innerText()).slice(0, 700)); await page.screenshot({ path: 'screens/fail.png' }); throw e; });
await page.getByRole('dialog').getByRole('button', { name: 'Tạo kỳ', exact: true }).click();
await page.getByText('Đã tạo kỳ ghi chỉ số').waitFor();
const period = (await api('GET', '/api/meter-periods?propertyId=1')).find((p) => p.startDate.startsWith(month));
assert.ok(period, 'kỳ phải được tạo trong DB');

// --- chuyển phòng có chốt chỉ số: UI
await goto(`/contracts?open=${c1.contractId}`);
await page.getByRole('dialog').getByRole('button', { name: 'Đổi phòng', exact: true }).click();
const dlg = page.getByRole('dialog').last();
await dlg.locator('select').selectOption(String(roomB.roomId));
await dlg.getByPlaceholder('Mã kỳ ghi chỉ số (tùy chọn)').fill(String(period.periodId));
await dlg.getByPlaceholder('Số điện chốt phòng cũ').fill('130');
await dlg.getByPlaceholder('Số nước chốt phòng cũ').fill('15');
await dlg.getByRole('button', { name: 'Đổi phòng', exact: true }).click();
await page.getByText('Đã đổi phòng').waitFor();
const after = await api('GET', `/api/contracts/${c1.contractId}`);
assert.equal(after.roomCode ?? after.contract?.roomCode, roomB.roomCode, 'hợp đồng phải sang phòng B trong DB');
log('chuyển phòng OK');

// --- khách mới vào phòng A (phòng cũ): dọn phòng rồi lập hợp đồng
await api('PATCH', `/api/chi/rooms/${roomA.roomId}/confirm-cleaned`);
const c2 = await api('POST', '/api/contracts', { roomId: roomA.roomId, tenantId: t2.tenantId, startDate: startMonth, endDate: `${y + 2}-12-31`, monthlyRentPrice: 3000000, depositAmount: 3000000, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 10000 });

await page.keyboard.press('Escape'); // đóng ngăn chi tiết hợp đồng còn mở
await checkinUi(c2.contractId);
// --- màn Chỉ số: phân biệt chỉ số chốt, mốc cho khách mới
await goto('/meters');
const rowOf = (code) => page.getByRole('row').filter({ hasText: code });
await rowOf(roomA.roomCode).first().waitFor();
assert.equal(await rowOf(roomA.roomCode).count(), 1, 'phòng A chỉ có 1 dòng (chốt) trước khi khách mới ghi');
assert.ok(await rowOf(roomA.roomCode).getByText('Chốt chuyển phòng').isVisible(), 'dòng chốt phải được gắn nhãn');
await page.screenshot({ path: 'screens/meters-closing-before.png', fullPage: true });

await page.getByRole('button', { name: new RegExp(`${roomA.roomCode}\\s*Ghi`) }).click();
const rec = page.getByRole('dialog').last();
await rec.getByText('Số cũ của phòng: điện 130, nước 15').waitFor();
// nhập thấp hơn mốc chốt -> chặn ngay trên UI, không gọi API
const before = posts.length;
await rec.getByPlaceholder('Số điện hiện tại').fill('120');
await rec.getByPlaceholder('Số nước hiện tại').fill('16');
await rec.getByRole('button', { name: 'Lưu chỉ số' }).click();
await rec.getByText(/nhỏ hơn số cũ \(130\)/).waitFor();
assert.equal(posts.length, before, 'nhập thấp hơn mốc không được gửi API');
await rec.getByPlaceholder('Số điện hiện tại').fill('140');
await rec.getByPlaceholder('Số nước hiện tại').fill('18');
await rec.getByRole('button', { name: 'Lưu chỉ số' }).click();
await page.getByText('Đã lưu chỉ số').first().waitFor();

const readings = await api('GET', `/api/meter-periods/${period.periodId}/readings`);
const ra = readings.filter((r) => r.roomId === roomA.roomId);
assert.equal(ra.length, 2);
assert.deepEqual([ra[0].oldElectric, ra[0].newElectric, ra[0].closingContractId], [100, 130, c1.contractId]);
assert.deepEqual([ra[1].oldElectric, ra[1].newElectric, ra[1].oldWater, ra[1].newWater, ra[1].closingContractId ?? null], [130, 140, 15, 18, null], 'DB: khách mới lấy mốc chốt');
const rows = rowOf(roomA.roomCode);
await rows.nth(1).waitFor();
assert.equal(await rows.count(), 2, 'UI: 2 dòng cho phòng A');
assert.ok(await rows.nth(0).getByRole('button', { name: 'Điều chỉnh' }).isDisabled(), 'chỉ số chốt đã làm mốc không được điều chỉnh');
assert.ok(await rows.nth(1).getByRole('button', { name: 'Điều chỉnh' }).isEnabled());
log('khách mới ghi chỉ số cùng kỳ OK');

// --- phòng B (khách cũ ở phòng mới): lấy chỉ số ban đầu của phòng
await page.getByRole('button', { name: new RegExp(`${roomB.roomCode}\\s*Ghi`) }).click();
const recB = page.getByRole('dialog').last();
await recB.getByPlaceholder('Số điện hiện tại').fill('120');
await recB.getByPlaceholder('Số nước hiện tại').fill('12');
await recB.getByRole('button', { name: 'Lưu chỉ số' }).click();
await page.getByText('Đã lưu chỉ số').first().waitFor();
for (const r of await api('GET', `/api/meter-periods/${period.periodId}/readings`)) {
  if (r.roomId === roomA.roomId || r.roomId === roomB.roomId) await api('POST', `/api/meter-readings/${r.readingId}/confirm`).catch(() => {});
}

// --- phân quyền: METER_READER chỉ ghi chỉ số; xác nhận/điều chỉnh/đóng kỳ/tạo kỳ/cảnh báo là việc của chủ trọ & quản lý
const roomC = await room('C');
const t3 = await tenant(3);
const c3 = await api('POST', '/api/contracts', { roomId: roomC.roomId, tenantId: t3.tenantId, startDate: startMonth, endDate: `${y + 2}-12-31`, monthlyRentPrice: 3000000, depositAmount: 3000000, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 10000 });
await checkinUi(c3.contractId);
const staff = await api('POST', '/api/staff', { fullName: `E2E Reader ${tag}`, email: `e2e.reader.${tag}@example.test`, roles: ['METER_READER'] });
const rdPass = `Qa#${tag}Mx9`;
const loginApi = async (identifier, password) => (await (await page.request.post(`${base}/api/auth/login`, { data: { identifier, password } })).json()).data;
const first = await loginApi(`e2e.reader.${tag}@example.test`, staff.temporaryPassword);
const chg = await page.request.post(`${base}/api/auth/password`, { headers: { Authorization: `Bearer ${first.accessToken}` }, data: { currentPassword: staff.temporaryPassword, newPassword: rdPass } });
assert.ok(chg.ok(), `đổi mật khẩu tạm: ${chg.status()}`);
const rdToken = (await loginApi(`e2e.reader.${tag}@example.test`, rdPass)).accessToken;
const rdStatus = async (method, url, data) => (await page.request.fetch(`${base}${url}`, { method, headers: { Authorization: `Bearer ${rdToken}`, 'Content-Type': 'application/json' }, data })).status();
const anyReading = (await api('GET', `/api/meter-periods/${period.periodId}/readings`))[0];
assert.equal(await rdStatus('POST', '/api/meter-periods', { propertyId: 1, periodName: 'x', startDate: '2040-01-01', endDate: '2040-01-31' }), 403, 'reader không tạo được kỳ');
assert.equal(await rdStatus('PATCH', `/api/meter-periods/${period.periodId}/closed?value=true`), 403, 'reader không đóng được kỳ');
assert.equal(await rdStatus('POST', `/api/meter-readings/${anyReading.readingId}/confirm`), 403, 'reader không xác nhận được');
assert.equal(await rdStatus('PUT', `/api/meter-readings/${anyReading.readingId}/adjust`, { adjustedField: 'NEW_ELECTRIC', newValue: 999, reason: 'x' }), 403, 'reader không điều chỉnh được');
assert.equal(await rdStatus('GET', '/api/billing/periods?propertyId=1'), 403, 'reader không xem tính tiền');
assert.equal(await rdStatus('GET', `/api/meter-periods/${period.periodId}/readings`), 200, 'reader xem được chỉ số');

const rctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
await rctx.addInitScript((t) => sessionStorage.setItem('stayly.session', t), rdToken);
const rp = await rctx.newPage();
await rp.goto(`${base}/#/meters`); await rp.waitForLoadState('networkidle');
for (let i = (y - 2026) * 12 + (m - 11); i > 0; i--) await rp.getByRole('button', { name: 'Tháng sau' }).click();
await rp.waitForLoadState('networkidle');
const ghiC = rp.getByRole('button', { name: new RegExp(`${roomC.roomCode}.*Ghi`) });
await ghiC.waitFor();
for (const name of ['Đóng kỳ', 'Điều chỉnh', 'Xác nhận', 'Đã xử lý']) assert.equal(await rp.getByRole('button', { name }).count(), 0, `METER_READER không được thấy nút ${name}`);
await ghiC.click();
await rp.getByPlaceholder('Số điện hiện tại').fill('110');
await rp.getByPlaceholder('Số nước hiện tại').fill('11');
await rp.getByRole('dialog').getByRole('button', { name: 'Lưu chỉ số' }).click();
await rp.getByText('Đã lưu chỉ số').first().waitFor();
const rc = (await api('GET', `/api/meter-periods/${period.periodId}/readings`)).find((r) => r.roomId === roomC.roomId);
assert.deepEqual([rc.oldElectric, rc.newElectric, rc.verified], [100, 110, false], 'METER_READER ghi được, chưa xác nhận');
await rctx.close();
log('phân quyền METER_READER OK');


// --- quản lý: xác nhận + điều chỉnh trên UI (lỗi thấp hơn chỉ số cũ, ghi lịch sử), chỉ số chốt đã làm mốc thì bị chặn
await goto('/billing'); await goto('/meters');
const rowC = rowOf(roomC.roomCode);
await rowC.getByRole('button', { name: 'Xác nhận' }).click();
await page.getByText(`Đã xác nhận chỉ số ${roomC.roomCode}`).waitFor();
assert.equal((await api('GET', `/api/meter-periods/${period.periodId}/readings`)).find((r) => r.roomId === roomC.roomId).verified, true);
const rowB = rowOf(roomB.roomCode);
await rowB.getByRole('button', { name: 'Điều chỉnh' }).click();
const adj = page.getByRole('dialog').last();
await adj.getByRole('spinbutton').fill('90');
await adj.getByPlaceholder('Lý do điều chỉnh (bắt buộc)').fill('Đọc nhầm công tơ');
await adj.getByRole('button', { name: 'Lưu điều chỉnh' }).click();
await adj.getByText(/nhỏ hơn chỉ số cũ|nho hon chi so cu/i).waitFor();
await adj.getByRole('spinbutton').fill('125');
await adj.getByRole('button', { name: 'Lưu điều chỉnh' }).click();
await page.getByText('Đã điều chỉnh, có ghi lại lịch sử').waitFor();
const rbAfter = (await api('GET', `/api/meter-periods/${period.periodId}/readings`)).find((r) => r.roomId === roomB.roomId);
assert.equal(rbAfter.newElectric, 125);
const adjLog = await api('GET', `/api/meter-readings/${rbAfter.readingId}/adjustments`);
assert.equal(adjLog.length, 1, 'lịch sử điều chỉnh được ghi');
const closingId = (await api('GET', `/api/meter-periods/${period.periodId}/readings`)).find((r) => r.roomId === roomA.roomId && r.closingContractId).readingId;
const adjClosing = await page.request.put(`${base}/api/meter-readings/${closingId}/adjust`, { headers: { Authorization: `Bearer ${await token()}` }, data: { adjustedField: 'NEW_ELECTRIC', newValue: 131, reason: 'thử', adjustedByUserId: 1 } });
assert.equal(adjClosing.status(), 409, 'chỉ số chốt đã làm mốc không điều chỉnh được (API)');
const dupPeriod = await page.request.post(`${base}/api/meter-periods`, { headers: { Authorization: `Bearer ${await token()}` }, data: { propertyId: 1, periodName: 'trùng', startDate: period.startDate, endDate: period.endDate } });
assert.ok(dupPeriod.status() >= 400 && dupPeriod.status() < 500, `kỳ trùng khoảng thời gian bị từ chối (${dupPeriod.status()})`);
log('quản lý xác nhận/điều chỉnh + lỗi OK');

// --- tính tiền trên UI: hợp đồng chuyển phòng cộng điện nước cả hai phòng, khách mới chỉ tính phần của mình
await goto('/billing');
await page.getByRole('button', { name: /^Tạo kỳ/ }).click();
await page.getByRole('dialog').getByRole('button', { name: 'Tạo kỳ', exact: true }).click();
await page.waitForURL(/#\/billing\/\d+/);
const billingId = Number(page.url().match(/billing\/(\d+)/)[1]);
await page.getByRole('button', { name: 'Tính tiền', exact: true }).click();
await page.getByRole('button', { name: 'Tính lại' }).waitFor();
const calc = await api('GET', `/api/billing/periods/${billingId}`);
const f1 = calc.fees.find((f) => f.contractId === c1.contractId);
const f2 = calc.fees.find((f) => f.contractId === c2.contractId);
assert.ok(f1 && f2, 'cả hai hợp đồng có dòng tính tiền');
assert.ok(f1.electricAmount > 0 && f2.electricAmount > 0, 'có tiền điện');
assert.equal(f1.electricAmount * 10, f2.electricAmount * 55, 'C1: 30+25=55 kWh (đã điều chỉnh 120->125), C2: 10 kWh (cùng đơn giá)');
assert.equal(f1.waterAmount * 3, f2.waterAmount * 7, 'C1: 5+2=7 m3, C2: 3 m3 (cùng đơn giá)');
log('tính tiền OK', { electricC1: f1.electricAmount, electricC2: f2.electricAmount, waterC1: f1.waterAmount, waterC2: f2.waterAmount });
await page.screenshot({ path: 'screens/billing-transfer.png', fullPage: true });

// --- duyệt kỳ + phát hành hóa đơn trên UI, hóa đơn khớp dòng tính tiền
await page.getByRole('button', { name: 'Duyệt kỳ', exact: true }).first().click();
await page.getByRole('dialog').getByRole('button', { name: 'Duyệt kỳ', exact: true }).click();
const force = page.getByRole('dialog').getByRole('button', { name: 'Vẫn duyệt' });
await Promise.race([page.getByText('Đã duyệt kỳ').waitFor(), force.waitFor()]);
if (await force.isVisible().catch(() => false)) { log('kỳ còn phòng thiếu chỉ số (fixture cũ), dùng Vẫn duyệt'); await force.click(); await page.getByText('Đã duyệt kỳ').waitFor(); }
await page.getByRole('button', { name: 'Phát hành hóa đơn', exact: true }).click();
await page.getByText(/Đã phát hành \d+ hóa đơn/).waitFor();
const invs = await api('GET', `/api/invoices?propertyId=1&monthYear=${month}`);
for (const [f, c] of [[f1, c1], [f2, c2]]) {
  const inv = invs.find((i) => i.calculatedFeeId === f.calculatedFeeId);
  assert.ok(inv, `hóa đơn của hợp đồng ${c.contractId}`);
  assert.equal(inv.totalAmount, f.totalCalculatedAmount, 'hóa đơn khớp dòng tính tiền');
  assert.equal(inv.paymentStatus, 'UNPAID');
}
await goto('/invoices');
await page.getByRole('row').filter({ hasText: f1.roomCode }).first().waitFor();
await page.getByRole('row').filter({ hasText: f2.roomCode }).first().waitFor();
log('hóa đơn OK', { c1: invs.find((i) => i.calculatedFeeId === f1.calculatedFeeId).totalAmount, c2: invs.find((i) => i.calculatedFeeId === f2.calculatedFeeId).totalAmount });
await page.screenshot({ path: 'screens/invoices-transfer.png', fullPage: true });


// --- kỳ đã đóng (do duyệt kỳ tính tiền): UI khóa ghi chỉ số, API từ chối
await goto('/meters');
await page.getByRole('button', { name: 'Mở lại kỳ' }).waitFor(); // duyệt kỳ tính tiền đã đóng kỳ ghi số
await page.getByRole('button', { name: 'Ghi chỉ số' }).and(page.locator('[disabled]')).waitFor();
const missingNow = await api('GET', `/api/meter-periods/${period.periodId}/missing-rooms`);
const closedTry = await page.request.post(`${base}/api/meter-readings`, { headers: { Authorization: `Bearer ${await token()}`, 'Content-Type': 'application/json' }, data: { periodId: period.periodId, roomId: (missingNow[0]?.roomId ?? roomA.roomId), newElectric: 999, newWater: 99, recordedByUserId: 1 } });
assert.equal(closedTry.status(), 409, 'kỳ đóng không ghi được chỉ số');
log('kỳ đóng OK');

// ---------- UC50 lỗi và phân quyền: đổi sang phòng đang có người ở bị từ chối, METER_READER không đổi phòng được
assert.equal(await (async () => (await page.request.post(`${base}/api/contracts/${c2.contractId}/transfer`, { headers: { Authorization: `Bearer ${await token()}`, 'Content-Type': 'application/json' }, data: { newRoomId: roomB.roomId } })).status())(), 409, 'phòng đích đang có người ở thì bị từ chối');
const rdTok = await (async () => { const st = await api('POST', '/api/staff', { fullName: `E2E Reader2 ${tag}`, email: `e2e.reader2.${tag}@example.test`, roles: ['METER_READER'] }); const f = await loginApi(`e2e.reader2.${tag}@example.test`, st.temporaryPassword); await page.request.post(`${base}/api/auth/password`, { headers: { Authorization: `Bearer ${f.accessToken}` }, data: { currentPassword: st.temporaryPassword, newPassword: `Qa#${tag}Rd2x` } }); return (await loginApi(`e2e.reader2.${tag}@example.test`, `Qa#${tag}Rd2x`)).accessToken; })();
assert.equal((await page.request.post(`${base}/api/contracts/${c2.contractId}/transfer`, { headers: { Authorization: `Bearer ${rdTok}`, 'Content-Type': 'application/json' }, data: { newRoomId: roomB.roomId } })).status(), 403, 'METER_READER không đổi phòng');
log('UC50 lỗi + phân quyền đổi phòng OK');

console.log(JSON.stringify({ ok: true, month, periodId: period.periodId, c1: c1.contractId, c2: c2.contractId, roomA: roomA.roomId, roomB: roomB.roomId }));
await page.screenshot({ path: 'screens/meters-closing-after.png', fullPage: true });
await browser.close();
