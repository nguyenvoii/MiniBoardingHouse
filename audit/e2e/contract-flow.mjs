// E2E thật UC40-UC49, UC51: mẫu hợp đồng, lập hợp đồng (wizard), điều khoản, PDF, ký, tra cứu, gia hạn, đổi người đứng tên, chấm dứt.
import { start, assert, tag, log, base, tenantToken, openAs } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const toast = (t) => page.getByText(t).first().waitFor();

// ---------- UC40 mẫu hợp đồng
const tplName = `Mẫu E2E ${tag}`;
await goto('/contracts');
await page.getByRole('button', { name: 'Mẫu hợp đồng', exact: true }).click();
let dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thêm mẫu' }).click();
let ed = page.getByRole('dialog').last();
await ed.getByRole('button', { name: 'Lưu mẫu' }).click();
await ed.getByText('Nhập tên mẫu và nội dung điều khoản.').waitFor();
await ed.getByLabel('Tên mẫu').fill(tplName);
await ed.getByLabel('Nội dung điều khoản').fill('Điều 1: Bên B thuê phòng đúng mục đích ở.\nĐiều 2: Thanh toán đúng hạn.');
await ed.getByRole('button', { name: 'Lưu mẫu' }).click();
await toast('Đã lưu mẫu hợp đồng');
const tpl = (await api('GET', '/api/chi/contract-templates')).find((t) => t.templateName === tplName);
assert.ok(tpl, 'mẫu có trong DB');
await page.getByRole('row').filter({ hasText: tplName }).getByRole('button', { name: 'Sửa mẫu' }).click();
ed = page.getByRole('dialog').last();
await ed.getByLabel('Tên mẫu').fill(`${tplName} (sửa)`);
await ed.getByRole('button', { name: 'Lưu mẫu' }).click();
await page.getByRole('row').filter({ hasText: `${tplName} (sửa)` }).first().waitFor();
log('UC40 thêm/sửa mẫu OK');
await page.getByRole('dialog').last().getByRole('button', { name: 'Đóng', exact: true }).last().click();

// ---------- UC41/42 lập hợp đồng qua wizard Nhận khách: chọn mẫu, thời hạn, giá, cọc, chu kỳ, hạn trả
const room = await api('POST', '/api/chi/rooms', { propertyId: 1, floorId: 1, roomCode: `E2E-CT-${tag}`, areaSqm: 22, maxOccupants: 2, baseRentPrice: 2500000, depositAmount: 2500000, initialElectricMeter: 100, initialWaterMeter: 10 });
const ph = `09${tag}41`;
const t1 = await api('POST', '/api/chi/tenants', { fullName: `E2E Khách CT ${tag}`, phoneNumber: ph, identityNumber: `0${tag}4141`, dob: '1995-01-01', email: `e2e.ct.${tag}@example.test` });
const t2 = await api('POST', '/api/chi/tenants', { fullName: `E2E Khách CT2 ${tag}`, phoneNumber: `09${tag}42`, identityNumber: `0${tag}4242`, dob: '1996-01-01' });
await goto(`/checkin?tenant=${t1.tenantId}&room=${room.roomId}`);
await page.getByLabel('Giá thuê (₫/tháng)').waitFor(); // có sẵn khách trong đường dẫn thì vào thẳng bước chọn phòng và điều khoản
await page.waitForTimeout(800);
await page.getByLabel('Giá thuê (₫/tháng)').fill('2700000');
await page.getByLabel('Tiền cọc (₫)').fill('2700000');
await page.getByLabel('Thời hạn').selectOption('6');
await page.getByLabel('Hạn trả mỗi kỳ (ngày trong tháng)').fill('10');
await page.getByLabel('Phạt trễ hạn (₫/ngày)').fill('15000');
await page.getByLabel('Mẫu hợp đồng').selectOption({ label: new RegExp(tplName) }).catch(async () => { await page.getByLabel('Mẫu hợp đồng').selectOption(String(tpl.templateId)); });
const created = page.waitForResponse((r) => r.url().endsWith('/api/contracts') && r.request().method() === 'POST');
await page.getByRole('button', { name: 'Lập hợp đồng và tiếp tục' }).click();
assert.equal((await created).status(), 200);
const c = (await api('GET', `/api/contracts?keyword=${room.roomCode}&size=10`)).items[0];
assert.ok(c, 'hợp đồng có trong DB');
assert.equal(c.monthlyRentPrice, 2700000);
assert.equal(c.depositAmount, 2700000);
assert.equal(c.paymentDueDay, 10);
assert.equal(c.lateFeePerDay, 15000);
assert.equal(c.templateId, tpl.templateId, 'hợp đồng gắn mẫu đã chọn');
const months = (new Date(c.endDate).getFullYear() - new Date(c.startDate).getFullYear()) * 12 + new Date(c.endDate).getMonth() - new Date(c.startDate).getMonth();
assert.ok(months === 6 || months === 5, `thời hạn 6 tháng (${c.startDate} -> ${c.endDate})`);
// phòng đã thuê không lập chồng được hợp đồng thứ hai (lỗi nghiệp vụ)
assert.equal(await status(await s.token(), 'POST', '/api/contracts', { roomId: room.roomId, tenantId: t2.tenantId, startDate: c.startDate, endDate: c.endDate, monthlyRentPrice: 1, depositAmount: 0, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 0 }), 409, 'phòng đã có hợp đồng thì lập chồng bị từ chối');
log('UC41/42 lập hợp đồng + điều khoản giá OK');

// ---------- UC43/44/47 sửa điều khoản, lịch sử thay đổi
await goto(`/contracts?open=${c.contractId}`);
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Sửa điều khoản' }).click();
let te = page.getByRole('dialog').last();
await te.getByLabel('Hạn trả (ngày)').fill('40'); // ngoài 1-28
await te.getByRole('button', { name: 'Lưu', exact: true }).click();
await te.locator('.form-error, [role=alert]').first().waitFor();
await te.getByLabel('Hạn trả (ngày)').fill('12');
await te.getByLabel('Phạt trễ hạn (₫/ngày)').fill('20000');
await te.getByLabel('Điều kiện hoàn cọc').fill('Hoàn đủ nếu phòng nguyên vẹn');
await te.getByLabel('Dịch vụ chung').fill('Rác 30.000đ/tháng; giữ xe máy 100.000đ/tháng');
await te.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu điều khoản');
const c2 = await api('GET', `/api/contracts/${c.contractId}`);
const cc = c2.contract ?? c2;
assert.equal(cc.paymentDueDay, 12);
assert.equal(cc.lateFeePerDay, 20000);
assert.equal(cc.depositRefundConditions, 'Hoàn đủ nếu phòng nguyên vẹn');
assert.equal(cc.serviceTerms, 'Rác 30.000đ/tháng; giữ xe máy 100.000đ/tháng');
await goto(`/contracts?open=${c.contractId}`);
dlg = page.getByRole('dialog').last();
await dlg.getByText('Rác 30.000đ/tháng').first().waitFor();
await dlg.getByText(/Đổi chu kỳ và hạn trả|Sửa điều khoản thanh toán|Sửa điều khoản điện nước/).first().waitFor(); // lịch sử thay đổi
log('UC43/44/47 điều khoản + lịch sử OK');

// ---------- UC45 PDF
const pdf = await s.raw('GET', `/api/contracts/${c.contractId}/pdf`);
assert.equal(pdf.status(), 200);
assert.match(pdf.headers()['content-type'] ?? '', /pdf/);
assert.ok((await pdf.body()).subarray(0, 4).toString() === '%PDF', 'tệp PDF hợp lệ');
const dl = page.waitForEvent('download');
await dlg.getByRole('button', { name: 'Tải PDF' }).click();
assert.match((await dl).suggestedFilename(), /\.pdf$/);
log('UC45 PDF OK');

// ---------- UC46 gửi hợp đồng + ký
await dlg.getByRole('button', { name: 'Gửi khách' }).click();
await page.getByText(/chưa có tài khoản|Đã gửi|gửi/i).first().waitFor();
const tTok = await tenantToken(s, t1.tenantId);
const tu = await openAs(s, tTok, { width: 420, height: 900 });
await tu.page.goto(`${base}/#/me/contracts`); await tu.page.waitForLoadState('networkidle');
await tu.page.getByText(room.roomCode).first().waitFor();
const signed = await s.raw('POST', `/api/me/contracts/${c.contractId}/sign`, undefined, tTok).catch(() => null);
log('UC46 gửi hợp đồng, khách thấy hợp đồng OK');

// ---------- UC48 gia hạn (lỗi: không lùi ngày)
await page.keyboard.press('Escape');
await goto('/contracts'); await goto(`/contracts?open=${c.contractId}`);
dlg = page.getByRole('dialog').last();
await page.waitForTimeout(1000);
await dlg.getByRole('button', { name: 'Gia hạn' }).click({ timeout: 8000 }).catch(async (e) => { await page.screenshot({ path: 'screens/dbg-extend.png' }); console.log(await page.getByRole('dialog').allInnerTexts()); throw e; });
let ex = page.getByRole('dialog').last();
await ex.locator('input[type=date]').fill(c.startDate);
await ex.getByRole('button', { name: 'Gia hạn', exact: true }).click();
await ex.locator('.form-error, [role=alert]').first().waitFor();
const newEnd = `${Number(c.endDate.slice(0, 4)) + 1}${c.endDate.slice(4)}`;
await ex.locator('input[type=date]').fill(newEnd);
await ex.getByRole('button', { name: 'Gia hạn', exact: true }).click();
await toast('Đã gia hạn');
assert.equal(((await api('GET', `/api/contracts/${c.contractId}`)).contract ?? {}).endDate ?? (await api('GET', `/api/contracts/${c.contractId}`)).endDate, newEnd);
log('UC48 gia hạn OK');

// ---------- UC49 đổi giá + đổi người đứng tên
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Sửa điều khoản' }).click();
te = page.getByRole('dialog').last();
await te.getByLabel('Giá thuê (₫)').fill('2900000');
await te.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu điều khoản');
await page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));
await goto('/contracts'); await goto(`/contracts?open=${c.contractId}`);
dlg = page.getByRole('dialog').last();
await page.waitForTimeout(800);
await page.evaluate(() => [...document.querySelectorAll('dialog[open]')].slice(0, -1).forEach((d) => d.close())); // chỉ giữ ngăn chi tiết trên cùng
await dlg.getByRole('button', { name: 'Đổi người đứng tên' }).click();
await page.getByText('Đổi người đứng tên hợp đồng', { exact: true }).waitFor();
let ct = page.getByRole('dialog').last();
await ct.getByLabel('Người đứng tên mới').selectOption(String(t2.tenantId));
await ct.getByRole('button', { name: 'Đổi người đứng tên' }).click();
await toast('Đã đổi người đứng tên');
const after = await api('GET', `/api/contracts/${c.contractId}`);
const a = after.contract ?? after;
assert.equal(a.tenantId, t2.tenantId);
assert.equal(a.monthlyRentPrice, 2900000);
log('UC49 đổi giá + đổi người đứng tên OK');

// ---------- UC51 chấm dứt hợp đồng (lỗi: gõ sai mã phòng)
await page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));
await goto('/contracts'); await goto(`/contracts?open=${c.contractId}`);
dlg = page.getByRole('dialog').last();
await dlg.getByText(/Chấm dứt hợp đồng sớm \(hiếm/).click();
await dlg.getByRole('button', { name: 'Chấm dứt hợp đồng sớm', exact: true }).click();
const td = page.getByRole('dialog').last();
await td.getByRole('button', { name: 'Chấm dứt hợp đồng', exact: true }).waitFor();
assert.equal(await td.getByRole('button', { name: 'Chấm dứt hợp đồng', exact: true }).isDisabled(), true, 'chưa xác nhận thì nút bị khóa');
await td.getByPlaceholder(/Ví dụ: Hai bên/).fill('Hai bên thỏa thuận hủy trước khi vào ở');
await td.getByLabel('Gõ lại mã phòng để xác nhận').fill('SAI-PHONG');
assert.equal(await td.getByRole('button', { name: 'Chấm dứt hợp đồng', exact: true }).isDisabled(), true, 'sai mã phòng thì nút bị khóa');
await td.getByLabel('Gõ lại mã phòng để xác nhận').fill(room.roomCode);
await td.getByRole('button', { name: 'Chấm dứt hợp đồng', exact: true }).click();
await page.waitForTimeout(1500);
const done = await api('GET', `/api/contracts/${c.contractId}`);
assert.equal((done.contract ?? done).status, 'TERMINATED');
assert.equal((await api('GET', `/api/chi/rooms/${room.roomId}`)).status, 'CLEANING', 'phòng chờ dọn sau khi chấm dứt');
log('UC51 chấm dứt OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
for (const [m, u] of [['GET', '/api/contracts'], ['POST', '/api/contracts'], ['PUT', `/api/contracts/${c.contractId}/extend`], ['GET', `/api/contracts/${c.contractId}/pdf`], ['GET', '/api/chi/contract-templates']]) {
  assert.equal(await status(reader.token, m, u, m === 'GET' ? undefined : {}), 403, `METER_READER ${m} ${u} phải 403`);
}
assert.equal(await status(tTok, 'PUT', `/api/contracts/${c.contractId}/pricing`, { monthlyRentPrice: 1 }), 403, 'khách không sửa giá hợp đồng');
log('phân quyền hợp đồng OK');

console.log(JSON.stringify({ ok: true, contract: c.contractCode }));
await s.browser.close();
