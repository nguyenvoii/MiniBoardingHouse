// E2E UC80 (+UC1/UC2 email): giao diện -> API -> SMTP thật (giao thức SMTP tới máy chủ nhận thư cục bộ smtp-sink) -> nội dung thư.
// Chạy: backend 18099 với app.mail.enabled=true spring.mail.host=127.0.0.1 spring.mail.port=2525 và `node smtp-sink.mjs 2525 ../../target/smtp-sink.json`;
//       BASE_URL=http://127.0.0.1:18099 node email-smtp-flow.mjs
import fs from 'node:fs';
import { start, assert, tag, log, fixtureInvoice, base } from './lib.mjs';

const SINK = process.env.SINK ?? 'C:/VoiVoi/projava1/MiniBoardingHouse/target/smtp-sink.json';
const mails = () => JSON.parse(fs.readFileSync(SINK, 'utf8'));
const decode = (raw) => raw.replace(/=\r?\n/g, '').replace(/=([0-9A-F]{2})/g, (_, h) => String.fromCharCode(parseInt(h, 16)));
const waitMail = async (to, n0, ms = 15000) => {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { const m = mails().filter((x) => x.to.includes(to)); if (m.length > n0) return m.at(-1); await new Promise((r) => setTimeout(r, 400)); }
  throw new Error(`không có thư gửi tới ${to}`);
};

const s = await start();
const { page, api, goto } = s;
assert.match(base, /18099/, 'chạy trên backend có bật SMTP');

// ---------- UC80: gửi hóa đơn cho khách qua email
const fx = await fixtureInvoice(api, { label: 'EM' });
const email = `e2e.em.${tag}@example.test`;
await api('PUT', `/api/chi/tenants/${fx.tenant.tenantId}`, { fullName: fx.tenant.fullName, phoneNumber: fx.tenant.phoneNumber, identityNumber: fx.tenant.identityNumber, email });
await goto('/invoices'); await page.waitForTimeout(900);
await page.getByPlaceholder('Mã hóa đơn hoặc mã phòng').fill(fx.invoice.invoiceCode);
await page.getByRole('row').filter({ hasText: fx.invoice.invoiceCode }).first().click();
const dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Gửi email' }).click();
await page.getByText('Đã gửi hóa đơn qua email').first().waitFor();
const m = await waitMail(email, 0);
const body = decode(m.raw);
assert.match(body, new RegExp(fx.invoice.invoiceCode), 'thư có mã hóa đơn');
assert.match(body, /#\/share\//, 'thư có liên kết xem hóa đơn (trang giao diện)');
assert.match(body, /Subject:/i);
assert.ok((await api('GET', `/api/invoices/${fx.invoice.invoiceId}/notifications`)).some((n) => n.channel === 'EMAIL' && n.status === 'SENT'), 'lịch sử gửi ghi nhận email đã gửi');
log('UC80 gửi hóa đơn qua email OK (SMTP cục bộ)');

// ---------- UC1/UC2: mã xác thực đăng ký gửi qua email thật, kích hoạt bằng mã trong thư
const regMail = `e2e.reg.smtp.${tag}@example.test`;
const reg = await s.page.request.post(`${base}/api/auth/register`, { data: { fullName: `E2E SMTP ${tag}`, email: regMail, password: `Reg#${tag}pw1` } });
assert.ok(reg.ok());
const om = await waitMail(regMail, 0);
const code = /l[aà] (\d{6})/.exec(Buffer.from(om.raw, 'binary').toString('utf8'))?.[1] ?? /(\d{6})\. M/.exec(om.raw)?.[1];
assert.ok(code, 'thư có mã 6 số');
const ver = await s.page.request.post(`${base}/api/auth/verify-email`, { data: { email: regMail, code } });
assert.ok(ver.ok(), 'kích hoạt bằng mã trong thư');
assert.ok((await s.page.request.post(`${base}/api/auth/login`, { data: { identifier: regMail, password: `Reg#${tag}pw1` } })).ok(), 'đăng nhập được sau khi kích hoạt');
log('UC1/UC2 xác thực email bằng mã nhận qua SMTP OK');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
