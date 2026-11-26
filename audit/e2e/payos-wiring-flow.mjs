// UC86 đầy đủ: khách bấm Thanh toán online -> API tạo giao dịch GATEWAY và gọi payOS (HTTPS, ký HMAC) -> mở trang thanh toán ->
// payOS báo PAID -> khách bấm Kiểm tra -> hóa đơn được cộng tiền. Dùng payOS GIẢ cục bộ (fake-payos.mjs); thanh toán thật cần khóa payOS.
import { start, assert, log, base, fixtureInvoice, tenantToken, openAs } from './lib.mjs';
const s = await start();
const { api } = s;
assert.match(base, /18099/, 'chạy trên backend bật payOS (xem fake-payos.mjs)');
const fx = await fixtureInvoice(api, { label: 'PO' });
const tTok = await tenantToken(s, fx.tenant.tenantId);
const t = await openAs(s, tTok, { width: 420, height: 900 });
await t.ctx.route('https://localhost:5443/**', (r) => r.continue());
await t.page.goto(`${base}/#/me/invoices`); await t.page.waitForLoadState('networkidle');
const popup = t.page.waitForEvent('popup');
const checkout = t.page.waitForResponse((r) => /\/api\/me\/payments\/\d+\/payos\/checkout/.test(r.url()));
await t.page.getByRole('button', { name: 'Thanh toán online (payOS)' }).first().click();
const r = await checkout;
assert.equal(r.status(), 200);
const co = (await r.json()).data;
assert.match(co.checkoutUrl, /^https:\/\/localhost:5443\/pay\/\d+$/);
await popup; // trang thanh toán payOS mở ở tab mới (noopener nên không đọc được URL, đã kiểm checkoutUrl ở API)
const gw = (await api('GET', `/api/payments?invoiceId=${fx.invoice.invoiceId}&size=10`)).items.find((p) => p.method === 'GATEWAY');
assert.ok(gw && gw.status === 'FAILED', 'giao dịch GATEWAY đang chờ');
// chưa thanh toán: Kiểm tra -> chưa xác nhận, hóa đơn chưa đổi
await t.page.getByRole('button', { name: 'Kiểm tra đã thanh toán' }).click();
await t.page.getByText(/payOS chưa xác nhận/).first().waitFor();
assert.equal((await api('GET', `/api/invoices/${fx.invoice.invoiceId}`)).paidAmount, 0);
// khách trả tiền trên payOS (giả) rồi bấm Kiểm tra
const order = Number(/pay\/(\d+)/.exec(co.checkoutUrl)[1]);
const mark = await fetch(`https://localhost:5443/_pay/${order}`);
assert.equal(mark.status, 200);
const rec = t.page.waitForResponse((x) => /payos\/reconcile/.test(x.url()));
await t.page.getByRole('button', { name: 'Kiểm tra đã thanh toán' }).click();
assert.equal((await rec).status(), 200);
await t.page.waitForTimeout(1500); // giao diện tự tải lại khi đã ghi nhận
const inv = await api('GET', `/api/invoices/${fx.invoice.invoiceId}`);
assert.equal(inv.paymentStatus, 'PAID');
assert.equal(inv.paidAmount, fx.invoice.totalAmount, 'hóa đơn đã trả đủ qua payOS');
// khách khác không đụng được giao dịch của người khác
const other = await fixtureInvoice(api, { label: 'PO2' });
const oTok = await tenantToken(s, other.tenant.tenantId);
const st = (await s.page.request.post(`${base}/api/me/payments/${gw.transactionId}/payos/checkout`, { headers: { Authorization: `Bearer ${oTok}` } })).status();
assert.equal(st, 404);
log('UC86 payOS toàn luồng OK với payOS giả (thanh toán thật chỉ cần thay khóa/URL thật)');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
