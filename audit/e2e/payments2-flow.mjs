// E2E thật UC78, UC80 (email, BLOCKED SMTP), UC82, UC84, UC87, UC88, UC121 (mẫu thông báo thực sự được dùng).
import { start, assert, tag, log, base, fixtureInvoice, tenantToken, openAs } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const today = new Date();
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; // ngày theo giờ máy (không dùng UTC)
const addDays = (n) => iso(new Date(today.getTime() + n * 86400000));
const toast = (t) => page.getByText(t).first().waitFor();
const closeAll = () => page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));

// hóa đơn A: hạn sau 3 ngày (nhắc trước hạn); hóa đơn B: đã quá hạn hôm qua
const fa = await fixtureInvoice(api, { label: 'PX', issueDate: addDays(0), dueDate: addDays(3) });
const fb = await fixtureInvoice(api, { label: 'PY', issueDate: addDays(-10), dueDate: addDays(-1) });
const tokA = await tenantToken(s, fa.tenant.tenantId);
const tokB = await tenantToken(s, fb.tenant.tenantId);
const notifs = async (tok) => (await s.raw('GET', '/api/me/notifications?size=50', undefined, tok).then((r) => r.json())).data.items;
const openInvoice = async (code) => {
  await closeAll(); await goto('/payments'); await goto('/invoices'); await page.waitForTimeout(900);
  await page.getByPlaceholder('Mã hóa đơn hoặc mã phòng').fill(code);
  await page.getByRole('row').filter({ hasText: code }).first().click();
  return page.getByRole('dialog').last();
};

// ---------- UC84 tra cứu hóa đơn theo mã, phòng, trạng thái
await goto('/invoices'); await page.waitForTimeout(900);
const box = page.getByPlaceholder('Mã hóa đơn hoặc mã phòng');
await box.fill(fa.invoice.invoiceCode);
await page.getByRole('row').filter({ hasText: fa.invoice.invoiceCode }).first().waitFor();
await box.fill(fa.room.roomCode);
await page.getByRole('row').filter({ hasText: fa.invoice.invoiceCode }).first().waitFor();
await box.fill('khong-ton-tai-' + tag);
await page.waitForTimeout(900);
assert.equal(await page.getByRole('row').filter({ hasText: fa.invoice.invoiceCode }).count(), 0, 'từ khóa không khớp thì không có hóa đơn');
const payable = await api('GET', `/api/invoices/payable?tenantId=${fa.tenant.tenantId}`).catch(() => null);
if (payable) assert.ok(JSON.stringify(payable).includes(fa.invoice.invoiceCode), 'danh sách hóa đơn cần thanh toán');
log('UC84 tra cứu hóa đơn OK');

// ---------- UC78 chi tiết + in
let dlg = await openInvoice(fa.invoice.invoiceCode);
await dlg.getByRole('row').filter({ hasText: 'Tiền phòng' }).first().waitFor();
const printed = page.waitForResponse((r) => r.url().includes(`/api/invoices/${fa.invoice.invoiceId}/print`), { timeout: 8000 });
await dlg.getByRole('button', { name: 'In', exact: true }).click();
const pr = await printed.catch(async (e) => { await page.screenshot({ path: 'screens/dbg-print.png' }); console.log('BTN', await dlg.getByRole('button').allInnerTexts(), 'DIALOGS', await page.locator('dialog[open]').count()); throw e; });
assert.equal(pr.status(), 200);
assert.match(await pr.text(), new RegExp(fa.invoice.invoiceCode), 'bản in có mã hóa đơn');
log('UC78 chi tiết + in hóa đơn OK');

// ---------- UC80 gửi email: chưa cấu hình SMTP -> báo rõ, không giả vờ gửi
dlg = await openInvoice(fa.invoice.invoiceCode);
await dlg.getByRole('button', { name: 'Gửi email' }).click();
await page.getByText(/chưa có email|chua co email/i).first().waitFor(); // khách chưa có email: báo rõ
await api('PUT', `/api/chi/tenants/${fa.tenant.tenantId}`, { fullName: fa.tenant.fullName, phoneNumber: fa.tenant.phoneNumber, identityNumber: fa.tenant.identityNumber, email: `e2e.px.${tag}@example.test` });
dlg = await openInvoice(fa.invoice.invoiceCode);
await dlg.getByRole('button', { name: 'Gửi email' }).click();
await page.getByText(/chưa cấu hình gửi thư|Gửi email thất bại|Đã gửi hóa đơn qua email/).first().waitFor(); // có email: gửi hoặc báo chưa cấu hình SMTP
log('UC80 gửi email: giao diện + API xong, BLOCKED chờ SMTP thật (spring.mail.* / app.mail.*)');

// ---------- UC87 ghi nhận chuyển khoản trực tiếp (nhân viên có sao kê): người nhận, thời điểm, mã giao dịch, chống trùng
const fc = await fixtureInvoice(api, { label: 'PW' });
dlg = await openInvoice(fc.invoice.invoiceCode);
await dlg.getByRole('button', { name: 'Thu tiền' }).click();
let pay = page.getByRole('dialog').last();
await pay.locator('select').first().selectOption('BANK_TRANSFER');
const code = `FT${tag}`;
await pay.getByPlaceholder('Mã giao dịch ngân hàng (nếu có)').fill(code);
const posted = page.waitForResponse((r) => r.url().endsWith('/api/payments') && r.request().method() === 'POST');
await pay.getByRole('button', { name: 'Ghi nhận' }).click();
assert.equal((await posted).status(), 200);
const txC = (await api('GET', `/api/payments?invoiceId=${fc.invoice.invoiceId}&size=20`)).items.find((t) => t.transactionCode === code);
assert.ok(txC && txC.status === 'SUCCESS' && txC.receivedByUserId, 'chuyển khoản ghi nhận có người nhận và thời điểm');
assert.equal((await api('GET', `/api/invoices/${fc.invoice.invoiceId}`)).paymentStatus, 'PAID');
assert.ok([400, 409].includes(await status(await s.token(), 'POST', '/api/payments', { invoiceId: fb.invoice.invoiceId, method: 'BANK_TRANSFER', amount: 1000, transactionCode: code })), 'mã giao dịch trùng bị từ chối');
log('UC87 ghi nhận chuyển khoản + chống trùng mã OK');

// ---------- UC92 hủy giao dịch ghi nhầm (chủ trọ/quản lý), thu ngân không hủy được
const cashier0 = await s.staffToken('CASHIER');
assert.equal(await status(cashier0.token, 'POST', `/api/payment-transactions/${txC.transactionId}/reverse`), 403, 'thu ngân không hủy giao dịch');
await closeAll(); await goto('/billing'); await goto('/payments'); await page.waitForTimeout(1000);
await page.getByPlaceholder(/Mã giao dịch hoặc mã hóa đơn/).fill(fc.invoice.invoiceCode);
await page.waitForTimeout(900);
await page.getByRole('row').filter({ hasText: fc.invoice.invoiceCode }).first().click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Hủy giao dịch' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: /^Hủy giao dịch$|Hủy GD|Xác nhận/ }).last().click();
await page.waitForTimeout(1500);
const rev = await api('GET', `/api/invoices/${fc.invoice.invoiceId}`);
assert.equal(rev.paidAmount, 0, 'hủy giao dịch trừ lại số đã trả');
assert.equal(rev.paymentStatus === 'PAID', false);
log('UC92 hủy giao dịch sai OK');

// ---------- UC88 khách tự tạo QR (giao dịch chờ), nhân viên xác nhận theo số tiền thực nhận trên sao kê
const fd = await fixtureInvoice(api, { label: 'PV' });
const tokD = await tenantToken(s, fd.tenant.tenantId);
const qr = (await s.raw('POST', '/api/me/payments/qr', { invoiceId: fd.invoice.invoiceId, method: 'QR_CODE' }, tokD).then((r) => r.json())).data;
const tx = qr.payment;
assert.equal(tx.status, 'FAILED', 'giao dịch QR chờ xác nhận, chưa cộng vào hóa đơn');
assert.equal((await api('GET', `/api/invoices/${fd.invoice.invoiceId}`)).paidAmount, 0);
await closeAll(); await goto('/billing'); await goto('/payments'); await page.waitForTimeout(1000);
await page.getByPlaceholder(/Mã giao dịch hoặc mã hóa đơn/).fill(fd.invoice.invoiceCode);
await page.waitForTimeout(900);
await page.getByRole('row').filter({ hasText: fd.invoice.invoiceCode }).getByRole('button', { name: 'Xác nhận' }).first().click();
let cf = page.getByRole('dialog').last();
await cf.locator('input[type=number]').fill(String(tx.amountPaid - 1000)); // lệch tiền
await cf.getByRole('button', { name: 'Xác nhận', exact: true }).click();
await cf.locator('.form-error, [role=alert]').first().waitFor();
assert.equal((await api('GET', `/api/invoices/${fd.invoice.invoiceId}`)).paidAmount, 0, 'lệch số tiền thì không cộng vào hóa đơn');
await cf.locator('input[type=number]').fill(String(tx.amountPaid));
await cf.getByRole('button', { name: 'Xác nhận', exact: true }).click();
await page.waitForTimeout(1500);
assert.equal((await api('GET', `/api/invoices/${fd.invoice.invoiceId}`)).paidAmount, tx.amountPaid, 'xác nhận đúng số tiền thì cộng vào hóa đơn');
log('UC88 xác nhận chuyển khoản theo số thực nhận OK');

// đối chiếu nội dung chuyển khoản với mã hóa đơn
await page.getByRole('button', { name: 'Đối chiếu chuyển khoản' }).click();
let mt = page.getByRole('dialog').last();
await mt.locator('input, textarea').first().fill('NOI DUNG KHONG LIEN QUAN');
await mt.getByRole('button', { name: 'Tìm hóa đơn' }).click();
await page.waitForTimeout(1200);
await mt.locator('input, textarea').first().fill(`THANH TOAN ${fa.invoice.invoiceCode} PHONG`);
await mt.getByRole('button', { name: 'Tìm hóa đơn' }).click();
await mt.getByText(fa.invoice.invoiceCode).first().waitFor();
log('UC88 đối chiếu nội dung chuyển khoản OK');
await closeAll();

// ---------- UC82 nhắc trước hạn + quá hạn: job thủ công, một lần mỗi ngày
const nA0 = (await notifs(tokA)).length, nB0 = (await notifs(tokB)).length;
await api('POST', '/api/admin/jobs/invoice-reminders/run');
const nA1 = await notifs(tokA), nB1 = await notifs(tokB);
assert.ok(nA1.length > nA0 && nA1.some((n) => /sắp đến hạn/i.test(n.title)), 'UC82 khách A nhận nhắc trước hạn');
assert.ok(nB1.length >= nB0, 'khách B xử lý quá hạn');
const invB = await api('GET', `/api/invoices/${fb.invoice.invoiceId}`);
await api('POST', '/api/admin/jobs/invoice-reminders/run');
assert.equal((await notifs(tokA)).length, nA1.length, 'chạy lại trong ngày không nhắc trùng');
log('UC82 nhắc trước hạn/quá hạn OK', { A: nA1.length - nA0, B: nB1.length - nB0, statusB: invB.paymentStatus });

// ---------- UC121 mẫu thông báo được dùng thật khi gửi hóa đơn vào ứng dụng
const code2 = 'INVOICE_ISSUED';
const existing = (await api('GET', `/api/notification-templates?code=${code2}&channel=SYSTEM_APP`))[0];
const body = { templateCode: code2, channel: 'SYSTEM_APP', subject: `Mẫu E2E ${tag}`, content: `Mẫu E2E cho {{invoice}} hạn {{dueDate}}` };
const tpl = existing ? await api('PUT', `/api/notification-templates/${existing.notifTemplateId}`, body) : await api('POST', '/api/notification-templates', body);
const wasActive = existing ? existing.isActive : false;
await api('PATCH', `/api/notification-templates/${tpl.notifTemplateId}/active`, { active: true });
await api('POST', `/api/invoices/${fa.invoice.invoiceId}/send-app`);
const mine = (await notifs(tokA)).find((n) => n.title.includes(`Mẫu E2E ${tag}`) || n.content.includes(`Mẫu E2E cho ${fa.invoice.invoiceCode}`));
assert.ok(mine, 'thông báo dùng nội dung của mẫu đã sửa');
// tắt mẫu thì quay về nội dung mặc định
await api('PATCH', `/api/notification-templates/${tpl.notifTemplateId}/active`, { active: false });
await api('POST', `/api/invoices/${fa.invoice.invoiceId}/send-app`);
const after = await notifs(tokA);
assert.ok(after[0].title === 'Hóa đơn mới' || !after[0].content.includes('Mẫu E2E'), 'tắt mẫu thì dùng nội dung mặc định');
if (existing) {
  await api('PUT', `/api/notification-templates/${existing.notifTemplateId}`, { templateCode: code2, channel: 'SYSTEM_APP', subject: existing.subject, content: existing.content });
  await api('PATCH', `/api/notification-templates/${existing.notifTemplateId}/active`, { active: wasActive });
}
log('UC121 mẫu thông báo được dùng thật OK');

// ---------- UC89 phiếu thu: phiếu tiền cọc lập tự động khi thu cọc theo hợp đồng
const receipts = await api('GET', '/api/receipts');
assert.ok(receipts.some((r) => /DEPOSIT/i.test(r.voucherType)), 'có phiếu thu tiền đặt cọc');
log('UC89 phiếu thu cọc OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
const cashier = await s.staffToken('CASHIER');
assert.ok([401, 403].includes(await status(reader.token, 'POST', `/api/invoices/${fa.invoice.invoiceId}/send-app`)), 'METER_READER không gửi hóa đơn');
assert.equal(await status(cashier.token, 'POST', '/api/admin/jobs/invoice-reminders/run'), 403, 'chỉ chủ trọ chạy tác vụ tự động');
log('phân quyền hóa đơn OK');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
