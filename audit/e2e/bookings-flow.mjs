// E2E thật UC37-UC39: khách gửi yêu cầu thuê & giữ chỗ (khách vãng lai ở trang đăng nhập, khách thuê trong cổng), chủ trọ duyệt/từ chối/ghi nhận cọc.
import { start, assert, tag, log, base, tenantToken, openAs } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const mkRoom = (n) => api('POST', '/api/chi/rooms', { propertyId: 1, floorId: 1, roomCode: `E2E-BK${n}-${tag}`, areaSqm: 20, maxOccupants: 2, baseRentPrice: 2500000, depositAmount: 2500000, initialElectricMeter: 100, initialWaterMeter: 10 });
const roomA = await mkRoom('A');
const roomB = await mkRoom('B');
const toast = (t) => page.getByText(t).first().waitFor();

// ---------- UC37 khách vãng lai gửi yêu cầu từ trang đăng nhập
const guestCtx = await s.browser.newContext({ viewport: { width: 420, height: 900 } });
const gp = await guestCtx.newPage();
await gp.goto(`${base}/#/login`); await gp.waitForLoadState('networkidle');
await gp.getByRole('button', { name: 'Gửi yêu cầu thuê phòng' }).click();
let g = gp.getByRole('dialog').last();
await g.locator('select').first().selectOption(String(roomA.roomId));
await g.getByRole('button', { name: 'Gửi yêu cầu' }).click();
await g.locator('.form-error, [role=alert]').first().waitFor(); // thiếu họ tên/SĐT/ngày
await g.getByLabel('Họ tên khách').fill(`E2E Khách vãng lai ${tag}`);
await g.getByLabel('Số điện thoại khách').fill(`09${tag}61`);
await g.getByLabel('Ngày dự kiến thuê').fill('2026-12-15');
await g.getByLabel('Số người ở').fill('2');
await g.getByRole('button', { name: 'Gửi yêu cầu' }).click();
await gp.getByText('Đã gửi yêu cầu').first().waitFor();
let bks = await api('GET', '/api/chi/bookings');
const bkA = bks.find((b) => b.roomId === roomA.roomId);
assert.ok(bkA && bkA.status === 'PENDING', 'yêu cầu PENDING trong DB');
assert.equal(bkA.occupantsCount ?? bkA.occupants, 2);
log('UC37 khách gửi yêu cầu OK');

// khách thuê đã có tài khoản gửi yêu cầu thứ hai trong cổng khách
const tn = await api('POST', '/api/chi/tenants', { fullName: `E2E Khách TK BK ${tag}`, phoneNumber: `09${tag}62`, identityNumber: `0${tag}6262`, dob: '1995-01-01' });
const tTok = await tenantToken(s, tn.tenantId);
const t = await openAs(s, tTok, { width: 420, height: 900 });
await t.page.goto(`${base}/#/me/support`); await t.page.waitForLoadState('networkidle');
await t.page.getByRole('button', { name: 'Gửi yêu cầu thuê phòng' }).click();
const td = t.page.getByRole('dialog').last();
await td.locator('select').first().selectOption(String(roomB.roomId));
await td.getByLabel('Ngày dự kiến thuê').fill('2026-12-20');
await td.getByRole('button', { name: 'Gửi yêu cầu' }).click();
await t.page.getByText('Đã gửi yêu cầu').first().waitFor();
bks = await api('GET', '/api/chi/bookings');
const bkB = bks.find((b) => b.roomId === roomB.roomId);
assert.ok(bkB && bkB.status === 'PENDING');
log('UC37 khách thuê gửi yêu cầu OK');

// ---------- UC38 duyệt / từ chối
await goto('/rooms'); await page.waitForTimeout(1000);
await page.getByRole('button', { name: 'Yêu cầu thuê phòng' }).click();
let dlg = page.getByRole('dialog').last();
await dlg.getByRole('row').filter({ hasText: `09${tag}61` }).getByRole('button', { name: 'Duyệt' }).click();
let ap = page.getByRole('dialog').last();
await ap.getByRole('button', { name: 'Giữ phòng' }).click();
await ap.getByText('Chọn thời điểm hết hạn.').waitFor();
const until = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 16);
await ap.locator('input[type=datetime-local]').fill(until);
await ap.getByRole('button', { name: 'Giữ phòng' }).click();
await page.waitForTimeout(1500);
const approved = (await api('GET', '/api/chi/bookings')).find((b) => b.bookingId === bkA.bookingId);
assert.equal(approved.status, 'APPROVED');
assert.equal((await api('GET', `/api/chi/rooms/${roomA.roomId}`)).holdUntil != null, true, 'phòng đang được giữ chỗ');
// phòng đang giữ chỗ: không lập hợp đồng cho người khác (409)
assert.equal(await status(await s.token(), 'POST', '/api/contracts', { roomId: roomA.roomId, tenantId: tn.tenantId, startDate: '2026-11-26', endDate: '2027-11-25', monthlyRentPrice: 1, depositAmount: 0, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 0 }), 409, 'phòng đang giữ chỗ không cho thuê người khác');
// từ chối kèm lý do (bắt buộc)
dlg = page.getByRole('dialog').last();
await dlg.getByRole('row').filter({ hasText: `09${tag}62` }).getByRole('button', { name: 'Từ chối' }).click();
let rj = page.getByRole('dialog').last();
await rj.getByRole('button', { name: 'Lưu', exact: true }).click();
await rj.locator('.form-error, [role=alert]').first().waitFor();
await rj.locator('input').first().fill('Phòng đã có người đặt trước');
await rj.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1500);
assert.equal((await api('GET', '/api/chi/bookings')).find((b) => b.bookingId === bkB.bookingId).status, 'REJECTED');
await t.page.reload(); await t.page.waitForLoadState('networkidle');
log('UC38 duyệt / từ chối OK');

// ---------- UC39 ghi nhận tiền đặt cọc giữ chỗ
dlg = page.getByRole('dialog').last();
await dlg.getByRole('row').filter({ hasText: `09${tag}61` }).getByRole('button', { name: 'Ghi nhận cọc' }).click();
let dp = page.getByRole('dialog').last();
await dp.locator('input').first().fill('-1');
await dp.getByRole('button', { name: 'Lưu', exact: true }).click();
await dp.locator('.form-error, [role=alert]').first().waitFor().catch(() => {});
await dp.locator('input').first().fill('500000');
await dp.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1500);
const paid = (await api('GET', '/api/chi/bookings')).find((b) => b.bookingId === bkA.bookingId);
assert.equal(paid.depositPaid, 500000, 'cọc giữ chỗ được ghi nhận');
log('UC39 cọc giữ chỗ OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
for (const [m, u, d] of [['GET', '/api/chi/bookings'], ['PATCH', `/api/chi/bookings/${bkA.bookingId}/approve`, {}], ['PATCH', `/api/chi/bookings/${bkA.bookingId}/deposit`, {}]]) {
  assert.ok([401, 403].includes(await status(reader.token, m, u, d)), `METER_READER ${m} ${u} bị chặn`);
}
assert.ok([401, 403].includes(await status(tTok, 'PATCH', `/api/chi/bookings/${bkA.bookingId}/approve`, {})), 'khách không duyệt yêu cầu');
log('phân quyền yêu cầu thuê OK');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
