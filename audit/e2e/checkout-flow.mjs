// E2E thật UC101-107: khách báo trả phòng -> hẹn kiểm tra -> hiện trạng (ảnh, hư hỏng) -> quyết toán cọc -> hoàn cọc -> thanh lý.
import { start, assert, tag, log, base, tenantToken, openAs } from './lib.mjs';
import { fixtureRoom } from './lib2.mjs';
import { fixtureInvoice } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
const toast = (t) => page.getByText(t).first().waitFor();
const fx = await fixtureRoom(api, { label: 'CO', startMonth: '2026-11' });
log('fixture', fx.room.roomCode, fx.contract.contractCode);

// ---------- UC101 khách báo trả phòng trên giao diện của khách
const tTok = await tenantToken(s, fx.tenant.tenantId);
const t = await openAs(s, tTok, { width: 420, height: 900 });
await t.page.goto(`${base}/#/me`); await t.page.waitForLoadState('networkidle');
await t.page.getByRole('button', { name: 'Báo trả phòng', exact: true }).click();
const td = t.page.getByRole('dialog').last();
await td.locator('input[type=date]').evaluate((el) => { el.value = '2020-01-01'; el.dispatchEvent(new Event('input', { bubbles: true })); }); // quá khứ
await td.getByRole('button', { name: 'Gửi báo trả phòng' }).click();
await td.locator('.form-error, [role=alert]').first().waitFor(); // ngày quá khứ bị từ chối
const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
await td.locator('input[type=date]').fill(tomorrow);
await td.getByRole('button', { name: 'Gửi báo trả phòng' }).click();
await t.page.getByText('Đã gửi báo trả phòng').waitFor();
const co = (await api('GET', '/api/checkout-requests?size=100')).items.find((x) => x.roomCode === fx.room.roomCode);
assert.ok(co && co.status === 'PENDING', 'yêu cầu trả phòng PENDING trong DB');
await t.page.getByText(/chờ chủ trọ hẹn kiểm tra phòng/).first().waitFor();
log('UC101 khách báo trả phòng OK');

// ---------- UC101 chủ trọ xác nhận lịch
await goto('/checkout');
await page.getByRole('row').filter({ hasText: fx.room.roomCode }).first().click();
let dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Xác nhận lịch kiểm tra' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Xác nhận', exact: true }).click();
await toast('Đã xác nhận lịch kiểm tra phòng');
assert.equal((await api('GET', `/api/checkout-requests/${co.checkoutId}`)).status, 'INSPECTING');

// ---------- UC102/103 hiện trạng: lỗi số thấp hơn, hư hỏng, ảnh
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Ghi nhận hiện trạng' }).click();
let ins = page.getByRole('dialog').last();
await ins.getByPlaceholder('Số điện cuối').fill('50');
await ins.getByPlaceholder('Số nước cuối').fill('5');
await ins.getByRole('button', { name: 'Lưu hiện trạng' }).click();
await ins.getByText(/nhỏ hơn chỉ số kỳ trước/).waitFor(); // thấp hơn số ban đầu 100/10 bị chặn ngay cạnh ô nhập
await ins.getByPlaceholder('Số điện cuối').fill('140');
await ins.getByPlaceholder('Số nước cuối').fill('14');
await ins.getByRole('button', { name: 'Thêm thiết bị hư hỏng' }).click();
await ins.getByPlaceholder('Tiền bồi thường').fill('25000'); // có tiền nhưng chưa có tên
await ins.getByRole('button', { name: 'Lưu hiện trạng' }).click();
await ins.getByText(/chưa ghi tên thiết bị/).waitFor();
await ins.getByPlaceholder('Thiết bị hư hỏng').fill('Gương phòng tắm');
await ins.getByLabel('Ảnh hiện trạng phòng').setInputFiles({ name: 'hien-trang.png', mimeType: 'image/png', buffer: png });
await ins.getByRole('button', { name: 'Lưu hiện trạng' }).click();
await toast('Đã ghi nhận hiện trạng phòng');
const insp = await api('GET', `/api/checkout-requests/${co.checkoutId}/inspection`);
assert.equal(insp.finalElectricMeter, 140);
assert.equal(insp.damageCompensationAmount, 25000);
assert.ok(insp.inspectionPhotoUrl, 'ảnh hiện trạng được lưu');
assert.equal((await page.request.get(`${base}${insp.inspectionPhotoUrl}`)).status(), 401, 'ảnh hiện trạng cần đăng nhập');
await page.getByRole('dialog').last().getByRole('button', { name: 'Xem ảnh' }).click();
await page.getByRole('dialog').last().getByAltText('Lúc trả phòng').waitFor();
await page.getByRole('dialog').last().getByRole('button', { name: 'Đóng', exact: true }).last().click();
log('UC102/103 hiện trạng + ảnh + bồi thường OK');

// ---------- UC104/105/106 quyết toán, hoàn cọc
const st = await api('GET', `/api/checkout-requests/${co.checkoutId}/settlement`);
assert.equal(st.refundable, 3000000 - 25000 - st.debtTotal, 'hoàn = cọc - bồi thường - nợ');
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Lập phiếu hoàn cọc' }).click();
await toast('Đã lập phiếu hoàn cọc');
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Ghi nhận đã hoàn cọc' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Đã hoàn tiền' }).click();
await toast('Đã ghi nhận hoàn cọc');
log('UC104-106 quyết toán + hoàn cọc OK');

// ---------- UC107 thanh lý + biên bản
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thanh lý hợp đồng' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Thanh lý', exact: true }).click();
await page.waitForTimeout(1500);
const after = await api('GET', `/api/checkout-requests/${co.checkoutId}`);
assert.equal(after.status, 'FINALIZED');
assert.equal((await api('GET', `/api/chi/rooms/${fx.room.roomId}`)).status, 'CLEANING', 'phòng sang chờ dọn sau thanh lý');
log('UC107 thanh lý OK');

// ---------- UC105 có nợ hóa đơn: khấu trừ nợ trước rồi mới tới bồi thường; cọc thiếu thì khách phải trả thêm
const dbt = await fixtureInvoice(api, { label: 'CD' });
const coD = await api('POST', '/api/checkout-requests', { contractId: dbt.contract.contractId, expectedCheckoutDate: tomorrow });
await api('PATCH', `/api/checkout-requests/${coD.checkoutId}/confirm`, { expectedCheckoutDate: tomorrow });
await api('PUT', `/api/checkout-requests/${coD.checkoutId}/inspection`, { finalElectricMeter: 160, finalWaterMeter: 16, damages: [{ name: 'Cửa tủ', cost: 25000 }], inspectionPhotoUrl: null });
const stD = await api('GET', `/api/checkout-requests/${coD.checkoutId}/settlement`);
assert.equal(stD.debtTotal, dbt.invoice.remainingAmount, 'nợ hóa đơn được tính vào quyết toán');
assert.equal(stD.deductedDebt, 3000000, 'nợ khấu trừ tối đa bằng tiền cọc');
assert.equal(stD.deductedDamage, 0, 'cọc hết thì chưa khấu trừ được bồi thường');
assert.equal(stD.refundable, 0);
assert.equal(stD.tenantMustPay, dbt.invoice.remainingAmount + 25000 - 3000000, 'khách phải trả thêm phần thiếu');
log('UC105 khấu trừ nợ + bồi thường vào cọc (cọc thiếu) OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
for (const [m, u] of [['GET', '/api/checkout-requests'], ['PATCH', `/api/checkout-requests/${co.checkoutId}/confirm`], ['POST', `/api/checkout-requests/${co.checkoutId}/refund-voucher`]]) {
  assert.equal(await status(reader.token, m, u, m === 'PATCH' ? { expectedCheckoutDate: tomorrow } : undefined), 403, `METER_READER ${m} ${u} phải 403`);
}
assert.equal(await status(tTok, 'GET', '/api/checkout-requests'), 403, 'khách không xem danh sách trả phòng của khu');
const other = await fixtureRoom(api, { label: 'CO2', startMonth: '2026-11' });
const oTok = await tenantToken(s, other.tenant.tenantId);
assert.ok([403, 404].includes(await status(oTok, 'GET', `/api/checkout-requests/${co.checkoutId}`)), 'khách khác không xem yêu cầu trả phòng');
log('phân quyền trả phòng OK');

console.log(JSON.stringify({ ok: true, checkoutId: co.checkoutId }));
await s.browser.close();
