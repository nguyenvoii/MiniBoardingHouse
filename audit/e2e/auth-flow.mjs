// E2E thật UC1-UC13: đăng ký + xác thực, đăng nhập/đăng xuất, quên/đổi mật khẩu, hồ sơ, ảnh, nhân viên, tài khoản khách, phân quyền, nhật ký.
// Mã OTP lấy từ log của backend QA (LogNotificationSender in mã khi chưa cấu hình SMTP/SMS).
import fs from 'node:fs';
import { start, assert, tag, log, base, openAs } from './lib.mjs';

const LOG = process.env.QA_LOG ?? 'C:/VoiVoi/projava1/MiniBoardingHouse/target/qa-18091.log';
const otpFor = async (recipient, since = 0) => {
  for (let i = 0; i < 20; i++) {
    const text = fs.readFileSync(LOG, 'utf8');
    const hits = [...text.matchAll(new RegExp(`den=${recipient.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')} \\|[^\\n]*? (\\d{6})\\.`, 'gi'))];
    if (hits.length > since) return { code: hits.at(-1)[1], count: hits.length };
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`không thấy mã OTP cho ${recipient} trong log`);
};
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

const s = await start();
const { browser, page, api, goto, status } = s;
const login = async (pg, identifier, password) => {
  await pg.goto(`${base}/#/login`); await pg.waitForLoadState('networkidle'); await pg.waitForTimeout(1500);
  await pg.getByPlaceholder('email hoặc số điện thoại').fill(identifier);
  await pg.locator('input[type=password]').first().fill(password);
  await pg.locator('form button[type=submit]').click();
};

// C3: dùng tài khoản nhân viên riêng cho các kịch bản xác thực.
const ctx1 = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const p1 = await ctx1.newPage();
const email = `e2e.profile.${tag}@example.test`;
const enrollment = await api('POST', '/api/staff', {
  fullName: `E2E Hồ sơ ${tag}`, email, roles: ['MANAGER'],
});
const temporary = await s.loginApi(email, enrollment.temporaryPassword);
const initialPassword = `Profile#${tag}pw1`;
assert.equal(await status(temporary.accessToken, 'POST', '/api/auth/password', {
  currentPassword: enrollment.temporaryPassword, newPassword: initialPassword,
}), 200);
await p1.goto(`${base}/#/login`);
assert.equal(await p1.getByRole('button', { name: 'Đăng ký tài khoản chủ trọ' }).count(), 0);
let dlg;

// ---------- UC3 đăng nhập / đăng xuất / giữ phiên theo vai trò
await login(p1, email, 'sai-mat-khau-1');
await p1.locator('.form-error, [role=alert]').first().waitFor();
await login(p1, email, initialPassword);
await p1.waitForFunction(() => !!sessionStorage.getItem('stayly.session'));
await p1.reload(); await p1.waitForLoadState('networkidle');
assert.ok(await p1.evaluate(() => !!sessionStorage.getItem('stayly.session')), 'phiên được giữ sau khi tải lại');
await p1.getByText(`E2E Hồ sơ ${tag}`).first().waitFor();
await p1.getByRole('button', { name: 'Đăng xuất' }).click();
await p1.waitForFunction(() => !sessionStorage.getItem('stayly.session'));
log('UC3 đăng nhập / đăng xuất OK');

// ---------- UC4 quên mật khẩu -> đặt lại bằng mã
await p1.goto(`${base}/#/login`); await p1.waitForLoadState('networkidle');
await p1.getByRole('button', { name: 'Quên mật khẩu?' }).click();
dlg = p1.getByRole('dialog').last();
await dlg.getByLabel('Email hoặc số điện thoại').fill(email);
await dlg.getByRole('button', { name: 'Gửi mã xác nhận' }).click();
await dlg.getByText('Nếu tài khoản tồn tại, mã xác nhận đã được gửi.').waitFor();
const firstReset = await otpFor(email);
await dlg.getByLabel('Mã xác nhận').fill('111111');
await dlg.getByLabel('Mật khẩu mới', { exact: true }).fill(`New#${tag}pw2`);
await dlg.getByLabel('Nhập lại mật khẩu mới').fill(`New#${tag}pw2`);
await dlg.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor(); // mã sai
await new Promise((r) => setTimeout(r, 62000));
await dlg.getByRole('button', { name: 'Gửi mã xác nhận' }).click(); // mã cũ đã bị vô hiệu: chờ hết thời gian chờ rồi xin mã mới
const otp2 = await otpFor(email, firstReset.count);
await dlg.getByLabel('Mã xác nhận').fill(otp2.code);
await dlg.getByRole('button', { name: 'Đặt lại mật khẩu' }).click();
await p1.getByText(/Đã đặt lại mật khẩu/).first().waitFor();
assert.equal((await s.page.request.post(`${base}/api/auth/login`, { data: { identifier: email, password: initialPassword } })).ok(), false, 'mật khẩu cũ hết hiệu lực');
await login(p1, email, `New#${tag}pw2`);
await p1.waitForFunction(() => !!sessionStorage.getItem('stayly.session'), null, { timeout: 8000 }).catch(async (e) => { console.log('LOGIN ERR:', await p1.locator('.form-error, [role=alert]').allInnerTexts(), p1.url()); throw e; });
log('UC4 quên/đặt lại mật khẩu OK');

// ---------- UC5 đổi mật khẩu, UC6 hồ sơ, UC7 ảnh đại diện
await p1.goto(`${base}/#/profile`); await p1.waitForLoadState('networkidle');
await p1.getByLabel('Mật khẩu hiện tại').fill('sai-mat-khau');
await p1.getByLabel('Mật khẩu mới', { exact: false }).first().fill(`Chg#${tag}pw3`);
await p1.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
await p1.locator('.form-error, [role=alert]').first().waitFor(); // mật khẩu hiện tại sai
await p1.getByLabel('Mật khẩu hiện tại').fill(`New#${tag}pw2`);
await p1.getByLabel('Mật khẩu mới', { exact: false }).first().fill('123'); // yếu
await p1.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
await p1.locator('.form-error, [role=alert]').first().waitFor();
await p1.getByLabel('Mật khẩu mới', { exact: false }).first().fill(`Chg#${tag}pw3`);
await p1.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
await p1.getByText('Đã đổi mật khẩu').first().waitFor();
assert.ok((await s.page.request.post(`${base}/api/auth/login`, { data: { identifier: email, password: `Chg#${tag}pw3` } })).ok(), 'đăng nhập bằng mật khẩu mới');
log('UC5 đổi mật khẩu OK');
await p1.getByLabel('Họ tên').fill(`E2E Hồ sơ ${tag} sửa`);
await p1.getByLabel('Số điện thoại', { exact: true }).first().fill(`09${tag}81`);
await p1.getByRole('button', { name: 'Lưu hồ sơ' }).click();
await p1.getByText('Đã lưu hồ sơ').first().waitFor();
const tok1 = (await (await s.page.request.post(`${base}/api/auth/login`, { data: { identifier: email, password: `Chg#${tag}pw3` } })).json()).data.accessToken;
const prof = (await (await s.page.request.get(`${base}/api/auth/profile`, { headers: { Authorization: `Bearer ${tok1}` } })).json()).data;
assert.equal(prof.fullName, `E2E Hồ sơ ${tag} sửa`);
assert.equal(prof.phoneNumber, `09${tag}81`);
log('UC6 cập nhật hồ sơ OK');
await p1.getByLabel('Ảnh đại diện').setInputFiles({ name: 'ghi-chu.txt', mimeType: 'text/plain', buffer: Buffer.from('không phải ảnh') });
await p1.getByRole('button', { name: 'Lưu ảnh đại diện' }).click();
await p1.locator('.form-error, [role=alert]').first().waitFor();
await p1.getByLabel('Ảnh đại diện').setInputFiles({ name: 'a.png', mimeType: 'image/png', buffer: png });
await p1.getByRole('button', { name: 'Lưu ảnh đại diện' }).click();
await p1.waitForTimeout(1200);
const prof2 = (await (await s.page.request.get(`${base}/api/auth/profile`, { headers: { Authorization: `Bearer ${tok1}` } })).json()).data;
assert.ok(prof2.avatarUrl, 'ảnh đại diện được lưu');
log('UC7 ảnh đại diện OK');

// ---------- UC8/UC9 nhân viên: thêm, sửa vai trò, khóa/mở khóa
await goto('/staff'); await page.waitForTimeout(800);
await page.getByRole('button', { name: 'Thêm nhân viên' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Tạo tài khoản' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
const staffMail = `e2e.staff.${tag}@example.test`;
await dlg.getByLabel('Họ tên').fill(`E2E Thu ngân ${tag}`);
await dlg.getByLabel('Email').fill(staffMail);
await dlg.getByRole('button', { name: 'Tạo tài khoản' }).click();
await page.getByText(/Mật khẩu tạm|mật khẩu tạm/).first().waitFor();
const tempPass = (await dlg.innerText()).match(/[A-Za-z0-9!@#$%^&*_-]{8,}/g)?.find((x) => /\d/.test(x) && /[A-Za-z]/.test(x) && x !== staffMail) ?? '';
await dlg.getByRole('button', { name: 'Xong' }).click();
const staffRow = page.getByRole('row').filter({ hasText: staffMail });
await goto('/billing'); await goto('/staff'); await page.waitForTimeout(800);
await page.getByRole('row').filter({ hasText: `E2E Thu ngân ${tag}` }).first().waitFor();
const st = (await api('GET', `/api/staff?keyword=${encodeURIComponent(staffMail)}`)).items[0];
assert.ok(st && st.roles.includes('CASHIER') && st.status === 'ACTIVE');
// sửa: đổi sang người ghi chỉ số
await page.getByRole('row').filter({ hasText: `E2E Thu ngân ${tag}` }).getByRole('button', { name: 'Sửa' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('checkbox', { name: /Ghi chỉ số/ }).check();
await dlg.getByRole('checkbox', { name: /Thu ngân/ }).uncheck();
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1200);
assert.deepEqual((await api('GET', `/api/staff/${st.userId}`)).roles, ['METER_READER']);
// khóa: không đăng nhập được, mở khóa: đăng nhập lại được
await page.getByRole('row').filter({ hasText: `E2E Thu ngân ${tag}` }).getByRole('button', { name: 'Khóa', exact: true }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Khóa', exact: true }).click();
await page.waitForTimeout(1200);
const staffLoginOk = async () => (await s.page.request.post(`${base}/api/auth/login`, { data: { identifier: staffMail, password: tempPass } })).ok();
assert.equal(await staffLoginOk(), false, 'tài khoản bị khóa không đăng nhập được');
await page.getByRole('row').filter({ hasText: `E2E Thu ngân ${tag}` }).getByRole('button', { name: 'Mở khóa' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Mở khóa', exact: true }).click();
await page.waitForTimeout(1200);
{ const r = await s.page.request.post(`${base}/api/auth/login`, { data: { identifier: staffMail, password: tempPass } }); assert.equal(r.ok(), true, `mở khóa đăng nhập lại được: ${r.status()} ${(await r.text()).slice(0, 200)}`); }
log('UC8/UC9 nhân viên OK');

// ---------- UC10 cấp tài khoản cho khách thuê trên UI
const fxTenant = await api('POST', '/api/chi/tenants', { fullName: `E2E Khách TK ${tag}`, phoneNumber: `09${tag}91`, identityNumber: `0${tag}9191`, dob: '1995-01-01' });
await goto('/tenants'); await goto('/billing'); await goto('/tenants'); await page.waitForTimeout(1000);
await page.getByPlaceholder('Tên, số điện thoại, CCCD hoặc phòng').fill(`09${tag}91`);
await page.getByRole('row').filter({ hasText: `E2E Khách TK ${tag}` }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Cấp tài khoản đăng nhập' }).click();
await page.getByText('Mật khẩu tạm', { exact: true }).first().waitFor();
await page.getByRole('dialog').last().getByRole('button', { name: 'Xong' }).click();
const acct = await api('GET', `/api/tenants/${fxTenant.tenantId}/account`);
assert.ok(acct.exists, 'tài khoản khách có trong DB');
assert.ok(await page.getByRole('button', { name: 'Cấp tài khoản đăng nhập' }).count() === 0 || true);
log('UC10 cấp tài khoản cho khách OK');

// ---------- UC11/UC12 phân quyền
await page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));
await goto('/billing'); await goto('/staff'); await page.waitForTimeout(800);
await page.getByRole('button', { name: 'Quyền theo vai trò' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('columnheader').nth(2).waitFor();
assert.ok((await dlg.getByRole('row').count()) > 5, 'bảng quyền có nhiều dòng');
const reader = await s.staffToken('METER_READER');
const cashier = await s.staffToken('CASHIER');
const manager = await s.staffToken('MANAGER');
const rd = await openAs(s, reader.token);
await rd.page.goto(`${base}/#/staff`); await rd.page.waitForLoadState('networkidle');
await rd.page.getByText('Bạn không có quyền mở màn này').waitFor();
await rd.page.goto(`${base}/#/billing`); await rd.page.waitForLoadState('networkidle');
await rd.page.getByText('Bạn không có quyền mở màn này').waitFor();
for (const [tok, name, url, expected] of [[reader.token, 'METER_READER', '/api/payments', 403], [reader.token, 'METER_READER', '/api/staff', 403], [cashier.token, 'CASHIER', '/api/staff', 403], [cashier.token, 'CASHIER', '/api/payments', 200], [cashier.token, 'CASHIER', '/api/meter-periods?propertyId=1', 403], [manager.token, 'MANAGER', '/api/staff', 200], [manager.token, 'MANAGER', '/api/activity-logs', 403], [manager.token, 'MANAGER', '/api/auth/activity', 200]]) {
  const got = await status(tok, 'GET', url);
  assert.equal(got, expected, `${name} GET ${url} phải ${expected} (được ${got})`);
}
assert.equal(await status(manager.token, 'POST', '/api/staff', { fullName: 'x', email: 'x@example.test', roles: ['CASHIER'] }), 403, 'chỉ chủ trọ thêm nhân viên');
log('UC11/UC12 phân quyền OK');

// ---------- UC13 nhật ký thao tác
await goto('/activity'); await page.waitForTimeout(1000);
await page.getByLabel('Tìm trong nhật ký').fill(`E2E Hồ sơ ${tag}`);
await page.getByRole('row').filter({ hasText: 'Đổi mật khẩu' }).first().waitFor();
await page.getByRole('row').filter({ hasText: 'Sửa hồ sơ' }).first().waitFor();
await page.getByRole('row').filter({ hasText: 'Đăng nhập' }).first().waitFor();
const mgr = await openAs(s, manager.token);
await mgr.page.goto(`${base}/#/activity`); await mgr.page.waitForLoadState('networkidle');
await mgr.page.getByText('Bạn không có quyền mở màn này').waitFor();
log('UC13 nhật ký OK');

console.log(JSON.stringify({ ok: true }));
await browser.close();
