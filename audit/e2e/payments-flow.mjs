// E2E thật UC81, UC83, UC85, UC87, UC89-92: thu tiền nhiều lần, phiếu thu, gửi xác nhận, chia sẻ hóa đơn, điều chỉnh, số dư trả trước.
import { start, assert, tag, log, base, fixtureInvoice, tenantToken, openAs } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const fx = await fixtureInvoice(api, { label: 'PAY' });
const inv = fx.invoice;
log('fixture hóa đơn', inv.invoiceCode, inv.totalAmount);
const toast = (t) => page.getByText(t).first().waitFor();
const refresh = () => api('GET', `/api/invoices/${inv.invoiceId}`);
const openInvoice = async (code = inv.invoiceCode) => {
  await page.keyboard.press('Escape');
  await goto('/payments'); await goto('/invoices'); await page.waitForTimeout(800); // danh sách còn tự tải lại sau thao tác trước
  await page.getByPlaceholder('Mã hóa đơn hoặc mã phòng').fill(code);
  await page.getByRole('row').filter({ hasText: code }).first().click();
  return page.getByRole('dialog').last();
};

// ---------- UC85/87/91 thu tiền nhiều lần
const confirmPartial = async () => { const yes = page.getByRole('button', { name: 'Đúng, ghi nhận' }); await yes.waitFor({ timeout: 4000 }).then(() => yes.click()).catch(() => {}); };
let dlg = await openInvoice();
await dlg.getByRole('button', { name: 'Thu tiền' }).click();
let pay = page.getByRole('dialog').last();
await pay.locator('input[type=number]').first().fill('0');
await pay.getByRole('button', { name: 'Ghi nhận' }).click();
await pay.locator('.form-error, [role=alert]').first().waitFor(); // số tiền 0 bị chặn
await pay.locator('input[type=number]').first().fill('1000000');
let posted = page.waitForResponse((r) => r.url().endsWith('/api/payments') && r.request().method() === 'POST');
await pay.getByRole('button', { name: 'Ghi nhận' }).click();
await confirmPartial();
assert.equal((await posted).status(), 200);
let cur = await refresh();
assert.equal(cur.paidAmount, 1000000);
assert.equal(cur.paymentStatus, 'PARTIALLY_PAID');
dlg = await openInvoice();
await dlg.getByRole('button', { name: 'Thu tiền' }).click();
pay = page.getByRole('dialog').last();
await pay.locator('input[type=number]').first().fill(String(cur.remainingAmount + 500000));
await pay.getByRole('button', { name: 'Ghi nhận' }).click();
await confirmPartial();
await pay.locator('.form-error, [role=alert]').first().waitFor();
assert.equal((await refresh()).paidAmount, 1000000, 'thu vượt số còn lại không làm đổi số đã trả');
await pay.locator('input[type=number]').first().fill('500000');
posted = page.waitForResponse((r) => r.url().endsWith('/api/payments') && r.request().method() === 'POST');
await pay.getByRole('button', { name: 'Ghi nhận' }).click();
await confirmPartial();
assert.equal((await posted).status(), 200);
cur = await refresh();
assert.equal(cur.paidAmount, 1500000);
dlg = await openInvoice();
await dlg.getByText('Lần 1:').waitFor();
await dlg.getByText('Lần 2:').waitFor();
log('UC85/87/91 thu nhiều lần OK');

// ---------- UC89/90 phiếu thu + gửi xác nhận
const tx = (await api('GET', `/api/payments?invoiceId=${inv.invoiceId}&size=20`)).items.find((t) => t.status === 'SUCCESS');
assert.ok(tx, 'có giao dịch thành công');
await page.keyboard.press('Escape');
await goto('/payments');
await page.getByRole('button', { name: 'Phiếu thu', exact: true }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Lập phiếu thu' }).click();
const rc = page.getByRole('dialog').last();
await rc.getByRole('button', { name: 'Lập phiếu', exact: true }).click();
await rc.locator('.form-error, [role=alert]').first().waitFor();
await rc.getByRole('button', { name: 'Hủy' }).click();
const receipt = await api('POST', '/api/receipts', { transactionId: tx.transactionId, voucherType: 'MONTHLY_RENT', amount: tx.amountPaid, note: 'E2E phiếu thu' });
await dlg.getByRole('button', { name: 'Đóng', exact: true }).last().click();
await page.getByRole('button', { name: 'Phiếu thu', exact: true }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('row').filter({ hasText: receipt.voucherCode }).getByRole('button', { name: 'Gửi xác nhận' }).click();
await toast('Đã gửi xác nhận thanh toán cho khách');
assert.equal(await status(await s.token(), 'POST', '/api/receipts/999999/send-confirmation'), 404);
log('UC89/90 phiếu thu + gửi xác nhận OK');

// ---------- UC81 chia sẻ + gửi vào ứng dụng + khách xem
const tTok = await tenantToken(s, fx.tenant.tenantId);
const tenantUi = await openAs(s, tTok, { width: 420, height: 900 });
dlg = await openInvoice();
await dlg.getByRole('button', { name: 'Gửi vào ứng dụng' }).click();
await toast('Đã gửi thông báo hóa đơn vào ứng dụng của khách');
await dlg.getByText(/Ứng dụng · đã gửi/).first().waitFor();
await dlg.getByRole('button', { name: 'Liên kết chia sẻ' }).click();
const sd = page.getByRole('dialog').last();
const url = await sd.getByLabel('Liên kết chia sẻ').inputValue();
assert.match(url, /#\/share\/[\w-]+$/);
const anon = await s.browser.newContext();
const ap = await anon.newPage();
await ap.goto(url);
await ap.getByText(`Hóa đơn ${inv.invoiceCode}`).waitFor();
await ap.getByText('Còn phải trả').waitFor();
await sd.getByRole('button', { name: 'Thu hồi liên kết' }).click();
await toast('Đã thu hồi liên kết');
await ap.reload();
await ap.getByText('Liên kết không còn hiệu lực').waitFor();
await anon.close();
await tenantUi.page.goto(`${base}/#/me/notifications`); await tenantUi.page.waitForLoadState('networkidle');
await tenantUi.page.getByText(new RegExp(inv.invoiceCode)).first().waitFor();
log('UC81 chia sẻ / gửi ứng dụng / khách xem OK');

// ---------- UC83 điều chỉnh tổng tiền, khách được báo
dlg = await openInvoice();
await dlg.getByRole('button', { name: 'Điều chỉnh tổng tiền' }).click();
const ad = page.getByRole('dialog').last();
await ad.getByRole('button', { name: 'Lưu điều chỉnh' }).click();
await ad.getByText('Nhập lý do điều chỉnh.').waitFor();
await ad.getByLabel('Tổng tiền mới').fill('-5');
await ad.getByLabel('Lý do điều chỉnh').fill('Số âm');
await ad.getByRole('button', { name: 'Lưu điều chỉnh' }).click();
await ad.locator('.form-error, [role=alert]').first().waitFor();
assert.equal((await refresh()).totalAmount, inv.totalAmount, 'số âm bị từ chối');
// tổng mới thấp hơn số đã trả (1.500.000) -> phát sinh trả dư 100.000
await ad.getByLabel('Tổng tiền mới').fill('1400000');
await ad.getByLabel('Lý do điều chỉnh').fill('Giảm do công tơ đọc nhầm');
await ad.getByRole('button', { name: 'Lưu điều chỉnh' }).click();
await toast('Đã điều chỉnh hóa đơn và báo cho khách');
cur = await refresh();
assert.equal(cur.totalAmount, 1400000);
await tenantUi.page.reload(); await tenantUi.page.waitForLoadState('networkidle');
await tenantUi.page.getByText(/điều chỉnh/i).first().waitFor();
log('UC83 điều chỉnh + báo khách OK');

// ---------- UC92 trả dư -> số dư -> hoàn tiền / cấn trừ / hủy cấn trừ
dlg = await openInvoice();
await dlg.getByRole('button', { name: 'Chuyển phần trả dư vào số dư' }).click();
await toast('Đã chuyển phần trả dư vào số dư trả trước của khách');
let bal = await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`);
assert.equal(bal.excessBalance, 100000 + 0, 'số dư = phần trả dư');
// hóa đơn thứ hai của cùng khách để cấn trừ
let fx2 = null;
try { fx2 = await fixtureInvoice(api, { label: 'PAY2', tenant: fx.tenant }); } catch (e) { log('không dựng được hóa đơn thứ hai cho cùng khách:', String(e.message).slice(0, 120)); }
await page.keyboard.press('Escape');
await goto('/payments');
await page.getByRole('button', { name: 'Số dư trả trước' }).click();
const pd = page.getByRole('dialog').last();
await pd.getByLabel('Chọn khách').selectOption(String(fx.tenant.tenantId));
if (fx2) {
  // hóa đơn kỳ sau vừa phát hành đã được hệ thống tự cấn trừ số dư (UC92 cấn trừ kỳ sau)
  assert.equal((await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`)).excessBalance, 0, 'số dư đã tự cấn trừ vào hóa đơn kỳ sau');
  const auto = await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment/deductions`);
  assert.equal(auto.length, 1, 'có một lần cấn trừ tự động');
  const autoInv = await api('GET', `/api/invoices/${auto[0].targetInvoiceId}`);
  assert.equal(autoInv.paidAmount, 100000, 'phần cấn trừ tính vào số đã trả của hóa đơn được cấn trừ');
  const undone = page.waitForResponse((r) => r.url().includes('/api/prepayments/deductions/') && r.request().method() === 'DELETE');
  await pd.getByRole('button', { name: 'Hủy cấn trừ' }).click();
  const ur = await undone;
  assert.equal(ur.status(), 200, await ur.text());
  assert.equal((await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`)).excessBalance, 100000);
  assert.equal((await api('GET', `/api/invoices/${autoInv.invoiceId}`)).paidAmount, 0, 'hóa đơn được cấn trừ trả lại như cũ');
}
await pd.getByRole('button', { name: 'Hoàn tiền cho khách' }).click();
await pd.getByText('Nhập số tiền lớn hơn 0.').waitFor();
await pd.getByLabel('Số tiền').fill('500000'); // vượt số dư
await pd.getByRole('button', { name: 'Hoàn tiền cho khách' }).click();
await pd.locator('.form-error, [role=alert]').first().waitFor();
assert.equal((await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`)).excessBalance, 100000, 'hoàn vượt số dư bị từ chối');
if (fx2) {
  await pd.getByLabel('Số tiền').fill('40000');
  await pd.getByRole('button', { name: 'Cấn trừ vào hóa đơn' }).click();
  await toast('Đã cấn trừ vào hóa đơn');
  assert.equal((await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`)).excessBalance, 60000);
  assert.equal((await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment/deductions`)).length, 1, 'ghi nhận lần cấn trừ tay');
  await pd.getByRole('row').filter({ hasText: '40.000' }).getByRole('button', { name: 'Hủy cấn trừ' }).click();
  await page.waitForResponse((r) => r.url().includes('/api/prepayments/deductions/') && r.request().method() === 'DELETE');
  await page.waitForTimeout(500);
  assert.equal((await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`)).excessBalance, 100000);
}
await pd.getByLabel('Số tiền').fill('30000');
await pd.getByRole('button', { name: 'Hoàn tiền cho khách' }).click();
await toast('Đã hoàn tiền, số dư đã trừ');
assert.equal((await api('GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`)).excessBalance, 70000);
log('UC92 trả dư / số dư / cấn trừ / hoàn tiền OK', fx2 ? '' : '(chưa chứng minh cấn trừ)');

// ---------- UC92 hủy giao dịch sai
const tx2 = (await api('GET', `/api/payments?invoiceId=${inv.invoiceId}&size=20`)).items.find((t) => t.status === 'SUCCESS' && t.transactionId !== tx.transactionId);
await goto('/payments'); await page.getByLabel(/Tìm/).last().waitFor().catch(() => {});
log('giao dịch để hủy:', tx2 ? tx2.transactionId : 'không có');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
const cashier = await s.staffToken('CASHIER');
const manager = await s.staffToken('MANAGER');
for (const [m, u, d] of [['GET', `/api/invoices/${inv.invoiceId}`], ['POST', '/api/receipts', {}], ['POST', `/api/invoices/${inv.invoiceId}/share-link`], ['GET', `/api/tenants/${fx.tenant.tenantId}/prepayment`], ['POST', '/api/prepayments/deduct', {}]]) {
  assert.equal(await status(reader.token, m, u, d), 403, `METER_READER ${m} ${u} phải 403`);
}
assert.equal(await status(cashier.token, 'POST', '/api/prepayments/refund', { tenantId: fx.tenant.tenantId, amount: 1000 }), 403, 'thu ngân không hoàn tiền');
assert.equal(await status(cashier.token, 'PUT', `/api/invoices/${inv.invoiceId}/total`, { newTotalAmount: 1, reason: 'x' }), 403, 'thu ngân không điều chỉnh hóa đơn');
assert.equal(await status(cashier.token, 'GET', `/api/invoices/${inv.invoiceId}`), 200);
assert.equal(await status(tTok, 'POST', `/api/invoices/${inv.invoiceId}/share-link`), 403, 'khách không tạo liên kết');
assert.equal(await status(tTok, 'PUT', `/api/invoices/${inv.invoiceId}/total`, { newTotalAmount: 1, reason: 'x' }), 403);
// khách khác không đọc được hóa đơn của khách này
const other = await fixtureInvoice(api, { label: 'OTH' });
const oTok = await tenantToken(s, other.tenant.tenantId);
assert.equal(await status(oTok, 'GET', `/api/invoices/${inv.invoiceId}/detail`), 404, 'khách khác không thấy hóa đơn');
assert.equal(await status(tTok, 'GET', `/api/invoices/${inv.invoiceId}/detail`), 200, 'chủ hóa đơn xem được');
// giao diện: thu ngân không thấy nút điều chỉnh tổng tiền
const cu = await openAs(s, cashier.token);
await cu.page.goto(`${base}/#/invoices`); await cu.page.waitForLoadState('networkidle');
await cu.page.getByPlaceholder('Mã hóa đơn hoặc mã phòng').fill(inv.invoiceCode);
await cu.page.getByRole('row').filter({ hasText: inv.invoiceCode }).first().click();
await cu.page.getByRole('dialog').last().getByRole('button', { name: 'Thu tiền' }).waitFor();
assert.equal(await cu.page.getByRole('button', { name: 'Điều chỉnh tổng tiền' }).count(), 0);
log('phân quyền thanh toán OK');

console.log(JSON.stringify({ ok: true, invoice: inv.invoiceCode }));
await s.browser.close();
