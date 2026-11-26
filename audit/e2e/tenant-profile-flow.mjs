// E2E thật UC30-UC32, UC35: hồ sơ khách thuê, cư trú, CCCD hai mặt, xác minh, tra cứu, phân quyền.
import { start, assert, tag, log, base } from './lib.mjs';

const { browser, page, api, goto, staffToken, status, raw, token } = await start();
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
const name = `E2E Hồ sơ ${tag}`;
const phone = `09${tag}00`;
const cccd = `0${tag}5555`;

// --- UC30: thêm hồ sơ đầy đủ trên UI, có lỗi ngày cư trú sai
await goto('/tenants');
await page.getByRole('button', { name: 'Thêm khách hàng' }).first().click();
let dlg = page.getByRole('dialog').last();
await dlg.getByPlaceholder('Họ và tên').fill(name);
await dlg.getByPlaceholder('09xx xxx xxx').fill(phone);
await dlg.getByPlaceholder('Số CCCD').fill(cccd);
await dlg.getByLabel('Ngày sinh').fill('1994-03-09');
await dlg.getByLabel('Địa chỉ thường trú').fill('12 Lê Lợi, Quận 1');
await dlg.getByLabel('Người liên hệ khẩn cấp').fill('Nguyễn Văn Mẹ');
await dlg.getByLabel('SĐT liên hệ khẩn cấp').fill('0912345678');
await dlg.getByLabel('Bắt đầu cư trú').fill('2029-06-10');
await dlg.getByLabel('Kết thúc cư trú').fill('2029-05-01');
await dlg.getByRole('button', { name: 'Thêm khách hàng' }).click();
await dlg.getByText('Ngày kết thúc cư trú không được trước ngày bắt đầu.').waitFor();
await dlg.getByLabel('Kết thúc cư trú').fill('2030-06-09');
await dlg.getByRole('button', { name: 'Thêm khách hàng' }).click();
await page.getByText(`Đã thêm khách ${name}`).waitFor();
const all = await api('GET', '/api/chi/tenants');
const t = all.find((x) => x.identityNumber === cccd);
assert.ok(t, 'khách có trong DB');
assert.deepEqual([t.dob, t.permanentAddress, t.emergencyContactName, t.emergencyContactPhone, t.residenceStartDate, t.residenceEndDate],
  ['1994-03-09', '12 Lê Lợi, Quận 1', 'Nguyễn Văn Mẹ', '0912345678', '2029-06-10', '2030-06-09']);
// trùng CCCD/SĐT bị từ chối trên UI
await page.getByRole('button', { name: 'Thêm khách hàng' }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByPlaceholder('Họ và tên').fill('Trùng');
await dlg.getByPlaceholder('09xx xxx xxx').fill(`09${tag}99`);
await dlg.getByPlaceholder('Số CCCD').fill(cccd);
await dlg.getByRole('button', { name: 'Thêm khách hàng' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByRole('button', { name: 'Hủy' }).click();
log('UC30 thêm hồ sơ + lỗi OK');

// --- UC35: tra cứu theo SĐT và CCCD trên UI
await goto('/tenants'); await page.waitForLoadState('networkidle'); await page.waitForTimeout(800); // danh sách còn tự tải lại sau khi thêm khách
const search = page.getByLabel('Tìm khách hàng');
for (const q of [phone, cccd]) {
  await search.fill(q);
  await page.getByRole('row').filter({ hasText: name }).first().waitFor();
  assert.equal(await page.getByRole('row').filter({ hasText: name }).count(), 1);
}
await search.fill('khong-ton-tai-' + tag);
await page.waitForTimeout(600);
assert.equal(await page.getByRole('row').filter({ hasText: name }).count(), 0, 'không khớp thì không hiện');
log('UC35 tra cứu SĐT/CCCD OK');

// --- UC31: sửa hồ sơ + ngày cư trú trên UI
await search.fill(phone);
await page.getByRole('row').filter({ hasText: name }).first().click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Sửa hồ sơ' }).click();
dlg = page.getByRole('dialog').last();
await page.waitForFunction(() => [...document.querySelectorAll('dialog input')].some((i) => i.value === '12 Lê Lợi, Quận 1')); // đã nạp hồ sơ đầy đủ
await dlg.getByLabel('Địa chỉ thường trú').fill('99 Nguyễn Huệ, Quận 1');
await dlg.getByLabel('Kết thúc cư trú').fill('2029-06-01');
await dlg.getByRole('button', { name: 'Lưu hồ sơ' }).click();
await dlg.getByText('Ngày kết thúc cư trú không được trước ngày bắt đầu.').waitFor();
await dlg.getByLabel('Kết thúc cư trú').fill('2031-06-09');
await dlg.getByRole('button', { name: 'Lưu hồ sơ' }).click();
await page.getByText('Đã lưu hồ sơ khách').waitFor();
const t2 = await api('GET', `/api/chi/tenants/${t.tenantId}`);
assert.deepEqual([t2.permanentAddress, t2.residenceEndDate, t2.emergencyContactName], ['99 Nguyễn Huệ, Quận 1', '2031-06-09', 'Nguyễn Văn Mẹ'], 'sửa lưu đúng, trường không đổi được giữ');
log('UC31 sửa hồ sơ/cư trú OK');

// --- UC32: CCCD hai mặt + xác minh
await goto('/tenants'); await goto('/tenants');
await page.getByLabel('Tìm khách hàng').fill(phone);
await page.getByRole('row').filter({ hasText: name }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Xác minh hồ sơ' }).waitFor();
await dlg.getByRole('button', { name: 'Tải ảnh CCCD' }).click();
await dlg.getByText('Chọn đủ ảnh mặt trước và mặt sau.').waitFor();
await dlg.getByLabel('Ảnh CCCD mặt trước').setInputFiles({ name: 'front.png', mimeType: 'image/png', buffer: png });
await dlg.getByLabel('Ảnh CCCD mặt sau').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('không phải ảnh') });
await dlg.getByRole('button', { name: 'Tải ảnh CCCD' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
assert.equal((await api('GET', `/api/chi/tenants/${t.tenantId}`)).frontImageUrl ?? null, null, 'tệp sai không làm lưu nửa chừng');
await dlg.getByLabel('Ảnh CCCD mặt sau').setInputFiles({ name: 'back.png', mimeType: 'image/png', buffer: png });
await dlg.getByRole('button', { name: 'Tải ảnh CCCD' }).click();
await page.getByText('Đã lưu ảnh CCCD').waitFor();
await dlg.getByAltText('CCCD mặt trước').waitFor();
await dlg.getByAltText('CCCD mặt sau').waitFor();
const t3 = await api('GET', `/api/chi/tenants/${t.tenantId}`);
assert.ok(t3.frontImageUrl && t3.backImageUrl);
assert.equal((await page.request.get(`${base}${t3.frontImageUrl}`)).status(), 401, 'ảnh CCCD không mở được khi chưa đăng nhập');
// gửi lại ảnh CCCD: ảnh cũ bị xóa khỏi đĩa, ảnh mới xem được
await dlg.getByLabel('Ảnh CCCD mặt trước').setInputFiles({ name: 'front2.png', mimeType: 'image/png', buffer: png });
await dlg.getByLabel('Ảnh CCCD mặt sau').setInputFiles({ name: 'back2.png', mimeType: 'image/png', buffer: png });
await dlg.getByRole('button', { name: 'Tải ảnh CCCD' }).click();
await page.waitForTimeout(1500);
const t4 = await api('GET', `/api/chi/tenants/${t.tenantId}`);
assert.notEqual(t4.frontImageUrl, t3.frontImageUrl, 'ảnh mới thay ảnh cũ');
assert.equal((await raw('GET', t3.frontImageUrl)).status(), 404, 'ảnh cũ đã bị xóa khỏi đĩa');
assert.equal((await raw('GET', t4.frontImageUrl)).status(), 200, 'ảnh mới xem được');
await dlg.getByRole('button', { name: 'Xác minh hồ sơ' }).click();
await page.getByText('Đã xác minh hồ sơ').waitFor();
assert.equal((await api('GET', `/api/chi/tenants/${t.tenantId}`)).isVerified, true);
await page.screenshot({ path: 'screens/tenant-profile.png', fullPage: true });
log('UC32 CCCD + xác minh OK');

// --- phân quyền: người ghi chỉ số không đọc/ghi được hồ sơ khách và ảnh CCCD
const reader = await staffToken('METER_READER');
for (const [m, u, d] of [['GET', '/api/chi/tenants'], ['GET', `/api/chi/tenants/${t.tenantId}`], ['POST', '/api/chi/tenants', { fullName: 'x', phoneNumber: '0900000000', identityNumber: '0123456789' }],
  ['PATCH', `/api/chi/tenants/${t.tenantId}/verify`], ['GET', `${t3.frontImageUrl}`]]) {
  assert.equal(await status(reader.token, m, u, d), 403, `METER_READER ${m} ${u} phải 403`);
}
log('phân quyền METER_READER trên khách thuê OK');

console.log(JSON.stringify({ ok: true, tenantId: t.tenantId }));
await browser.close();
