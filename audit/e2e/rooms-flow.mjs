// E2E thật UC14-UC21, UC22-UC26, UC28-UC29 (khu trọ, tầng, loại phòng, phòng, ảnh, thiết bị, trạng thái, chờ dọn).
import { start, assert, tag, log, base } from './lib.mjs';
import { fixtureRoom } from './lib2.mjs';

const s = await start();
const { page, api, goto, status } = s;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
const toast = (t) => page.getByText(t).first().waitFor();
const tab = async (name) => { await goto('/settings'); await page.waitForTimeout(500); await page.getByRole('tab', { name, exact: true }).click(); await page.waitForTimeout(500); };
const closeAll = () => page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));

// ---------- UC14 khu trọ (đổi rồi trả lại tên để không làm bẩn dữ liệu demo)
const getProp = async () => (await api('GET', '/api/chi/properties')).find((x) => x.propertyId === 1);
const prop = await getProp();
await tab('Khu trọ và tầng');
await page.getByRole('button', { name: /Sửa khu trọ|Sửa thông tin/ }).first().click();
let dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Tên khu trọ').fill('');
await dlg.getByRole('button', { name: 'Lưu thông tin' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByLabel('Tên khu trọ').fill(`${prop.propertyName} E2E`);
await dlg.getByLabel('Số điện thoại hỗ trợ').fill('0911222333');
await dlg.getByRole('button', { name: 'Lưu thông tin' }).click();
await page.waitForTimeout(1200);
let p2 = await getProp();
assert.equal(p2.propertyName, `${prop.propertyName} E2E`);
await closeAll();
await tab('Khu trọ và tầng');
await page.getByRole('button', { name: /Sửa khu trọ|Sửa thông tin/ }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Tên khu trọ').fill(prop.propertyName);
await dlg.getByLabel('Số điện thoại hỗ trợ').fill(prop.hotline ?? prop.supportPhone ?? prop.phoneNumber ?? '');
await dlg.getByRole('button', { name: 'Lưu thông tin' }).click();
await page.waitForTimeout(1200);
assert.equal((await getProp()).propertyName, prop.propertyName, 'đã khôi phục tên khu');
log('UC14 khu trọ OK');

// ---------- UC15 tầng
await closeAll();
await tab('Khu trọ và tầng');
const floorNo = Math.max(100, ...(await api('GET', '/api/chi/floors?propertyId=1')).map((f) => f.floorNumber)) + 1;
await page.getByRole('button', { name: 'Thêm tầng' }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Số tầng').fill(String(floorNo));
await dlg.getByLabel('Tên tầng').fill(`Tầng E2E ${tag}`);
await dlg.getByRole('button', { name: 'Lưu tầng' }).click();
await page.getByRole('row').filter({ hasText: `Tầng E2E ${tag}` }).first().waitFor();
const floors = await api('GET', '/api/chi/floors?propertyId=1');
const floor = floors.find((f) => f.floorName === `Tầng E2E ${tag}`);
assert.ok(floor, 'tầng có trong DB');
// trùng số tầng bị từ chối
await page.getByRole('button', { name: 'Thêm tầng' }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Số tầng').fill(String(floorNo));
await dlg.getByLabel('Tên tầng').fill('Trùng số');
await dlg.getByRole('button', { name: 'Lưu tầng' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByRole('button', { name: 'Hủy' }).click();
await page.getByRole('row').filter({ hasText: `Tầng E2E ${tag}` }).getByRole('button', { name: /Sửa/ }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Tên tầng').fill(`Tầng E2E ${tag} (sửa)`);
await dlg.getByRole('button', { name: 'Lưu tầng' }).click();
await page.getByRole('row').filter({ hasText: `(sửa)` }).first().waitFor();
log('UC15 tầng thêm/sửa/trùng OK');

// ---------- UC16 loại phòng + giá theo loại
await closeAll();
await tab('Loại phòng');
await page.getByRole('button', { name: 'Thêm loại phòng' }).first().click();
dlg = page.getByRole('dialog').last();
const typeInputs = dlg.locator('input');
await typeInputs.first().fill(`Loại E2E ${tag}`);
await dlg.getByLabel('Giá thuê mặc định (₫)').fill('2800000');
await dlg.getByLabel('Tiền cọc mặc định (₫)').fill('2800000');
await dlg.getByRole('button', { name: /^Lưu|Thêm/ }).last().click();
await page.getByRole('row').filter({ hasText: `Loại E2E ${tag}` }).first().waitFor({ timeout: 8000 }).catch(async () => { console.log('LOẠI PHÒNG form:', await dlg.innerText()); });
assert.ok((await api('GET', '/api/chi/room-types?propertyId=1')).some((t) => (t.typeName ?? t.roomTypeName ?? t.name) === `Loại E2E ${tag}`), 'loại phòng có trong DB');
log('UC16 loại phòng (thêm) OK');

// ---------- UC17/18/19/20/21 phòng
await closeAll();
await goto('/rooms'); await page.waitForTimeout(800);
await page.getByRole('button', { name: 'Thêm phòng', exact: true }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thêm phòng' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByLabel('Tầng').selectOption({ index: 0 });
await dlg.getByLabel('Loại phòng').selectOption({ label: `Loại E2E ${tag}` });
assert.equal(await dlg.getByLabel('Giá thuê niêm yết (₫/tháng)').inputValue(), '2800000', 'UC16 chọn loại phòng thì giá thuê mặc định của loại được điền');
await dlg.getByLabel('Mã phòng').fill(`E2E-RM-${tag}`);
await dlg.getByLabel('Diện tích (m²)').fill('-5');
await dlg.getByLabel('Số người tối đa').fill('2');
await dlg.getByLabel('Giá thuê niêm yết (₫/tháng)').fill('2400000');
await dlg.getByLabel('Tiền cọc (₫)').fill('2400000');
await dlg.getByRole('button', { name: 'Thêm phòng' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor(); // diện tích âm bị từ chối
await dlg.getByLabel('Diện tích (m²)').fill('24');
const made = page.waitForResponse((r) => r.url().endsWith('/api/chi/rooms') && r.request().method() === 'POST');
await dlg.getByRole('button', { name: 'Thêm phòng' }).click();
assert.equal((await made).status(), 200);
const room = (await api('GET', '/api/chi/rooms?propertyId=1')).find((r) => r.roomCode === `E2E-RM-${tag}`);
assert.ok(room && room.status === 'VACANT' && room.baseRentPrice === 2400000 && room.roomTypeId, 'phòng gắn loại phòng đã chọn');
// trùng mã bị từ chối
await page.waitForTimeout(1000);
await page.getByRole('button', { name: 'Thêm phòng', exact: true }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Mã phòng').fill(`E2E-RM-${tag}`);
await dlg.getByLabel('Diện tích (m²)').fill('20');
await dlg.getByLabel('Số người tối đa').fill('2');
await dlg.getByLabel('Giá thuê niêm yết (₫/tháng)').fill('2000000');
await dlg.getByLabel('Tiền cọc (₫)').fill('2000000');
await dlg.getByRole('button', { name: 'Thêm phòng' }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor();
await dlg.getByRole('button', { name: 'Hủy' }).click();
log('UC17 thêm phòng + lỗi OK');

// UC19/20/21 tra cứu theo mã, lọc, sơ đồ theo tầng
await page.waitForTimeout(500);
const codeBox = page.getByPlaceholder('Nhập mã phòng');
await codeBox.fill(`E2E-RM-${tag}`);
await page.locator('.room-tile').filter({ hasText: `E2E-RM-${tag}` }).first().waitFor();
assert.equal(await page.locator('.room-tile').count(), 1, 'tra cứu theo mã chỉ còn một phòng');
assert.ok((await page.locator('section.floor h2').first().textContent()).startsWith('Tầng'), 'phòng nằm dưới tiêu đề tầng');
await codeBox.fill('');
await page.getByRole('button', { name: /^Trống \d+/ }).click();
assert.ok(await page.locator('.room-tile').count() > 0);
await page.getByRole('button', { name: /^Tất cả \d+/ }).click();
log('UC19/20/21 tra cứu + lọc + sơ đồ tầng OK');

// UC18 sửa phòng
await codeBox.fill(`E2E-RM-${tag}`);
await page.locator('.room-tile').filter({ hasText: `E2E-RM-${tag}` }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Sửa phòng' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Giá thuê niêm yết (₫/tháng)').fill('2600000');
await dlg.getByLabel('Chỉ số điện ban đầu').fill('120');
await dlg.getByRole('button', { name: 'Lưu phòng' }).click();
await page.waitForTimeout(1500);
const r2 = await api('GET', `/api/chi/rooms/${room.roomId}`);
assert.equal(r2.baseRentPrice, 2600000);
assert.equal(r2.initialElectricMeter, 120);
log('UC18 sửa phòng OK');

// ---------- UC22/23 ảnh phòng
await closeAll();
await goto('/billing'); await goto('/rooms'); await page.waitForTimeout(600);
await page.getByPlaceholder('Nhập mã phòng').fill(`E2E-RM-${tag}`);
await page.locator('.room-tile').filter({ hasText: `E2E-RM-${tag}` }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Tải ảnh phòng' }).click();
await dlg.getByText('Chọn ít nhất một ảnh.').waitFor();
await dlg.locator('input[type=file]').first().setInputFiles([{ name: 'a.png', mimeType: 'image/png', buffer: png }, { name: 'b.png', mimeType: 'image/png', buffer: png }]);
await dlg.getByRole('button', { name: 'Tải ảnh phòng' }).click();
await dlg.locator('img[alt^="Phòng"]').nth(1).waitFor();
let media = await api('GET', `/api/chi/media/room/${room.roomId}`);
assert.equal(media.length, 2, 'tải nhiều ảnh một lần');
await dlg.getByRole('button', { name: 'Đặt làm ảnh bìa' }).first().click();
await page.waitForTimeout(800);
media = await api('GET', `/api/chi/media/room/${room.roomId}`);
assert.equal(media.filter((m) => m.isCover).length, 1);
await dlg.getByRole('button', { name: 'Xóa ảnh' }).first().click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Xóa ảnh', exact: true }).last().click();
await page.waitForTimeout(1000);
assert.equal((await api('GET', `/api/chi/media/room/${room.roomId}`)).length, 1, 'xóa ảnh');
log('UC22/23 ảnh phòng OK');

// ---------- UC25/26 thiết bị phòng + bộ chuẩn
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thêm thiết bị' }).click();
let fd = page.getByRole('dialog').last();
await fd.getByRole('button', { name: 'Lưu thiết bị' }).click();
await fd.locator('.form-error, [role=alert]').first().waitFor();
await fd.getByLabel('Tên thiết bị').fill(`Tủ E2E ${tag}`);
await fd.getByLabel('Số lượng').fill('1');
await fd.getByLabel('Tình trạng').fill('Tốt');
await fd.getByRole('button', { name: 'Lưu thiết bị' }).click();
await page.waitForTimeout(1000);
let items = await api('GET', `/api/chi/furnishings/room/${room.roomId}`);
assert.ok(items.some((i) => i.itemName === `Tủ E2E ${tag}`));
await page.getByRole('dialog').last().locator('.list-row').filter({ hasText: `Tủ E2E ${tag}` }).getByRole('button', { name: 'Sửa', exact: true }).click();
fd = page.getByRole('dialog').last();
await fd.getByLabel('Tình trạng').fill('Trầy xước nhẹ');
await fd.getByRole('button', { name: 'Lưu thiết bị' }).click();
await page.waitForTimeout(1000);
items = await api('GET', `/api/chi/furnishings/room/${room.roomId}`);
assert.equal(items.find((i) => i.itemName === `Tủ E2E ${tag}`).conditionStatus ?? items.find((i) => i.itemName === `Tủ E2E ${tag}`).condition, 'Trầy xước nhẹ');
log('UC25/26 thiết bị + tình trạng OK');


// ---------- UC24 danh mục thiết bị chuẩn + áp dụng cho phòng
await closeAll();
await tab('Thiết bị chuẩn');
await page.locator('#view').getByRole('button', { name: 'Thêm thiết bị' }).click();
let cd = page.getByRole('dialog').last();
await cd.getByLabel('Tên thiết bị').fill(`Chuẩn E2E ${tag}`);
await cd.getByLabel('Số lượng').fill('1');
await cd.getByLabel('Tình trạng').fill('Mới');
await cd.getByRole('button', { name: 'Lưu thiết bị' }).click();
await page.waitForTimeout(1000);
const catalog = await api('GET', '/api/chi/furnishings/standard-catalog');
assert.ok(catalog.some((i) => i.itemName === `Chuẩn E2E ${tag}`), 'mục có trong danh mục chuẩn');
const apply = await api('POST', `/api/chi/furnishings/apply-standard/${room.roomId}`);
assert.ok((await api('GET', `/api/chi/furnishings/room/${room.roomId}`)).some((i) => i.itemName === `Chuẩn E2E ${tag}`), 'áp dụng bộ chuẩn cho phòng');
log('UC24 danh mục chuẩn OK');

// ---------- UC28 đổi trạng thái phòng
await closeAll();
await goto('/billing'); await goto('/rooms'); await page.waitForTimeout(800);
await page.getByPlaceholder('Nhập mã phòng').fill(`E2E-RM-${tag}`);
await page.locator('.room-tile').filter({ hasText: `E2E-RM-${tag}` }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Đổi trạng thái' }).click();
dlg = page.getByRole('dialog').last();
await dlg.getByLabel('Trạng thái').selectOption('REPAIRING');
await dlg.getByRole('button', { name: 'Lưu trạng thái' }).click();
await page.waitForTimeout(1200);
assert.equal((await api('GET', `/api/chi/rooms/${room.roomId}`)).status, 'REPAIRING');
// phòng đang sửa không lập được hợp đồng (409)
const t = await api('POST', '/api/chi/tenants', { fullName: `E2E Khách RM ${tag}`, phoneNumber: `09${tag}71`, identityNumber: `0${tag}7171`, dob: '1995-01-01' });
assert.equal(await status(await s.token(), 'POST', '/api/contracts', { roomId: room.roomId, tenantId: t.tenantId, startDate: '2026-11-26', endDate: '2027-11-25', monthlyRentPrice: 1, depositAmount: 0, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 0 }), 409, 'phòng sửa chữa không cho thuê');
await api('PATCH', `/api/chi/rooms/${room.roomId}/status?status=VACANT`);
log('UC28 đổi trạng thái OK');

// ---------- UC29 phòng chờ dọn: sau khi chấm dứt hợp đồng, xác nhận dọn xong trên giao diện
const fx = await fixtureRoom(api, { label: 'CL', startMonth: '2026-11' });
await api('POST', `/api/contracts/${fx.contract.contractId}/terminate`, { reason: 'Hai bên thỏa thuận hủy hợp đồng', confirmRoomCode: fx.room.roomCode });
assert.equal((await api('GET', `/api/chi/rooms/${fx.room.roomId}`)).status, 'CLEANING');
assert.equal(await status(await s.token(), 'POST', '/api/contracts', { roomId: fx.room.roomId, tenantId: t.tenantId, startDate: '2026-11-26', endDate: '2027-11-25', monthlyRentPrice: 1, depositAmount: 0, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 0 }), 409, 'phòng chờ dọn không giao được');
await closeAll();
await goto('/billing'); await goto('/rooms'); await page.waitForTimeout(800);
await page.getByRole('button', { name: /^Chờ dọn \d+/ }).click();
await page.locator('.room-tile').filter({ hasText: fx.room.roomCode }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByText('Chờ dọn dẹp').first().waitFor();
await dlg.getByRole('button', { name: 'Đã dọn xong, cho thuê lại' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Đã dọn xong', exact: true }).click();
await page.waitForTimeout(1200);
assert.equal((await api('GET', `/api/chi/rooms/${fx.room.roomId}`)).status, 'VACANT');
log('UC29 chờ dọn → sẵn sàng cho thuê OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
const manager = await s.staffToken('MANAGER');
const cashier = await s.staffToken('CASHIER');
for (const [m, u, d] of [['POST', '/api/chi/rooms', { roomCode: 'X' }], ['PUT', `/api/chi/rooms/${room.roomId}`, {}], ['POST', '/api/chi/floors', {}], ['PATCH', `/api/chi/rooms/${room.roomId}/status?status=SUSPENDED`]]) {
  assert.ok([401, 403].includes(await status(reader.token, m, u, d)), `METER_READER ${m} ${u} bị chặn`);
}
console.log('quyền cashier tạo phòng:', await status(cashier.token, 'POST', '/api/chi/rooms', { roomCode: 'X' }), ' | manager:', await status(manager.token, 'GET', '/api/chi/rooms?propertyId=1'));
log('phân quyền phòng OK');

console.log(JSON.stringify({ ok: true, room: room.roomCode }));
await s.browser.close();
