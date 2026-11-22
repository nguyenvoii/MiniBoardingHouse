import { chromium } from 'playwright';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const baseURL = process.env.BASE_URL ?? 'http://localhost:18086';
const screenshot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'screens', 'checkin-api-failure.png');
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const calls = [];
await page.route('**/api/contracts/*/deposit', async (route) => {
  calls.push(route.request().url());
  await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'QA giả lập lỗi deposit' }) });
});
await page.route('**/api/chi/checkin-inspections*', async (route) => {
  calls.push(route.request().url());
  await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'QA giả lập lỗi biên bản' }) });
});
await page.goto(`${baseURL}/#/login`, { waitUntil: 'networkidle' });
await page.getByLabel(/email|số điện thoại|tài khoản/i).fill('admin@minihouse.local');
await page.getByLabel(/mật khẩu/i).fill('Admin@123');
await page.getByRole('button', { name: /đăng nhập/i }).click();
await page.waitForTimeout(1000);
if (page.url().includes('/#/login')) throw new Error('QA login failed');
const token = await page.evaluate(() => sessionStorage.getItem('stayly.session'));
const auth = { Authorization: `Bearer ${token}` };
const roomsRes = await page.request.get(`${baseURL}/api/chi/rooms?propertyId=1`, { headers: auth });
const tenantsRes = await page.request.get(`${baseURL}/api/chi/tenants`, { headers: auth });
const roomsBody = await roomsRes.json();
const tenantsBody = await tenantsRes.json();
const rooms = roomsBody.data ?? roomsBody;
const tenants = tenantsBody.data ?? tenantsBody;
const room = rooms.find((r) => r.status === 'VACANT');
const tenant = tenants[0];
if (!room || !tenant) throw new Error('No QA vacant room/tenant available for interrupted checkin test');
const createRes = await page.request.post(`${baseURL}/api/contracts`, { headers: { ...auth, 'Content-Type': 'application/json' }, data: {
  roomId: room.roomId, tenantId: tenant.tenantId, startDate: '2026-11-22', endDate: '2027-11-21',
  monthlyRentPrice: room.baseRentPrice, depositAmount: room.depositAmount, paymentCycleMonths: 1,
  paymentDueDay: 5, lateFeePerDay: 10000,
} });
const createBody = await createRes.json();
const created = createBody.data ?? createBody;
const c = created.contractId ? created : created.contract;
await page.goto(`${baseURL}/#/checkin?contract=${c.contractId}`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
const text = await page.locator('body').innerText();
const falseState = /Không thể kiểm tra tiến độ nhận phòng/i.test(text);
console.log(JSON.stringify({ contractId: c.contractId, calls, progressErrorShown: falseState, excerpt: text.slice(0, 1200) }, null, 2));
await page.screenshot({ path: screenshot, fullPage: true });
await browser.close();
