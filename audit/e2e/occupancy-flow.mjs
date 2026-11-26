// E2E thật UC27 (công tơ/đồng hồ nước của phòng), UC33 (người ở cùng), UC34 (phương tiện), UC57-UC59 (cảnh báo, lịch sử, so sánh tiêu thụ).
import { start, assert, tag, log, base } from './lib.mjs';
import { fixtureRoom, gotoMonth } from './lib2.mjs';

const s = await start();
const { page, api, goto, status } = s;
const toast = (t) => page.getByText(t).first().waitFor();
const fx = await fixtureRoom(api, { label: 'OC', startMonth: '2026-11' });
const closeAll = () => page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));
const openRoom = async () => {
  await closeAll();
  await goto('/billing'); await goto('/rooms'); await page.waitForTimeout(800);
  await page.getByPlaceholder('Nhập mã phòng').fill(fx.room.roomCode);
  await page.locator('.room-tile').filter({ hasText: fx.room.roomCode }).first().click();
  return page.getByRole('dialog').last();
};

// ---------- UC33 người ở cùng: thêm, sửa, xóa; vượt sức chứa bị từ chối
let dlg = await openRoom();
await dlg.getByRole('button', { name: 'Thêm người ở cùng' }).click();
let rd = page.getByRole('dialog').last();
await rd.getByRole('button', { name: 'Lưu', exact: true }).click();
await rd.locator('.form-error, [role=alert]').first().waitFor();
await rd.getByPlaceholder('Họ và tên').fill(`Người ở cùng ${tag}`);
await rd.getByPlaceholder(/Quan hệ/).fill('Bạn bè');
await rd.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1200);
let list = await api('GET', `/api/rooms/${fx.room.roomId}/residents`);
assert.ok(list.some((r) => r.fullName === `Người ở cùng ${tag}`), 'người ở cùng có trong DB');
await dlg.locator('.list-row').filter({ hasText: `Người ở cùng ${tag}` }).getByRole('button', { name: 'Sửa' }).click();
rd = page.getByRole('dialog').last();
await rd.getByPlaceholder(/Quan hệ/).fill('Vợ/chồng');
await rd.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1200);
list = await api('GET', `/api/rooms/${fx.room.roomId}/residents`);
assert.equal(list.find((r) => r.fullName === `Người ở cùng ${tag}`).relationship, 'Vợ/chồng');
// phòng tối đa 2 người: khách + 1 người ở cùng là đủ, người thứ hai nữa vượt sức chứa
const over = await s.raw('POST', `/api/rooms/${fx.room.roomId}/residents`, { fullName: `Vượt ${tag}`, relationship: 'Khác' });
assert.ok([400, 409].includes(over.status()), `vượt sức chứa phòng bị từ chối (${over.status()})`);
await dlg.locator('.list-row').filter({ hasText: `Người ở cùng ${tag}` }).getByRole('button', { name: 'Rời phòng' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Xác nhận', exact: true }).click();
await page.waitForTimeout(1200);
list = await api('GET', `/api/rooms/${fx.room.roomId}/residents`);
assert.ok(!list.some((r) => r.fullName === `Người ở cùng ${tag}`) || list.find((r) => r.fullName === `Người ở cùng ${tag}`).isActive === false, 'xóa người ở cùng');
log('UC33 người ở cùng OK');

// ---------- UC34 phương tiện của khách
await closeAll();
await goto('/billing'); await goto('/tenants'); await page.waitForTimeout(1000);
await page.getByPlaceholder('Tên, số điện thoại, CCCD hoặc phòng').fill(fx.phone);
await page.getByRole('row').filter({ hasText: `E2E Khách OC ${tag}` }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Thêm xe' }).click();
let vd = page.getByRole('dialog').last();
await vd.getByRole('button', { name: 'Lưu', exact: true }).click();
await vd.locator('.form-error, [role=alert]').first().waitFor();
await vd.getByPlaceholder('Ví dụ: 59A1-123.45').fill(`59A1-${tag.slice(0, 3)}.${tag.slice(3)}`);
await vd.getByRole('button', { name: 'Lưu', exact: true }).click();
await toast('Đã lưu xe');
let vehicles = await api('GET', `/api/tenants/${fx.tenant.tenantId}/vehicles`);
assert.equal(vehicles.length, 1);
// biển số trùng (xe đã đăng ký) bị từ chối
const dup = await s.raw('POST', `/api/tenants/${fx.tenant.tenantId}/vehicles`, { licensePlate: vehicles[0].licensePlate, vehicleType: 'MOTORBIKE' });
assert.ok([400, 409].includes(dup.status()), `biển số trùng bị từ chối (${dup.status()})`);
await dlg.locator('.list-row').filter({ hasText: vehicles[0].licensePlate }).getByRole('button', { name: 'Sửa' }).click();
vd = page.getByRole('dialog').last();
await vd.getByPlaceholder('Hãng xe (không bắt buộc)').fill('Honda');
const putDone = page.waitForResponse((r) => /\/api\/vehicles\/\d+$/.test(r.url()) && r.request().method() === 'PUT');
await vd.getByRole('button', { name: 'Lưu', exact: true }).click();
await putDone;
vehicles = await api('GET', `/api/tenants/${fx.tenant.tenantId}/vehicles`);
assert.equal(vehicles[0].brand, 'Honda');
log('UC34 phương tiện OK');

// ---------- UC27 công tơ điện / đồng hồ nước của phòng
dlg = await openRoom();
await dlg.getByRole('button', { name: 'Lịch sử công tơ' }).click();
await page.getByRole('dialog').last().getByRole('button', { name: 'Ghi lần lắp hoặc thay' }).click();
let md = page.getByRole('dialog').last();
await md.getByRole('button', { name: 'Lưu lần lắp' }).click();
await md.locator('.form-error, [role=alert]').first().waitFor();
await md.getByLabel('Số sê-ri').fill(`SN-${tag}`);
await md.getByLabel('Chỉ số lúc lắp').fill('0');
await md.getByRole('button', { name: 'Lưu lần lắp' }).click();
await toast('Đã lưu lịch sử công tơ');
const changes = await api('GET', `/api/chi/rooms/${fx.room.roomId}/meter-changes`);
assert.ok(changes.some((c) => c.serialNumber === `SN-${tag}`), 'lần lắp công tơ có trong DB');
await page.getByRole('dialog').last().getByText(`SN-${tag}`).first().waitFor();
log('UC27 công tơ/đồng hồ nước OK');

// ---------- UC57/58/59 cảnh báo tăng đột biến + lịch sử + so sánh tháng (trên khu 1, tháng quá khứ)
await closeAll();
const mk = async (month, e, w) => {
  const last = new Date(Number(month.slice(0, 4)), Number(month.slice(5)), 0).getDate();
  const p = (await api('GET', '/api/meter-periods?propertyId=1')).find((x) => x.startDate.startsWith(month)) ?? await api('POST', '/api/meter-periods', { propertyId: 1, periodName: `E2E ${month} ${tag}`, startDate: `${month}-01`, endDate: `${month}-${last}` });
  const r = await api('POST', '/api/meter-readings', { periodId: p.periodId, roomId: fx.room.roomId, newElectric: e, newWater: w, recordedByUserId: 1 });
  await api('POST', `/api/meter-readings/${r.readingId}/confirm`);
  return p;
};
const free = [];
const existing = (await api('GET', '/api/meter-periods?propertyId=1')).map((p) => p.startDate.slice(0, 7));
for (let n = 0; n < 240 && free.length < 2; n++) { const k = `${2036 + Math.floor(n / 12)}-${String((n % 12) + 1).padStart(2, '0')}`; if (!existing.includes(k) && (free.length === 0 || true)) free.push(k); }
if (free.length === 2) {
  const [m1, m2] = free;
  await mk(m1, 110, 11);      // dùng 10 kWh
  const p2 = await mk(m2, 210, 21); // dùng 100 kWh: tăng đột biến so với kỳ trước
  const alerts = await api('GET', `/api/meter-alerts?propertyId=1&roomId=${fx.room.roomId}`);
  assert.ok(alerts.some((a) => a.utilityType === 'ELECTRIC'), 'UC57 có cảnh báo điện tăng đột biến');
  await goto('/meters'); await page.waitForTimeout(800);
  await gotoMonth(page, m2);
  await page.getByText('Cảnh báo tiêu thụ bất thường').waitFor();
  await page.getByRole('row').filter({ hasText: fx.room.roomCode }).first().waitFor();
  // UC58/59 lịch sử tiêu thụ
  await page.getByRole('button', { name: 'Lịch sử tiêu thụ' }).click();
  const hd = page.getByRole('dialog').last();
  await hd.waitFor();
  await hd.locator('meter').first().waitFor(); // UC59 biểu đồ cột so sánh từng kỳ
  assert.ok((await hd.locator('meter').count()) >= 2, 'UC59 có cột tiêu thụ của từng kỳ');
  const hist = await api('GET', `/api/meter-readings/history?roomId=${fx.room.roomId}&fromDate=2030-01-01&toDate=2060-12-31`).catch(() => null);
  if (hist) assert.ok(JSON.stringify(hist).includes('100') || hist.length >= 2, 'UC58 lịch sử tiêu thụ của phòng');
  log('UC57/58/59 cảnh báo + lịch sử tiêu thụ OK');
} else log('UC57-59: hết tháng trống để dựng kịch bản cảnh báo, bỏ qua lần chạy này');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
const manager = await s.staffToken('MANAGER');
assert.ok([401, 403].includes(await status(reader.token, 'POST', `/api/chi/rooms/${fx.room.roomId}/meter-changes`, {})), 'METER_READER không sửa lịch sử công tơ');
assert.ok([401, 403].includes(await status(reader.token, 'POST', `/api/rooms/${fx.room.roomId}/residents`, { fullName: 'x' })), 'METER_READER không sửa người ở cùng');
assert.equal(await status(manager.token, 'GET', `/api/chi/rooms/${fx.room.roomId}/meter-changes`), 200);
log('phân quyền công tơ/người ở OK');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
