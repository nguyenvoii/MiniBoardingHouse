import { h, busy } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, Page } from '../lib/api';
import { getMonth } from '../lib/ctx';
import { date, money, monthLabel } from '../lib/format';
import { confirmDialog, emptyState, field, formError, input, openDialog, pageHead, panel, select, table, toast } from '../ui/kit';

interface Expense { expenseId: number; expenseCategory: string; description: string | null; amount: number; expenseDate: string; receiptUrl: string | null; recordedByName: string | null }
interface Summary { totalAmount: number; count: number; byCategory: Array<{ expenseCategory: string; totalAmount: number; count: number }> }

export async function expensesPage(view: HTMLElement): Promise<void> {
  const month = getMonth();
  const [y, m] = month.split('-').map(Number);
  const from = `${month}-01`;
  const to = `${month}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}`;
  const [list, sum, cats] = await Promise.all([
    api.get<Page<Expense>>(`/api/expenses?from=${from}&to=${to}&size=100`),
    api.get<Summary>(`/api/expenses/summary?from=${from}&to=${to}`),
    api.get<string[]>('/api/expenses/categories').catch(() => [] as string[]),
  ]);
  const reload = () => void expensesPage(view);

  view.replaceChildren(
    pageHead('Chi phí vận hành', `${monthLabel(month)}. Khoản nhóm «Điện nước chung» được chia cho các phòng khi tính tiền; mọi khoản đều tính vào báo cáo doanh thu và chi phí.`, [
      h('button', { class: 'btn', onclick: () => expenseDialog(null, cats, reload) }, icon('plus', 16), 'Thêm khoản chi')]),
    h('div', { class: 'stack' },
      h('div', { class: 'figures' },
        h('div', { class: 'figure' }, h('span', { class: 'label' }, 'Tổng chi trong tháng'), h('span', { class: 'value' }, money(sum.totalAmount), h('small', null, '₫')), h('span', { class: 'note' }, `${sum.count} khoản`)),
        ...sum.byCategory.slice(0, 3).map((c) => h('div', { class: 'figure' }, h('span', { class: 'label' }, c.expenseCategory), h('span', { class: 'value' }, money(c.totalAmount), h('small', null, '₫')), h('span', { class: 'note' }, `${c.count} khoản`)))),
      panel('Các khoản chi', table<Expense>([
        { key: 'd', label: 'Ngày', render: (e) => date(e.expenseDate), sort: (a, b) => a.expenseDate.localeCompare(b.expenseDate) },
        { key: 'c', label: 'Nhóm', render: (e) => h('span', { class: e.expenseCategory === 'Điện nước chung' ? 'plate ghost' : 'cell-main' }, e.expenseCategory) },
        { key: 'n', label: 'Nội dung', render: (e) => e.description ?? '–' },
        { key: 'a', label: 'Số tiền (₫)', num: true, render: (e) => h('b', null, money(e.amount)), sort: (a, b) => a.amount - b.amount },
        { key: 'u', label: 'Người ghi', render: (e) => e.recordedByName ?? '–' },
        { key: 'x', label: '', render: (e) => h('div', { class: 'row-actions' },
          h('button', { class: 'btn secondary sm', onclick: () => expenseDialog(e, cats, reload) }, icon('edit', 14), 'Sửa'),
          h('button', { class: 'btn secondary sm', onclick: async () => {
            if (!(await confirmDialog('Xóa khoản chi', `Xóa «${e.description ?? e.expenseCategory}» ${money(e.amount)} ₫?`, 'Xóa', true))) return;
            try { await api.del(`/api/expenses/${e.expenseId}`); toast('Đã xóa'); reload(); } catch (x) { toast(errText(x), 'err'); }
          } }, 'Xóa')) },
      ], list.items, { empty: emptyState('Chưa có khoản chi nào trong tháng', 'Ghi các khoản như sửa chữa, vệ sinh, điện nước chung để báo cáo lợi nhuận đúng.') }), { flush: true }),
    ),
  );
}

function expenseDialog(e: Expense | null, cats: string[], done: () => void): void {
  const d = openDialog(e ? 'Sửa khoản chi' : 'Thêm khoản chi');
  const err = h('div');
  const all = [...new Set(['Điện nước chung', 'Bảo trì', 'Vệ sinh', 'Thuế', 'Khác', ...cats])];
  const cat = select(all.map((c) => [c, c] as [string, string]), e?.expenseCategory ?? 'Bảo trì');
  const desc = input({ value: e?.description ?? '', placeholder: 'Ví dụ: Thay bóng đèn hành lang' });
  const amount = input({ type: 'number', min: '1', step: '1000', value: String(e?.amount ?? ''), class: 'input num-in' });
  const day = input({ type: 'date', value: e?.expenseDate ?? new Date().toISOString().slice(0, 10) });
  d.body.append(err, field('Nhóm chi phí', cat, '«Điện nước chung» sẽ được chia cho các phòng theo số người ở'), field('Nội dung', desc),
    h('div', { class: 'form-grid' }, field('Số tiền (₫)', amount), field('Ngày chi', day)));
  const ok = h('button', { class: 'btn' }, 'Lưu');
  d.foot.append(h('button', { class: 'btn secondary', onclick: d.close }, 'Hủy'), ok);
  ok.addEventListener('click', () => busy(ok, async () => {
    const body = { expenseCategory: cat.value, description: desc.value.trim() || null, amount: Number(amount.value), expenseDate: day.value, receiptUrl: e?.receiptUrl ?? null };
    try { await (e ? api.put(`/api/expenses/${e.expenseId}`, body) : api.post('/api/expenses', body)); d.close(); toast('Đã lưu khoản chi'); done(); }
    catch (x) { err.replaceChildren(formError(errText(x))); }
  }));
}
