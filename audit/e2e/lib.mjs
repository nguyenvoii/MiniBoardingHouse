// Tiện ích chung cho các kịch bản E2E thật (UI -> API -> SQL Server QA). Không mock.
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { adminCredentials, localBase } from './environment.mjs';

export { assert };
export const base = localBase();
export const tag = String(Date.now()).slice(-6);
export const log = (...a) => console.log('•', ...a);

export async function start(role = adminCredentials()) {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await ctx.newPage();
  await page.goto(`${base}/#/login`, { waitUntil: 'networkidle' });
  assert.equal(new URL(page.url()).origin, base, 'Trang đăng nhập E2E phải ở máy QA');
  await page.getByLabel(/email|số điện thoại|tài khoản/i).fill(role.identifier);
  await page.getByLabel(/mật khẩu/i).fill(role.password);
  await page.getByRole('button', { name: /đăng nhập/i }).click();
  await page.waitForFunction(() => !!sessionStorage.getItem('stayly.session'));
  const token = () => page.evaluate(() => sessionStorage.getItem('stayly.session'));
  const raw = async (method, url, data, tok) => page.request.fetch(`${base}${url}`, {
    method, maxRedirects: 0, headers: { Authorization: `Bearer ${tok ?? (await token())}`, 'Content-Type': 'application/json' }, data,
  });
  const api = async (method, url, data) => {
    const res = await raw(method, url, data);
    const body = await res.json().catch(() => ({}));
    assert.ok(res.ok(), `${method} ${url} -> ${res.status()} ${JSON.stringify(body).slice(0, 300)}`);
    return body.data ?? body;
  };
  const goto = async (hash) => { await page.goto(`${base}/#${hash}`); await page.waitForLoadState('networkidle'); };
  const loginApi = async (identifier, password) => (await (await page.request.post(`${base}/api/auth/login`, { maxRedirects: 0, data: { identifier, password } })).json()).data;

  /** Tạo nhân viên theo vai trò, đổi mật khẩu tạm rồi trả về token để thử quyền. */
  const staffCache = {};
  async function staffToken(roleName) {
    if (staffCache[roleName]) return staffCache[roleName];
    const email = `e2e.${roleName.toLowerCase()}.${tag}@example.test`;
    const staff = await api('POST', '/api/staff', { fullName: `E2E ${roleName} ${tag}`, email, roles: [roleName] });
    const first = await loginApi(email, staff.temporaryPassword);
    const pass = `Qa#${tag}Mx9`;
    const chg = await page.request.post(`${base}/api/auth/password`, { headers: { Authorization: `Bearer ${first.accessToken}` }, data: { currentPassword: staff.temporaryPassword, newPassword: pass } });
    assert.ok(chg.ok(), `đổi mật khẩu tạm ${roleName}: ${chg.status()}`);
    return (staffCache[roleName] = { token: (await loginApi(email, pass)).accessToken, email, pass });
  }
  const status = async (tok, method, url, data) => (await raw(method, url, data, tok)).status();
  return { browser, ctx, page, token, raw, api, goto, loginApi, staffToken, status };
}

/** Fixture qua API (không phải bằng chứng E2E, chỉ dựng dữ liệu): phòng + khách + hợp đồng đã nhận phòng + hóa đơn đã phát hành. */
export async function fixtureInvoice(api, { rent = 3000000, label = 'INV', tenant: existing = null, issueDate = null, dueDate = null } = {}) {
  const periods = await api('GET', '/api/meter-periods?propertyId=1');
  const billing = await api('GET', '/api/billing/periods?propertyId=1');
  let y = 2031, m = 1;
  const key = () => `${y}-${String(m).padStart(2, '0')}`;
  while (periods.some((p) => p.startDate.startsWith(key())) || billing.some((b) => b.monthYear === key())) { m++; if (m > 12) { m = 1; y++; } }
  const month = key();
  const room = await api('POST', '/api/chi/rooms', { propertyId: 1, floorId: 1, roomCode: `E2E-${label}-${tag}`, areaSqm: 20, maxOccupants: 2, baseRentPrice: rent, depositAmount: rent, initialElectricMeter: 100, initialWaterMeter: 10 });
  const ph = `09${tag}${String(Math.floor(Math.random() * 90) + 10)}`;
  const tenant = existing ?? await api('POST', '/api/chi/tenants', { fullName: `E2E Khách ${label} ${tag}`, phoneNumber: ph, identityNumber: `0${tag}${ph.slice(-4)}`, dob: '1995-01-01' });
  const contract = await api('POST', '/api/contracts', { roomId: room.roomId, tenantId: tenant.tenantId, startDate: `${month}-01`, endDate: `${y + 2}-12-31`, monthlyRentPrice: rent, depositAmount: rent, paymentCycleMonths: 1, paymentDueDay: 5, lateFeePerDay: 10000 });
  await api('POST', `/api/contracts/${contract.contractId}/deposit`, { amount: rent });
  await api('POST', '/api/chi/checkin-inspections', { roomId: room.roomId, tenantId: tenant.tenantId, contractId: contract.contractId, checkinDate: `${month}-01`, initialElectricMeter: 100, initialWaterMeter: 10, keyHandedCount: 2, furnishingsCondition: 'Phòng sạch', tenantConfirmed: true });
  const last = new Date(y, m, 0).getDate();
  const mp = await api('POST', '/api/meter-periods', { propertyId: 1, periodName: `E2E ${month} ${tag}`, startDate: `${month}-01`, endDate: `${month}-${last}` });
  const rd = await api('POST', '/api/meter-readings', { periodId: mp.periodId, roomId: room.roomId, newElectric: 150, newWater: 15, recordedByUserId: 1 });
  await api('POST', `/api/meter-readings/${rd.readingId}/confirm`);
  const bp = (await api('POST', '/api/billing/periods', { propertyId: 1, monthYear: month })).period;
  await api('POST', `/api/billing/periods/${bp.billingPeriodId}/calculate`);
  await api('POST', `/api/billing/periods/${bp.billingPeriodId}/approve?force=true`);
  await api('POST', '/api/invoices/batch', { billingPeriodId: bp.billingPeriodId, issueDate, dueDate });
  const invs = await api('GET', `/api/invoices?propertyId=1&monthYear=${month}`);
  const invoice = invs.find((i) => i.roomCode === room.roomCode);
  assert.ok(invoice, 'hóa đơn của fixture');
  return { month, room, tenant, contract, invoice, phone: ph };
}

/** Cấp tài khoản cho khách (API), đổi mật khẩu tạm, trả token để mở giao diện khách. */
export async function tenantToken(s, tenantId) {
  const acct = await s.api('POST', `/api/tenants/${tenantId}/account`);
  const first = await s.loginApi(acct.phoneNumber, acct.temporaryPassword);
  const pass = `Qa#${tag}Tn9`;
  const chg = await s.page.request.post(`${base}/api/auth/password`, { headers: { Authorization: `Bearer ${first.accessToken}` }, data: { currentPassword: acct.temporaryPassword, newPassword: pass } });
  assert.ok(chg.ok(), `đổi mật khẩu tạm khách: ${chg.status()}`);
  return (await s.loginApi(acct.phoneNumber, pass)).accessToken;
}
export async function openAs(s, token, viewport = { width: 1440, height: 1000 }) {
  const ctx = await s.browser.newContext({ viewport });
  await ctx.addInitScript((t) => sessionStorage.setItem('stayly.session', t), token);
  return { ctx, page: await ctx.newPage() };
}
