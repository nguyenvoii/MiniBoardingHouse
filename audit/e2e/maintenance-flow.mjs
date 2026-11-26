// E2E thật UC110, UC111, UC114, UC115: khách báo sự cố/kiểm tra công tơ -> phân công -> xử lý -> hoàn tất -> điều chỉnh hóa đơn -> đánh giá.
import { start, assert, tag, log, base, fixtureInvoice, tenantToken, openAs } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const toast = (t) => page.getByText(t).first().waitFor();
const fx = await fixtureInvoice(api, { label: 'MT' });
const inv = fx.invoice;
const tTok = await tenantToken(s, fx.tenant.tenantId);
const t = await openAs(s, tTok, { width: 420, height: 900 });

// ---------- UC110/111 khách báo "nghi công tơ tính sai" (khẩn)
await t.page.goto(`${base}/#/me/support`); await t.page.waitForLoadState('networkidle');
await t.page.getByRole('button', { name: 'Báo sự cố' }).click();
let td = t.page.getByRole('dialog').last();
await td.getByRole('button', { name: 'Gửi', exact: true }).click();
await td.locator('.form-error, [role=alert]').first().waitFor(); // thiếu mô tả bị từ chối
await td.locator('select').first().selectOption('METER_CHECK');
await td.getByPlaceholder('Mô tả sự cố để chủ trọ xử lý nhanh').fill('Số điện tháng này cao bất thường, xin kiểm tra công tơ');
await td.locator('input[type=checkbox]').check();
await td.getByRole('button', { name: 'Gửi', exact: true }).click();
await t.page.getByText('Đã gửi yêu cầu').first().waitFor();
const tick = (await api('GET', '/api/maintenance-requests?size=100')).items.find((x) => x.roomCode === fx.room.roomCode);
assert.ok(tick && tick.category === 'METER_CHECK' && tick.priority === 'URGENT' && tick.status === 'OPEN');
log('UC110/111 khách gửi yêu cầu kiểm tra công tơ OK');

// ---------- UC114 phân công, bắt đầu, hoàn tất
await goto('/maintenance'); await page.waitForTimeout(800);
const open = async () => { await page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close())); await goto('/billing'); await goto('/maintenance'); await page.waitForTimeout(800); await page.getByRole('row').filter({ hasText: fx.room.roomCode }).first().click(); return page.getByRole('dialog').last(); };
let dlg = await open();
await dlg.getByRole('button', { name: /Giao người khác|Phân công/ }).first().click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Phân công', exact: true }).click();
await page.waitForTimeout(1200);
assert.equal((await api('GET', `/api/maintenance-requests/${tick.requestId}`)).status, 'ASSIGNED');
dlg = await open();
await dlg.getByRole('button', { name: 'Bắt đầu xử lý' }).click();
await page.waitForTimeout(1200);
assert.equal((await api('GET', `/api/maintenance-requests/${tick.requestId}`)).status, 'IN_PROGRESS');
dlg = await open();
await dlg.getByRole('button', { name: 'Ghi kết quả, hoàn tất' }).click();
let rs = page.getByRole('dialog').last();
await rs.getByPlaceholder('0').fill('-5');
await rs.getByRole('button', { name: 'Hoàn tất', exact: true }).click();
await rs.locator('.form-error, [role=alert]').first().waitFor(); // chi phí âm bị từ chối
await rs.getByPlaceholder('0').fill('35000');
await rs.getByPlaceholder('Đã làm gì, thay linh kiện gì').fill('Kiểm tra công tơ: đọc nhầm số, đã hiệu chỉnh');
await rs.getByRole('button', { name: 'Hoàn tất', exact: true }).click();
await page.waitForTimeout(1200);
let cur = await api('GET', `/api/maintenance-requests/${tick.requestId}`);
assert.equal(cur.status, 'RESOLVED');
assert.equal(cur.log.repairCost, 35000);
log('UC114 phân công / xử lý / chi phí OK');

// ---------- UC111 điều chỉnh hóa đơn theo kết quả kiểm tra
dlg = await open();
await dlg.getByRole('button', { name: 'Điều chỉnh hóa đơn' }).click();
const ad = page.getByRole('dialog').last();
await ad.getByRole('button', { name: 'Điều chỉnh hóa đơn' }).click();
await ad.locator('.form-error, [role=alert]').first().waitFor(); // thiếu số tiền và lý do
await ad.getByLabel('Số tiền giảm').fill(String(inv.remainingAmount + 1000));
await ad.getByLabel('Lý do điều chỉnh').fill('Vượt số còn nợ');
await ad.getByRole('button', { name: 'Điều chỉnh hóa đơn' }).click();
await ad.locator('.form-error, [role=alert]').first().waitFor();
assert.equal((await api('GET', `/api/invoices/${inv.invoiceId}`)).totalAmount, inv.totalAmount, 'giảm vượt số nợ bị từ chối');
await ad.getByLabel('Số tiền giảm').fill('150000');
await ad.getByLabel('Lý do điều chỉnh').fill('Công tơ đọc nhầm, giảm 150.000');
await ad.getByRole('button', { name: 'Điều chỉnh hóa đơn' }).click();
await toast('Đã điều chỉnh hóa đơn theo kết quả kiểm tra');
assert.equal((await api('GET', `/api/invoices/${inv.invoiceId}`)).totalAmount, inv.totalAmount - 150000);
log('UC111 điều chỉnh hóa đơn OK');

// ---------- UC114 xác nhận hoàn tất; UC115 khách đánh giá
dlg = await open();
await dlg.getByRole('button', { name: 'Xác nhận hoàn tất' }).click();
await page.waitForTimeout(1200);
assert.equal((await api('GET', `/api/maintenance-requests/${tick.requestId}`)).status, 'CLOSED');
await t.page.goto(`${base}/#/me/support`); await t.page.reload(); await t.page.waitForLoadState('networkidle');
await t.page.getByRole('button', { name: /Đánh giá/ }).first().click();
let rt = t.page.getByRole('dialog').last();
await rt.locator('select').selectOption('4');
await rt.getByRole('button', { name: 'Gửi đánh giá' }).click();
await t.page.getByText('Cảm ơn bạn đã đánh giá').first().waitFor();
cur = await api('GET', `/api/maintenance-requests/${tick.requestId}`);
assert.equal(cur.log.tenantRating, 4);
assert.equal(await status(tTok, 'POST', `/api/me/maintenance-requests/${tick.requestId}/rating`, { rating: 5 }), 400, 'đánh giá lần hai bị từ chối');
assert.ok([400, 409].includes(await status(tTok, 'POST', `/api/me/maintenance-requests/${tick.requestId}/rating`, { rating: 9 })), 'ngoài 1-5 bị từ chối');
log('UC115 đánh giá OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
const cashier = await s.staffToken('CASHIER');
for (const [m, u, d] of [['GET', '/api/maintenance-requests'], ['PATCH', `/api/maintenance-requests/${tick.requestId}/assign`, { staffId: 1 }], ['POST', `/api/maintenance-requests/${tick.requestId}/invoice-adjustment`, { invoiceId: inv.invoiceId, reduceAmount: 1, reason: 'x' }]]) {
  assert.ok([401, 403].includes(await status(reader.token, m, u, d)), `METER_READER ${m} ${u} bị chặn`);
}
assert.equal(await status(tTok, 'GET', '/api/maintenance-requests'), 403, 'khách không dùng API quản lý sự cố');
const other = await fixtureInvoice(api, { label: 'MT2' });
const oTok = await tenantToken(s, other.tenant.tenantId);
assert.ok([403, 404].includes(await status(oTok, 'POST', `/api/me/maintenance-requests/${tick.requestId}/rating`, { rating: 3 })), 'khách khác không đánh giá sự cố của người khác');
log('phân quyền sự cố OK');

console.log(JSON.stringify({ ok: true, request: tick.requestId }));
await s.browser.close();
