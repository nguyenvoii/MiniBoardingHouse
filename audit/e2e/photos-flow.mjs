// E2E thật UC55 (ảnh công tơ), UC112 (sự cố kèm ảnh + phân loại): UI -> API -> SQL QA.
import { start, assert, tag, log, base, tenantToken, openAs } from './lib.mjs';
import { fixtureRoom, gotoMonth } from './lib2.mjs';

const s = await start();
const { page, api, goto, status } = s;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

// ---------- UC55 ảnh công tơ khi ghi chỉ số
const periods = await api('GET', '/api/meter-periods?propertyId=1');
let y = 2034, m = 1;
const key = () => `${y}-${String(m).padStart(2, '0')}`;
while (periods.some((p) => p.startDate.startsWith(key()))) { m++; if (m > 12) { m = 1; y++; } }
const month = key();
const fx = await fixtureRoom(api, { label: 'PH', startMonth: month });
const period = await api('POST', '/api/meter-periods', { propertyId: 1, periodName: `E2E ảnh ${month} ${tag}`, startDate: `${month}-01`, endDate: `${month}-28` });
await goto('/meters');
await gotoMonth(page, month);
await page.getByRole('button', { name: new RegExp(`${fx.room.roomCode}.*Ghi`) }).click();
let dlg = page.getByRole('dialog').last();
await dlg.getByPlaceholder('Số điện hiện tại').fill('130');
await dlg.getByPlaceholder('Số nước hiện tại').fill('14');
await dlg.getByLabel('Ảnh công tơ điện').setInputFiles({ name: 'dien.png', mimeType: 'image/png', buffer: png });
await dlg.getByLabel('Ảnh đồng hồ nước').setInputFiles({ name: 'ghi-chu.txt', mimeType: 'text/plain', buffer: Buffer.from('không phải ảnh') });
await dlg.getByRole('button', { name: 'Lưu chỉ số' }).click();
await page.getByText(/chưa lưu được ảnh/).first().waitFor(); // chỉ số lưu, ảnh nước sai định dạng báo lỗi rõ
const rd = (await api('GET', `/api/meter-periods/${period.periodId}/readings`)).find((r) => r.roomId === fx.room.roomId);
assert.ok(rd, 'chỉ số vẫn được lưu khi ảnh lỗi');
const photos = await api('GET', `/api/meter-readings/${rd.readingId}/photos`);
assert.deepEqual(photos.map((p) => p.meterType), ['ELECTRIC'], 'chỉ ảnh điện hợp lệ được lưu');
await page.waitForTimeout(800);
await page.getByRole('row').filter({ hasText: fx.room.roomCode }).getByRole('button', { name: 'Ảnh' }).click();
await page.getByRole('dialog').last().getByAltText('Công tơ điện').waitFor();
assert.equal((await page.request.get(`${base}${photos[0].photoUrl}`)).status(), 401, 'ảnh công tơ cần đăng nhập');
await page.keyboard.press('Escape');
log('UC55 ảnh công tơ OK');

// ---------- UC112 sự cố kèm ảnh + phân loại động
const catCode = `E2E_INC_${tag}`;
await api('POST', '/api/incident-categories', { categoryCode: catCode, categoryName: `Sự cố E2E ${tag}`, categoryType: 'INCIDENT', description: 'E2E' });
const tTok = await tenantToken(s, fx.tenant.tenantId);
const t = await openAs(s, tTok, { width: 420, height: 900 });
await t.page.goto(`${base}/#/me/support`); await t.page.waitForLoadState('networkidle');
await t.page.getByRole('button', { name: 'Báo sự cố' }).click();
dlg = t.page.getByRole('dialog').last();
await dlg.locator('select').first().selectOption({ label: `Sự cố E2E ${tag}` }); // danh mục do chủ trọ quản lý
await dlg.getByPlaceholder('Mô tả sự cố để chủ trọ xử lý nhanh').fill('Vòi nước rò, có ảnh kèm');
await dlg.getByLabel('Ảnh sự cố').setInputFiles({ name: 'doc.txt', mimeType: 'text/plain', buffer: Buffer.from('không phải ảnh') });
await dlg.getByRole('button', { name: 'Gửi', exact: true }).click();
await dlg.locator('.form-error, [role=alert]').first().waitFor(); // tệp không phải ảnh bị từ chối
await dlg.getByLabel('Ảnh sự cố').setInputFiles({ name: 'ro-nuoc.png', mimeType: 'image/png', buffer: png });
await dlg.getByRole('button', { name: 'Gửi', exact: true }).click();
await t.page.getByText('Đã gửi yêu cầu').first().waitFor();
const tick = (await api('GET', '/api/maintenance-requests?size=100')).items.find((x) => x.roomCode === fx.room.roomCode);
assert.ok(tick && tick.category === catCode && tick.issuePhotoUrl, 'sự cố có nhóm đã chọn và ảnh');
await goto('/maintenance');
await page.getByRole('row').filter({ hasText: fx.room.roomCode }).first().click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Xem ảnh sự cố' }).click();
await page.getByRole('dialog').last().getByAltText(`Phòng ${fx.room.roomCode}`).waitFor();
assert.equal((await page.request.get(`${base}${tick.issuePhotoUrl}`)).status(), 401);
const other = await fixtureRoom(api, { label: 'PH2', startMonth: month });
const oTok = await tenantToken(s, other.tenant.tenantId);
assert.ok([403, 404].includes(await status(oTok, 'GET', tick.issuePhotoUrl)), 'khách khác không xem được ảnh sự cố');
assert.equal(await status(tTok, 'GET', tick.issuePhotoUrl), 200, 'chủ sự cố xem được ảnh');
log('UC112 sự cố kèm ảnh + phân loại OK');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
