// Tiện ích bổ sung cho các kịch bản E2E (tháng làm việc, phòng chưa có kỳ ghi số).
import { tag } from './lib.mjs';

/** Chọn tháng làm việc trên thanh trên bằng cách bấm Tháng sau (tháng mặc định là tháng hiện tại 2026-11). */
export async function gotoMonth(page, month, from = [2026, 11]) {
  const [y, m] = month.split('-').map(Number);
  const n = (y - from[0]) * 12 + (m - from[1]);
  await page.evaluate((count) => {
    const b = [...document.querySelectorAll('button')].find((x) => x.getAttribute('aria-label') === (count >= 0 ? 'Tháng sau' : 'Tháng trước'));
    for (let i = 0; i < Math.abs(count); i++) b.click();
  }, n);
  await page.waitForFunction((label) => document.querySelector('output')?.textContent?.includes(label), `${m}/${y}`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);
}

/** Phòng + khách + hợp đồng đã nhận phòng, chưa có kỳ ghi số: dùng cho các thử nghiệm ghi chỉ số. */
export async function fixtureRoom(api, { label = 'RM', startMonth = '2034-01', tenant: existing = null } = {}) {
  const room = await api('POST', '/api/chi/rooms', { propertyId: 1, floorId: 1, roomCode: `E2E-${label}-${tag}`, areaSqm: 20, maxOccupants: 2, baseRentPrice: 3000000, depositAmount: 3000000, initialElectricMeter: 100, initialWaterMeter: 10 });
  const ph = `09${tag}${String(Math.floor(Math.random() * 90) + 10)}`;
  const tenant = existing ?? await api('POST', '/api/chi/tenants', { fullName: `E2E Khách ${label} ${tag}`, phoneNumber: ph, identityNumber: `0${tag}${ph.slice(-4)}`, dob: '1995-01-01' });
  const [y] = startMonth.split('-').map(Number);
  const contract = await api('POST', '/api/contracts', { roomId: room.roomId, tenantId: tenant.tenantId, startDate: `${startMonth}-01`, endDate: `${y + 2}-12-31`, monthlyRentPrice: 3000000, depositAmount: 3000000, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 10000 });
  await api('POST', `/api/contracts/${contract.contractId}/deposit`, { amount: 3000000 });
  await api('POST', '/api/chi/checkin-inspections', { roomId: room.roomId, tenantId: tenant.tenantId, contractId: contract.contractId, checkinDate: `${startMonth}-01`, initialElectricMeter: 100, initialWaterMeter: 10, keyHandedCount: 2, furnishingsCondition: 'Phòng sạch', tenantConfirmed: true });
  return { room, tenant, contract, phone: ph };
}
