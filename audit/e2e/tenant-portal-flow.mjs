// E2E thật UC36, UC86 (code sẵn, chờ khóa payOS), UC90, UC94, UC123: cổng khách thuê.
import { start, assert, tag, log, base, fixtureInvoice, tenantToken, openAs } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const fx = await fixtureInvoice(api, { label: 'TP' });
const inv = fx.invoice;
// người ở cùng, thanh toán một phần, phiếu thu
await api('POST', '/api/chi/tenants/residents', { roomId: fx.room.roomId, fullName: `Người ở cùng ${tag}`, relationshipWithPrimary: 'Vợ/chồng', identityNumber: `0${tag}9999` }).catch(() => null);
const pay = await api('POST', '/api/payments', { invoiceId: inv.invoiceId, method: 'CASH', amount: 500000 });
const receipt = await api('POST', '/api/receipts', { transactionId: pay.transactionId, voucherType: 'MONTHLY_RENT', amount: 500000, note: 'E2E' });
await api('POST', `/api/receipts/${receipt.voucherId}/send-confirmation`);

const tTok = await tenantToken(s, fx.tenant.tenantId); // có tài khoản rồi mới gửi thông báo vào ứng dụng
await api('POST', `/api/invoices/${inv.invoiceId}/send-app`);
const t = await openAs(s, tTok, { width: 420, height: 1000 });
const tp = t.page;

// ---------- UC36 phòng của tôi + người ở cùng
await tp.goto(`${base}/#/me/contracts`); await tp.waitForLoadState('networkidle');
await tp.getByText(fx.room.roomCode).first().waitFor();
await tp.getByText('Số người tối đa').first().waitFor();
const rooms = await s.raw('GET', '/api/me/rooms', undefined, tTok).then((r) => r.json());
assert.equal((rooms.data ?? rooms)[0].roomCode, fx.room.roomCode, 'chỉ thấy phòng của mình');
log('UC36 phòng của tôi OK');

// ---------- UC94 lịch sử thanh toán + công nợ của tôi
await tp.goto(`${base}/#/me/invoices`); await tp.waitForLoadState('networkidle');
await tp.getByText(inv.invoiceCode).first().waitFor();
await tp.getByText(/Đã trả .* còn/).first().waitFor();
await tp.getByRole('button', { name: 'Xem chi tiết các khoản' }).first().click();
const d = tp.getByRole('dialog').last();
await d.getByRole('heading', { name: 'Lịch sử thanh toán' }).waitFor();
await d.getByText(/500\.000 ₫ · tiền mặt/).waitFor();
await d.getByRole('button', { name: 'Đóng' }).last().click();
log('UC94 lịch sử thanh toán OK');

// ---------- UC90 biên nhận của tôi
await tp.getByText(receipt.voucherCode).first().waitFor();
const popup = tp.waitForEvent('popup');
await tp.getByRole('button', { name: 'Xem và in biên nhận' }).first().click();
const rp = await popup;
await rp.waitForLoadState();
assert.match(await rp.content(), new RegExp(receipt.voucherCode), 'bản in biên nhận có mã phiếu');
assert.doesNotMatch(await rp.content(), /E2E<|ghi chú nội bộ/i, 'bản in không lộ ghi chú nội bộ');
// khách khác không in được biên nhận
const other = await fixtureInvoice(api, { label: 'TP2' });
const oTok = await tenantToken(s, other.tenant.tenantId);
assert.equal(await status(oTok, 'GET', `/api/me/receipts/${receipt.voucherId}/print`), 404, 'khách khác không xem biên nhận');
log('UC90 biên nhận phía khách OK');

// ---------- UC86 payOS: cổng chưa bật -> thông báo rõ, không treo; code sẵn sàng khi có khóa
await tp.getByRole('button', { name: 'Thanh toán online (payOS)' }).first().click();
await tp.getByText(/Cổng thanh toán online chưa được bật/).first().waitFor();
log('UC86 payOS: giao diện + luồng gọi API xong, BLOCKED chờ app.payment.payos.* (khóa thật)');

// ---------- UC123 trung tâm thông báo
await tp.goto(`${base}/#/me/notifications`); await tp.waitForLoadState('networkidle');
await tp.getByText(new RegExp(inv.invoiceCode)).first().waitFor({ timeout: 8000 }).catch(async (e) => { console.log((await tp.locator('main, #view').first().innerText()).slice(0, 700)); console.log(JSON.stringify((await s.raw('GET', '/api/me/notifications?size=30', undefined, tTok).then((r) => r.json())).data.items.slice(0,3))); throw e; });
const unread = (await s.raw('GET', '/api/me/notifications?size=30', undefined, tTok).then((r) => r.json())).data.items.filter((n) => !n.isRead).length;
assert.ok(unread > 0, 'có thông báo chưa đọc');
await tp.getByRole('button', { name: 'Đánh dấu đã đọc', exact: true }).first().click();
await tp.waitForTimeout(800);
const after = (await s.raw('GET', '/api/me/notifications?size=30', undefined, tTok).then((r) => r.json())).data.items;
assert.equal(after.filter((n) => !n.isRead).length, unread - 1, 'đánh dấu một thông báo đã đọc');
await tp.getByRole('button', { name: 'Đánh dấu đã đọc hết' }).click();
await tp.waitForTimeout(800);
await tp.getByRole('button', { name: 'Xóa', exact: true }).first().click();
await tp.waitForTimeout(800);
const left = (await s.raw('GET', '/api/me/notifications?size=30', undefined, tTok).then((r) => r.json())).data.items;
assert.equal(left.length, after.length - 1, 'xóa một thông báo đã đọc');
// không đọc/xóa thông báo của người khác
const foreignId = left[0].userNotificationId;
assert.ok([403, 404].includes(await status(oTok, 'DELETE', `/api/me/notifications/${foreignId}`)), 'khách khác không xóa được thông báo');
log('UC123 trung tâm thông báo OK');

console.log(JSON.stringify({ ok: true }));
await s.browser.close();
