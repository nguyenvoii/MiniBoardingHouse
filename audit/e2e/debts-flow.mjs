// E2E thật UC93, UC95-UC100 (+UC82 nhắc quá hạn): công nợ từng phòng/khách, phạt chậm, phân loại, nhắc nợ, ghi nhận xử lý, xuất Excel.
import { start, assert, tag, log, base, fixtureInvoice, tenantToken, openAs } from './lib.mjs';

const s = await start();
const { page, api, goto, status } = s;
const fx = await fixtureInvoice(api, { label: 'DB', issueDate: '2026-08-01', dueDate: '2026-08-10' });
const inv = fx.invoice;
log('fixture', inv.invoiceCode, 'hạn', inv.dueDate, 'trạng thái', inv.paymentStatus);
const tTok = await tenantToken(s, fx.tenant.tenantId);
await api('POST', '/api/debts/recalculate');
const debts = (await api('GET', `/api/debts?roomId=${fx.room.roomId}&status=&size=50`)).items;
const mine = debts.find((d) => d.roomCode === fx.room.roomCode);
assert.ok(mine, 'công nợ của phòng fixture có trong danh sách');
log('công nợ', JSON.stringify({ total: mine.totalDebt ?? mine.debtAmount ?? mine.amount, days: mine.overdueDays, status: mine.debtStatus ?? mine.status }));

// ---------- UC93/95/96/97 giao diện Công nợ
await goto('/debts'); await page.waitForTimeout(1500);
await page.getByLabel('Tìm công nợ').fill(fx.room.roomCode); // tìm theo phòng giữa nhiều khoản nợ
await page.waitForTimeout(800);
const row = page.getByRole('row').filter({ hasText: fx.room.roomCode }).first();
await row.waitFor();
const text = await row.innerText();
assert.match(text.replace(/\./g, ''), new RegExp(String(inv.remainingAmount)), 'UC93 số tiền còn nợ của phòng/khách');
await row.click();
let dlg = page.getByRole('dialog').last();
await dlg.getByText(/quá hạn|ngày/i).first().waitFor();
log('UC93/96/97 công nợ + quá hạn OK');
// tính lại nhiều lần không nhân đôi (UC95)
await api('POST', '/api/debts/recalculate'); await api('POST', '/api/debts/recalculate');
const again = (await api('GET', `/api/debts?roomId=${fx.room.roomId}&status=&size=50`)).items.find((d) => d.roomCode === fx.room.roomCode);
assert.equal(JSON.stringify(again.totalDebt ?? again.debtAmount ?? again.amount), JSON.stringify(mine.totalDebt ?? mine.debtAmount ?? mine.amount), 'UC95 tính lại không nhân đôi nợ');

// ---------- UC99/UC82 nhắc nợ: gửi vào ứng dụng của khách, không gửi trùng trong ngày
const countNotifs = async () => (await s.raw('GET', '/api/me/notifications?size=50', undefined, tTok).then((r) => r.json())).data.items.filter((n) => /nợ|quá hạn|nhac|qua han/i.test(n.title + n.content)).length;
const n0 = await countNotifs();
await page.evaluate(() => document.querySelectorAll('dialog[open]').forEach((d) => d.close()));
await goto('/billing'); await goto('/debts'); await page.waitForTimeout(1500);
const run1 = page.waitForResponse((r) => r.url().includes('/api/debts/reminders/run') && r.request().method() === 'POST');
await page.getByRole('button', { name: 'Gửi nhắc nợ' }).click();
const r1 = (await (await run1).json()).data;
const n1 = await countNotifs();
assert.ok(n1 > n0, `khách nhận được nhắc nợ (trước ${n0}, sau ${n1}, đã gửi ${JSON.stringify(r1)})`);
const run2 = page.waitForResponse((r) => r.url().includes('/api/debts/reminders/run') && r.request().method() === 'POST');
await page.getByRole('button', { name: 'Gửi nhắc nợ' }).click();
await run2;
if ((await countNotifs()) !== n1) console.log('NOTIFS', JSON.stringify((await s.raw('GET', '/api/me/notifications?size=50', undefined, tTok).then((r) => r.json())).data.items.map((n) => [n.title, n.createdAt])));
assert.equal(await countNotifs(), n1, 'chạy lại không gửi trùng');
log('UC99 nhắc nợ + chống trùng OK');

// ---------- UC100 ghi nhận cam kết, lý do chậm
await page.getByLabel('Tìm công nợ').fill(fx.room.roomCode);
await page.waitForTimeout(900);
await page.getByRole('row').filter({ hasText: fx.room.roomCode }).first().click();
dlg = page.getByRole('dialog').last();
await dlg.getByRole('button', { name: 'Ghi nhận xử lý' }).click();
const log1 = page.getByRole('dialog').last();
await log1.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1000);
await log1.getByLabel('Lý do chậm').fill('Khách chờ lương');
await log1.getByLabel('Ghi chú').fill('Hẹn trả sau');
await log1.getByRole('button', { name: 'Lưu', exact: true }).click();
await page.waitForTimeout(1200);
const detail = await api('GET', `/api/debts/${mine.debtId ?? mine.id}`);
assert.ok(JSON.stringify(detail).includes('Khách chờ lương'), 'lịch sử xử lý có ghi nhận');
log('UC100 ghi nhận xử lý OK');

// khách đã hẹn ngày trả chưa tới thì hệ thống chưa nhắc tiếp (vẫn chống trùng)
const hen = new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10);
await api('POST', `/api/debts/${mine.debtId ?? mine.id}/processing-logs`, { promisedPaymentDate: hen, delayReason: 'Hẹn trả', staffNote: null });
const before = await countNotifs();
await api('POST', '/api/debts/reminders/run');
assert.equal(await countNotifs(), before, 'đã hẹn ngày trả thì không nhắc');

// ---------- UC98 Excel
const dl = page.waitForEvent('download');
await page.getByRole('button', { name: 'Excel' }).click();
const file = await dl;
assert.match(file.suggestedFilename(), /\.xlsx$/);
log('UC98 xuất Excel OK');

// ---------- phân quyền
const reader = await s.staffToken('METER_READER');
const other = await fixtureInvoice(api, { label: 'DB2' });
const oTok = await tenantToken(s, other.tenant.tenantId);
assert.ok([401, 403].includes(await status(reader.token, 'GET', '/api/debts')), 'METER_READER không xem công nợ');
assert.ok([401, 403].includes(await status(reader.token, 'POST', '/api/debts/reminders/run')), 'METER_READER không gửi nhắc nợ');
assert.ok([403, 404].includes(await status(oTok, 'GET', `/api/me/debts/invoices/${inv.invoiceId}/payments`)), 'khách khác không xem lịch sử thanh toán');
log('phân quyền công nợ OK');
console.log(JSON.stringify({ ok: true }));
await s.browser.close();
