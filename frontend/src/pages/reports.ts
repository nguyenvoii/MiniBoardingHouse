import { h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, errText, PROPERTY_ID } from '../lib/api';
import { money } from '../lib/format';
import type { RevenueReport } from '../data/types';
import { emptyState, pageHead, panel, select, table, toast } from '../ui/kit';

const NS = 'http://www.w3.org/2000/svg';
const el = (tag: string, attrs: Record<string, string | number>, text?: string): SVGElement => {
  const e = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
  if (text !== undefined) e.textContent = text;
  return e as SVGElement;
};

function chart(r: RevenueReport): SVGElement {
  const W = 920, H = 300, L = 64, B = 34, T = 16, R = 8;
  const max = Math.max(1, ...r.months.flatMap((m) => [m.revenue, m.expense]));
  const step = niceStep(max / 4);
  const top = Math.ceil(max / step) * step;
  const svg = el('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img', 'aria-label': `Doanh thu và chi phí năm ${r.year}` });
  const y = (v: number) => T + (H - T - B) * (1 - v / top);
  for (let v = 0; v <= top; v += step) {
    svg.appendChild(el('line', { x1: L, x2: W - R, y1: y(v), y2: y(v), class: 'grid' }));
    svg.appendChild(el('text', { x: L - 8, y: y(v) + 4, 'text-anchor': 'end' }, v >= 1e6 ? `${v / 1e6} tr` : money(v)));
  }
  const slot = (W - L - R) / 12;
  r.months.forEach((m, i) => {
    const x0 = L + slot * i + slot * 0.14;
    const bw = slot * 0.34;
    const rev = el('rect', { style: `--i:${i}`, x: x0, y: y(m.revenue), width: bw, height: Math.max(0, H - B - y(m.revenue)), rx: 3, class: 'bar-rev' });
    rev.appendChild(el('title', {}, `Tháng ${m.month}: doanh thu ${money(m.revenue)} ₫`));
    const exp = el('rect', { style: `--i:${i}`, x: x0 + bw + 3, y: y(m.expense), width: bw, height: Math.max(0, H - B - y(m.expense)), rx: 3, class: 'bar-exp' });
    exp.appendChild(el('title', {}, `Tháng ${m.month}: chi phí ${money(m.expense)} ₫`));
    svg.append(rev, exp, el('text', { x: L + slot * i + slot / 2, y: H - 12, 'text-anchor': 'middle' }, `T${m.month}`));
  });
  return svg;
}
function niceStep(raw: number): number {
  const p = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1))));
  const f = raw / p;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * p;
}

export async function reportsPage(view: HTMLElement): Promise<void> {
  const year = new Date().getFullYear();
  const yearSel = select([-2, -1, 0].map((d) => [String(year + d), `Năm ${year + d}`] as [string, string]), String(year));
  const holder = h('div', { class: 'stack' });
  const load = async () => {
    const r = await api.get<RevenueReport>(`/api/reports/revenue?propertyId=${PROPERTY_ID}&year=${yearSel.value}`);
    const has = r.totalRevenue > 0 || r.totalExpense > 0;
    holder.replaceChildren(
      h('div', { class: 'figures' },
        h('div', { class: 'figure' }, h('span', { class: 'label' }, 'Doanh thu cả năm'), h('span', { class: 'value' }, money(r.totalRevenue), h('small', null, '₫'))),
        h('div', { class: 'figure' }, h('span', { class: 'label' }, 'Chi phí cả năm'), h('span', { class: 'value' }, money(r.totalExpense), h('small', null, '₫'))),
        h('div', { class: `figure ${r.totalProfit < 0 ? 'alert' : ''}` }, h('span', { class: 'label' }, 'Lợi nhuận'), h('span', { class: 'value' }, money(r.totalProfit), h('small', null, '₫'))),
        h('div', { class: 'figure' }, h('span', { class: 'label' }, 'Cách tính'), h('span', { class: 'note' }, 'Doanh thu là các giao dịch thanh toán thành công theo ngày nhận tiền; chi phí theo ngày chi.'))),
      has ? panel('Doanh thu và chi phí theo tháng', [
        h('div', { class: 'panel-body' }, h('div', { class: 'legend', style: 'margin-bottom:12px' },
          h('span', null, h('i', { style: 'background:var(--primary)' }), 'Doanh thu'), h('span', null, h('i', { style: 'background:var(--plate);border:1.2px solid var(--plate-ink)' }), 'Chi phí')), chart(r)),
      ]) : panel(null, emptyState(`Chưa có phát sinh trong năm ${r.year}`, 'Biểu đồ hiện khi có giao dịch thanh toán hoặc khoản chi.')),
      panel('Chi tiết theo tháng', table([
        { key: 'm', label: 'Tháng', render: (m: RevenueReport['months'][number]) => `Tháng ${m.month}` },
        { key: 'r', label: 'Doanh thu (₫)', num: true, render: (m) => money(m.revenue) },
        { key: 'e', label: 'Chi phí (₫)', num: true, render: (m) => money(m.expense) },
        { key: 'p', label: 'Lợi nhuận (₫)', num: true, render: (m) => h('b', { style: m.profit < 0 ? 'color:var(--danger)' : '' }, money(m.profit)) },
      ], r.months, { footer: [h('td', null, 'Cả năm'), h('td', { class: 'num' }, money(r.totalRevenue)), h('td', { class: 'num' }, money(r.totalExpense)), h('td', { class: 'num' }, money(r.totalProfit))] }), { flush: true }),
    );
  };
  yearSel.addEventListener('change', () => void load());
  const dl = (fmt: string) => api.download(`/api/reports/revenue/export?propertyId=${PROPERTY_ID}&year=${yearSel.value}&format=${fmt}`, `doanh-thu-${yearSel.value}.${fmt}`).catch((e) => toast(errText(e), 'err'));
  view.replaceChildren(
    pageHead('Doanh thu và chi phí', 'Báo cáo theo tháng của khu trọ.', [yearSel,
      h('button', { class: 'btn secondary', onclick: () => dl('xlsx') }, icon('download', 16), 'Excel'),
      h('button', { class: 'btn secondary', onclick: () => dl('pdf') }, icon('download', 16), 'PDF')]),
    holder,
  );
  await load();
}
