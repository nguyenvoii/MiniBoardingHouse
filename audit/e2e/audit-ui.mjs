import { chromium } from 'playwright';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const baseURL = process.env.BASE_URL ?? 'http://localhost:8080';
const out = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'screens');
await fs.mkdir(out, { recursive: true });
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const errors = [];
const responses = [];
page.on('console', (m) => { if (m.type() === 'error') errors.push(`console: ${m.text()}`); });
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('response', (r) => {
  responses.push(`${r.status()} ${r.request().method()} ${r.url()}`);
  if (r.status() >= 400) errors.push(`HTTP ${r.status()} ${r.url()}`);
});

await page.goto(`${baseURL}/#/login`, { waitUntil: 'networkidle' });
await page.getByLabel(/email|số điện thoại|tài khoản/i).fill('admin@minihouse.local');
await page.getByLabel(/mật khẩu/i).fill('Admin@123');
await page.getByRole('button', { name: /đăng nhập/i }).click();
await page.waitForTimeout(800);
const loginUrl = page.url();
const loginText = await page.locator('body').innerText();
if (loginUrl.includes('/#/login') || /Đăng nhập/.test(loginText)) {
  throw new Error(`Login audit failed: url=${loginUrl}; body=${loginText.slice(0, 500)}`);
}

await page.goto(`${baseURL}/#/tenants`, { waitUntil: 'networkidle' });
try {
  await page.getByRole('heading', { name: 'Khách hàng' }).waitFor({ state: 'visible', timeout: 10000 });
  await page.getByLabel('Tìm khách hàng').waitFor({ state: 'visible', timeout: 10000 });
} catch (e) {
  await page.screenshot({ path: `${out}/failure-tenants.png`, fullPage: true });
  console.error(`Tenant route failed: url=${page.url()} body=${(await page.locator('body').innerText()).slice(0, 1000)}`);
  throw e;
}
const tenantAccountRequests = responses.filter((r) => r.includes('/api/tenants/') && r.includes('/account')).length;
if (tenantAccountRequests > 10) {
  throw new Error(`Tenant list made too many account requests: ${tenantAccountRequests}`);
}
await page.screenshot({ path: `${out}/tenants-desktop.png`, fullPage: true });
const tenantRows = await page.locator('tbody tr, .table-row, .card').count();
const tenantSearch = await page.getByLabel('Tìm khách hàng').count();

await page.goto(`${baseURL}/#/checkin`, { waitUntil: 'networkidle' });
await page.getByRole('heading', { name: 'Nhận khách thuê phòng' }).waitFor({ state: 'visible', timeout: 10000 });
await page.screenshot({ path: `${out}/checkin-desktop.png`, fullPage: true });
const tenantSelects = await page.locator('select').allTextContents();
const tenantOptionCount = tenantSelects.length ? await page.locator('select').first().locator('option').count() : 0;
const checkinStepText = await page.locator('body').innerText();

await page.setViewportSize({ width: 390, height: 844 });
await page.goto(`${baseURL}/#/tenants`, { waitUntil: 'networkidle' });
await page.screenshot({ path: `${out}/tenants-mobile.png`, fullPage: true });
await page.goto(`${baseURL}/#/checkin`, { waitUntil: 'networkidle' });
await page.screenshot({ path: `${out}/checkin-mobile.png`, fullPage: true });

await fs.writeFile(`${out}/result.json`, JSON.stringify({
  tenantRows, tenantSearch, tenantOptionCount,
  checkinHasHeading: /Nhận khách thuê phòng/.test(checkinStepText),
  errors,
  responses,
}, null, 2));
console.log(JSON.stringify({ tenantRows, tenantSearch, tenantOptionCount, errors, responses }, null, 2));
await browser.close();
