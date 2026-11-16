import { h, busy, mount } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, Page } from '../lib/api';
import { date, dateTime, money } from '../lib/format';
import { emptyState, field, formError, input, openDialog, pageHead, panel, plate, select, table, toast } from '../ui/kit';

interface Debt {
  debtId: number; tenantId: number; tenantName: string; roomId: number; roomCode: string; invoiceId: number | null;
  openingDebtBalance: number; currentPeriodDebt: number; totalOutstandingDebt: number; overdueDays: number; debtStatus: string; updatedAt: string;
}
interface DebtDetail {
  debt: Debt; latestPenaltyAmount: number;
  penalties: Array<{ penaltyId: number; overdueDays: number; dailyPenaltyRate: number; calculatedPenaltyAmount: number; createdAt: string }>;
  reminders: Array<{ reminderId: number; reminderLevel: string; messageContent: string; sentAt?: string; createdAt?: string }>;
  processingLogs: Array<{ procLogId: number; promisedPaymentDate: string | null; delayReason: string | null; staffNote: string | null; recordedByName: string | null; recordedAt: string }>;
}
interface DebtSummary { totalOutstanding: number; debtorCount: number; byStatus: Array<{ status: string; count: number; totalAmount: number }> }

const DEBT_STATUS: Record<string, [string, string]> = {
  NORMAL: ['Đang theo dõi', 'wait'], WARNING_OVERDUE: ['Quá hạn cảnh báo', 'danger'], LONG_TERM_DEBT: ['Nợ kéo dài', 'danger'], CLEARED: ['Đã thanh toán hết', 'ok'],
};
const stBadge = (s: string) => { const [l, t] = DEBT_STATUS[s] ?? [s, 'off']; return h('span', { class: `st ${t}` }, l); };

export async function debtsPage(view: HTMLElement): Promise<void> {
  const state = { status: 'ACTIVE_ONLY' };
  const holder = h('div');
  const status = select([['ACTIVE_ONLY', 'Còn nợ'], ['', 'Tất cả'], ['NORMAL', 'Đang theo dõi'], ['WARNING_OVERDUE', 'Quá hạn cảnh báo'], ['LONG_TERM_DEBT', 'Nợ kéo dài'], ['CLEARED', 'Đã thanh toán hết']], 'ACTIVE_ONLY');
  const sum = await api.get<DebtSummary>('/api/debts/summary');
  const load = async () => {
    const q = state.status === 'ACTIVE_ONLY' ? '' : `status=${state.status}&`;
    const r = await api.get<Page<Debt>>(`/api/debts?${q}size=100`);
    const rows = state.status === 'ACTIVE_ONLY' ? r.items.filter((d) => d.debtStatus !== 'CLEARED') : r.items;
    holder.replaceChildren(table<Debt>([
      { key: 'r', label: 'Phòng', render: (d) => plate(d.roomCode), sort: (a, b) => a.roomCode.localeCompare(b.roomCode) },
      { key: 't', label: 'Người thuê', render: (d) => d.tenantName },
      { key: 'o', label: 'Nợ kỳ trước (₫)', num: true, render: (d) => money(d.openingDebtBalance) },
      { key: 'c', label: 'Kỳ này (₫)', num: true, render: (d) => money(d.currentPeriodDebt) },
      { key: 'x', label: 'Tổng nợ (₫)', num: true, render: (d) => h('b', null, money(d.totalOutstandingDebt)), sort: (a, b) => a.totalOutstandingDebt - b.totalOutstandingDebt },
      { key: 'd', label: 'Quá hạn', num: true, render: (d) => (d.overdueDays ? `${d.overdueDays} ngày` : '–'), sort: (a, b) => a.overdueDays - b.overdueDays },
      { key: 's', label: 'Trạng thái', render: (d) => stBadge(d.debtStatus) },
    ], rows, { onRow: (d) => openDebt(d.debtId, () => void load()), empty: emptyState('Không có công nợ', 'Công nợ được tạo khi có hóa đơn chưa trả. Bấm «Tính lại công nợ» để cập nhật.') }));
  };
  status.addEventListener('change', () => { state.status = status.value; void load(); });

  const recalc = h('button', { class: 'btn secondary' }, icon('refresh', 16), 'Tính lại công nợ');
  recalc.addEventListener('click', () => busy(recalc, async () => {
    try { const r = await api.post<{ processed: number; cleared: number; penaltiesLogged: number }>('/api/debts/recalculate'); toast(`Đã xử lý ${r.processed} khoản, tất toán ${r.cleared}, ghi ${r.penaltiesLogged} phạt trễ hạn`); void debtsPage(view); }
    catch (e) { toast(errText(e), 'err'); }
  }));
  const remind = h('button', { class: 'btn' }, icon('send', 16), 'Gửi nhắc nợ');
  remind.addEventListener('click', () => busy(remind, async () => {
    try { const r = await api.post<{ sent: number }>('/api/debts/reminders/run'); toast(`Đã gửi ${r.sent} lời nhắc`); } catch (e) { toast(errText(e), 'err'); }
  }));
  const xl = h('button', { class: 'btn secondary' }, icon('download', 16), 'Excel');
  xl.addEventListener('click', () => api.download('/api/debts/export', 'cong-no.xlsx').catch((e) => toast(errText(e), 'err')));

  view.replaceChildren(
    pageHead('Công nợ', 'Các khoản người thuê còn nợ, số ngày quá hạn và lịch sử xử lý.', [xl, recalc, remind]),
    h('div', { class: 'stack' },
      h('div', { class: 'figures' },
        h('div', { class: `figure ${sum.totalOutstanding > 0 ? 'alert' : ''}` }, h('span', { class: 'label' }, 'Tổng công nợ'), h('span', { class: 'value' }, money(sum.totalOutstanding), h('small', null, '₫')), h('span', { class: 'note' }, `${sum.debtorCount} người còn nợ`)),
        ...sum.byStatus.filter((s) => s.status !== 'CLEARED').slice(0, 3).map((s) => h('div', { class: 'figure' }, h('span', { class: 'label' }, (DEBT_STATUS[s.status] ?? [s.status])[0]), h('span', { class: 'value' }, String(s.count), h('small', null, 'khoản')), h('span', { class: 'note' }, `${money(s.totalAmount)} ₫`)))),
      h('section', { class: 'panel panel-flush' }, h('div', { class: 'toolbar' }, h('span', { class: 'grow' }), status), holder),
    ),
  );
  await load();
}

async function openDebt(id: number, reload: () => void): Promise<void> {
  const r = await api.get<DebtDetail>(`/api/debts/${id}`).catch((e) => { toast(errText(e), 'err'); return null; });
  if (!r) return;
  const d = openDialog(`Công nợ ${r.debt.roomCode} · ${r.debt.tenantName}`, { drawer: true });
  mount(d.body,
    h('div', { style: 'display:flex;gap:10px;align-items:center;flex-wrap:wrap' }, plate(r.debt.roomCode, 'lg'), stBadge(r.debt.debtStatus)),
    h('dl', { class: 'dl' }, h('dt', null, 'Tổng nợ'), h('dd', null, `${money(r.debt.totalOutstandingDebt)} ₫`), h('dt', null, 'Quá hạn'), h('dd', null, `${r.debt.overdueDays} ngày`), h('dt', null, 'Phạt trễ hạn gần nhất'), h('dd', null, `${money(r.latestPenaltyAmount)} ₫`)),
    h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Lịch sử xử lý'),
      r.processingLogs.length ? h('ol', { class: 'timeline' }, ...r.processingLogs.map((l) => h('li', null, h('span', { class: 'pip' }), h('div', null,
        h('b', null, l.delayReason ?? l.staffNote ?? 'Ghi chú'), h('span', null, `${l.promisedPaymentDate ? 'Hẹn trả ' + date(l.promisedPaymentDate) + ' · ' : ''}${l.recordedByName ?? ''} · ${dateTime(l.recordedAt)}`))))) : h('p', { class: 'muted' }, 'Chưa có ghi chú xử lý.')),
    r.reminders.length ? h('div', null, h('h3', { style: 'margin-bottom:10px' }, 'Đã nhắc nợ'), h('ul', null, ...r.reminders.map((m) => h('li', { class: 'list-row' }, h('div', { class: 'grow' }, h('b', null, m.reminderLevel), h('span', null, m.messageContent)))))) : null,
  );
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Đóng'), h('button', { class: 'btn', onclick: () => { d.close(); logDialog(id, reload); } }, icon('edit', 16), 'Ghi nhận xử lý'));
}

function logDialog(id: number, done: () => void): void {
  const d = openDialog('Ghi nhận xử lý công nợ');
  const err = h('div');
  const promised = input({ type: 'date' });
  const reason = input({ placeholder: 'Lý do chậm trả' });
  const note = h('textarea', { class: 'textarea', placeholder: 'Ghi chú của nhân viên' }) as HTMLTextAreaElement;
  d.body.append(err, field('Ngày khách hẹn trả', promised), field('Lý do chậm', reason), field('Ghi chú', note));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    try { await api.post(`/api/debts/${id}/processing-logs`, { promisedPaymentDate: promised.value || null, delayReason: reason.value.trim() || null, staffNote: note.value.trim() || null }); d.close(); toast('Đã ghi nhận'); done(); }
    catch (e) { err.replaceChildren(formError(errText(e))); }
  }));
}

void panel;
