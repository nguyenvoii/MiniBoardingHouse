// E2E thật UC108, UC109, UC113, UC120, UC121, UC122 + phân quyền: Cài đặt > thiết bị/phụ phí, danh mục, mẫu thông báo, lịch tự động, sao lưu.
import { start, assert, tag, log, base } from './lib.mjs';

const { browser, ctx, page, api, goto, staffToken, status, token, loginApi } = await start();
const tab = async (name) => { await goto('/settings'); await page.getByRole('button', { name, exact: true }).click(); };
const toast = (t) => page.getByText(t).first().waitFor();

// ---------- UC113 danh mục nhóm sự cố/thiết bị
const catName = `E2E Tủ lạnh ${tag}`;
const catCode = `E2E_FRIDGE_${tag}`;
await tab('Nhóm sự cố, thiết bị');
await page.locator('#view').getByRole('button', { name: 'Thêm nhóm' }).click();
let dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thêm nhóm' }).click();
await dlg.getByText('Nhập mã và tên nhóm.').waitFor();
await dlg.getByLabel('Mã nhóm').fill(catCode);
await dlg.getByLabel('Tên nhóm').fill(catName);
await dlg.getByLabel('Loại nhóm').selectOption('EQUIPMENT');
await dlg.getByRole('button', { name: 'Thêm nhóm' }).click();
await toast('Đã thêm nhóm');
let cats = await api('GET', '/api/incident-categories?type=EQUIPMENT');
const cat = cats.find((c) => c.categoryCode === catCode);
assert.ok(cat && cat.isActive, 'nhóm có trong DB');
// trùng mã bị từ chối
await page.locator('#view').getByRole('button', { name: 'Thêm nhóm' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Mã nhóm').fill(catCode);
await dlg.getByLabel('Tên nhóm').fill('Trùng mã');
await dlg.getByLabel('Loại nhóm').selectOption('EQUIPMENT');
await dlg.getByRole('button', { name: 'Thêm nhóm' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByRole('button', { name: 'Hủy' }).click();
// sửa
await page.getByRole('row').filter({ hasText: catCode }).getByRole('button', { name: 'Sửa' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Tên nhóm').fill(`${catName} (sửa)`);
await dlg.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu nhóm');
assert.equal((await api('GET', '/api/incident-categories?type=EQUIPMENT')).find((c) => c.categoryCode === catCode).categoryName, `${catName} (sửa)`);
log('UC113 danh mục OK');

// ---------- tenant xin thiết bị (UC108): fixture tenant + hợp đồng + tài khoản
const roomR = await api('POST', '/api/chi/rooms', { propertyId: 1, floorId: 1, roomCode: `E2E-EQ-${tag}`, areaSqm: 20, maxOccupants: 2, baseRentPrice: 3000000, depositAmount: 3000000, initialElectricMeter: 100, initialWaterMeter: 10 });
const ten = await api('POST', '/api/chi/tenants', { fullName: `E2E Khách TB ${tag}`, phoneNumber: `09${tag}33`, identityNumber: `0${tag}3333`, dob: '1995-01-01' });
await api('POST', '/api/contracts', { roomId: roomR.roomId, tenantId: ten.tenantId, startDate: '2026-11-01', endDate: '2027-10-31', monthlyRentPrice: 3000000, depositAmount: 3000000, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 10000 });
const acct = await api('POST', `/api/tenants/${ten.tenantId}/account`);
const first = await loginApi(acct.phoneNumber, acct.temporaryPassword);
const tpass = `Qa#${tag}Tn9`;
assert.ok((await page.request.post(`${base}/api/auth/password`, { headers: { Authorization: `Bearer ${first.accessToken}` }, data: { currentPassword: acct.temporaryPassword, newPassword: tpass } })).ok());
const tToken = (await loginApi(acct.phoneNumber, tpass)).accessToken;
const tctx = await browser.newContext({ viewport: { width: 420, height: 900 } });
await tctx.addInitScript((t) => sessionStorage.setItem('stayly.session', t), tToken);
const tp = await tctx.newPage();
await tp.goto(`${base}/#/me/support`); await tp.waitForLoadState('networkidle');
await tp.getByRole('button', { name: 'Xin lắp hoặc dùng thêm thiết bị' }).click();
let td = tp.getByRole('dialog').last();
await td.getByRole('button', { name: 'Gửi yêu cầu' }).click();
await td.getByText('Nhập tên thiết bị.').waitFor();
await td.getByLabel('Tên thiết bị').fill(`Tủ lạnh ${tag}`);
await td.getByLabel('Phí dự kiến mỗi tháng').fill('50000');
await td.locator('select').nth(1).selectOption({ label: `${catName} (sửa)` });
await td.getByRole('button', { name: 'Gửi yêu cầu' }).click();
await tp.getByText('Đã gửi yêu cầu, chờ chủ trọ duyệt').waitFor();
await tp.getByText('Chờ chủ trọ duyệt').first().waitFor();
const eqs = (await api('GET', '/api/equipment-requests?size=100')).items.filter((r) => r.roomCode === roomR.roomCode);
assert.equal(eqs.length, 1);
assert.deepEqual([eqs[0].status, eqs[0].estimatedMonthlyFee, eqs[0].categoryId], ['PENDING', 50000, cat.categoryId]);
log('UC108 khách gửi yêu cầu thiết bị OK');

// ---------- UC108/109 chủ trọ duyệt, từ chối, lắp, phụ phí
await tab('Thiết bị và phụ phí');
const row = () => page.getByRole('row').filter({ hasText: roomR.roomCode }).filter({ hasText: `Tủ lạnh ${tag}` });
await row().getByRole('button', { name: 'Từ chối' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Từ chối', exact: true }).click();
await dlg.getByText('Nhập lý do từ chối để khách biết.').waitFor();
await dlg.getByRole('button', { name: 'Hủy' }).click();
await row().getByRole('button', { name: 'Duyệt', exact: true }).click();
await toast('Đã duyệt yêu cầu');
assert.equal((await api('GET', '/api/equipment-requests?size=100')).items.find((r) => r.roomCode === roomR.roomCode).status, 'APPROVED');
await row().getByRole('button', { name: 'Đã lắp đặt' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Số tiền mỗi kỳ').fill('0');
await dlg.getByRole('button', { name: 'Xác nhận đã lắp' }).click();
await dlg.getByText('Số tiền phụ phí phải lớn hơn 0.').waitFor();
await dlg.getByLabel('Số tiền mỗi kỳ').fill('50000');
await dlg.getByRole('button', { name: 'Xác nhận đã lắp' }).click();
await toast('Đã ghi nhận lắp đặt và tạo phụ phí');
const fee = (await api('GET', `/api/room-addon-fees?roomId=${roomR.roomId}`))[0];
assert.deepEqual([fee.amount, fee.isActive], [50000, true], 'phụ phí tạo trong DB');
await page.getByRole('row').filter({ hasText: roomR.roomCode }).filter({ hasText: 'Đang tính' }).first().waitFor();
// khách thấy trạng thái mới
await tp.reload(); await tp.waitForLoadState('networkidle');
await tp.getByText('Đã lắp đặt, tính phụ phí hàng tháng').first().waitFor();
// thêm phụ phí thủ công + tắt/bật
await page.locator('#view').getByRole('button', { name: 'Thêm phụ phí' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thêm phụ phí' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByRole('button', { name: 'Hủy' }).click();
const feeRow = () => page.getByRole('row').filter({ hasText: roomR.roomCode }).filter({ hasText: 'Đang tính' }).first();
await feeRow().getByRole('button', { name: 'Tắt phí' }).click();
await toast('Đã tắt phụ phí');
assert.equal((await api('GET', `/api/room-addon-fees?roomId=${roomR.roomId}`))[0].isActive, false);
await page.getByRole('row').filter({ hasText: roomR.roomCode }).getByRole('button', { name: 'Bật phí' }).click();
await toast('Đã bật phụ phí');
// tháo thiết bị
await row().getByRole('button', { name: 'Đã tháo' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Xác nhận' }).click();
await toast('Đã tháo, phụ phí đã tắt');
assert.equal((await api('GET', '/api/equipment-requests?size=100')).items.find((r) => r.roomCode === roomR.roomCode).status, 'REMOVED');
assert.equal((await api('GET', `/api/room-addon-fees?roomId=${roomR.roomId}`))[0].isActive, false);
log('UC108/109 duyệt-lắp-phụ phí-tháo OK');

// ---------- UC121 mẫu thông báo
const tcode = `E2E_TPL_${tag}`;
await tab('Mẫu thông báo');
await page.locator('#view').getByRole('button', { name: 'Thêm mẫu' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thêm mẫu' }).click();
await dlg.getByText('Nhập mã mẫu và nội dung.').waitFor();
await dlg.getByLabel('Mã mẫu').fill(tcode);
await dlg.getByLabel('Kênh gửi').selectOption('SYSTEM_APP');
await dlg.getByLabel('Tiêu đề').fill('Hóa đơn {{invoice}}');
await dlg.getByLabel('Nội dung').fill('Hóa đơn {{invoice}} số tiền {{amount}} đồng đến hạn {{dueDate}}.');
await dlg.getByRole('button', { name: 'Thêm mẫu' }).click();
await toast('Đã lưu mẫu thông báo');
const tpl = (await api('GET', '/api/notification-templates')).find((t) => t.templateCode === tcode);
assert.ok(tpl);
await page.getByRole('row').filter({ hasText: tcode }).getByRole('button', { name: 'Xem trước' }).click();
await page.getByRole('dialog').last().getByText(/số tiền/).waitFor();
await page.getByRole('dialog').last().getByRole('button', { name: 'Đóng', exact: true }).last().click();
await page.getByRole('row').filter({ hasText: tcode }).getByRole('button', { name: 'Tắt', exact: true }).click();
await toast('Đã tắt mẫu');
assert.equal((await api('GET', '/api/notification-templates')).find((t) => t.templateCode === tcode).isActive, false);
log('UC121 mẫu thông báo OK');

const manager0 = await staffToken('MANAGER');
// ---------- UC120 lịch tự động
await tab('Lịch tự động');
for (const n of ['Chốt kỳ ghi chỉ số quá hạn', 'Nhắc hạn thanh toán', 'Tính lại công nợ', 'Nhắc nợ tự động']) await page.getByRole('row').filter({ hasText: n }).first().waitFor();
await page.getByRole('row').filter({ hasText: 'Chốt kỳ ghi chỉ số quá hạn' }).getByRole('button', { name: 'Chạy ngay' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Chạy', exact: true }).click();
await toast('Đã chạy xong tác vụ');
const jobs = await api('GET', '/api/admin/jobs');
assert.equal(jobs.find((j) => j.name === 'meter-close').lastResult, 'SUCCESS');
// sửa lịch chạy: cron sai bị từ chối, cron đúng lưu và áp dụng, tắt tự chạy
const jobRow = () => page.getByRole('row').filter({ hasText: 'Chốt kỳ ghi chỉ số quá hạn' });
await jobRow().getByRole('button', { name: 'Sửa lịch' }).click();
let sch = page.getByRole('dialog').last();
await sch.getByLabel('Biểu thức cron').fill('khong phai cron');
await sch.getByRole('button', { name: 'Lưu lịch' }).click();
await sch.locator('.form-error, [role=alert]').first().waitFor();
await sch.getByLabel('Biểu thức cron').fill('0 20 2 * * *');
await sch.getByRole('button', { name: 'Lưu lịch' }).click();
await toast('Đã lưu lịch chạy');
let jb = (await api('GET', '/api/admin/jobs')).find((j) => j.name === 'meter-close');
assert.equal(jb.cron, '0 20 2 * * *');
assert.equal(jb.overridden, true);
await jobRow().getByText('Đã chỉnh').waitFor();
await jobRow().getByRole('button', { name: 'Sửa lịch' }).click();
sch = page.getByRole('dialog').last();
await sch.getByLabel('Tự chạy theo lịch').uncheck();
await sch.getByRole('button', { name: 'Lưu lịch' }).click();
await page.waitForTimeout(1200);
assert.equal((await api('GET', '/api/admin/jobs')).find((j) => j.name === 'meter-close').cron, '-');
// khôi phục lịch mặc định để không ảnh hưởng lần sau
await api('PUT', '/api/admin/jobs/meter-close/schedule', { cron: '0 0 1 * * *', enabled: true });
assert.equal(await status(manager0.token, 'PUT', '/api/admin/jobs/meter-close/schedule', { cron: '0 0 1 * * *', enabled: true }), 403, 'chỉ chủ trọ đổi lịch');
log('UC120 lịch tự động OK');

// ---------- UC122 sao lưu
if (await api('GET', '/api/admin/backups/availability')) {
  await tab('Sao lưu');
  const before = (await api('GET', '/api/admin/backups')).length;
  await page.getByRole('button', { name: 'Sao lưu ngay' }).click();
  await toast('Đã sao lưu');
  const backups = await api('GET', '/api/admin/backups');
  assert.equal(backups.length, before + 1, 'bản sao lưu mới có trong danh sách');
  await page.getByRole('row').filter({ hasText: backups[0].name }).first().waitFor();
  // phục hồi: nhập sai tên DB bị chặn (không đụng dữ liệu)
  await page.getByRole('row').first().waitFor();
  await page.getByRole('button', { name: 'Phục hồi' }).first().click();
  dlg = page.getByRole('dialog').last();
  await dlg.getByRole('button', { name: 'Phục hồi dữ liệu' }).click();
  await dlg.getByText('Gõ tên cơ sở dữ liệu để xác nhận.').waitFor();
  await dlg.getByLabel('Tên cơ sở dữ liệu để xác nhận').fill('sai-ten');
  await dlg.getByRole('button', { name: 'Phục hồi dữ liệu' }).click();
  await dlg.locator('.form-error, [role=alert]').first().waitFor();
  assert.ok(await api('GET', '/api/chi/tenants'), 'dữ liệu còn nguyên sau phục hồi bị từ chối');
  await dlg.getByRole('button', { name: 'Hủy' }).click();
  log('UC122 sao lưu OK (phục hồi thật: BLOCKED quyền CREATE DATABASE trên SQL Server QA)');
} else {
  assert.equal(await page.getByRole('button', { name: 'Sao lưu', exact: true }).count(), 0);
  assert.equal(await status(await token(), 'GET', '/api/admin/backups'), 404);
}

// ---------- phân quyền
const manager = await staffToken('MANAGER');
const reader = await staffToken('METER_READER');
assert.equal(await status(manager.token, 'GET', '/api/admin/backups'), 403, 'MANAGER không xem sao lưu');
assert.equal(await status(manager.token, 'POST', '/api/admin/backups'), 403, 'MANAGER không sao lưu');
assert.equal(await status(manager.token, 'GET', '/api/admin/jobs'), 403, 'MANAGER không xem job');
assert.equal(await status(manager.token, 'GET', '/api/notification-templates'), 200, 'MANAGER xem mẫu thông báo');
for (const u of ['/api/equipment-requests', '/api/incident-categories', '/api/notification-templates', '/api/room-addon-fees', '/api/admin/jobs', '/api/admin/backups']) {
  assert.equal(await status(reader.token, 'GET', u), 403, `METER_READER ${u} phải 403`);
}
assert.equal(await status(tToken, 'GET', '/api/equipment-requests'), 403, 'khách không dùng API quản lý thiết bị');
assert.equal(await status(tToken, 'POST', '/api/incident-categories', { categoryCode: 'X', categoryName: 'X', categoryType: 'INCIDENT' }), 403);
// giao diện: quản lý không thấy tab chỉ-chủ-trọ
const mctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
await mctx.addInitScript((t) => sessionStorage.setItem('stayly.session', t), manager.token);
const mp = await mctx.newPage();
await mp.goto(`${base}/#/settings`); await mp.waitForLoadState('networkidle');
await mp.getByRole('tab', { name: 'Mẫu thông báo' }).waitFor();
assert.equal(await mp.getByRole('button', { name: 'Sao lưu' }).count(), 0);
assert.equal(await mp.getByRole('tab', { name: 'Lịch tự động' }).count(), 0);
log('phân quyền OK');

console.log(JSON.stringify({ ok: true }));
await browser.close();
