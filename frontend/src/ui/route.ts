import { h } from '../lib/dom';
import { icon } from '../lib/icons';

export interface Stop {
  name: string;
  sub: string;
  state: 'done' | 'current' | 'todo';
  href?: string;
  /** Số phòng còn kẹt ở trạm này: hiện trên biển vàng của trạm hiện tại. */
  stuck?: number;
  stuckLabel?: string;
}

/** Tuyến tháng: các trạm nối bằng đường tuyến, trạm hiện tại là biển vàng. */
export function routeLine(stops: Stop[]): HTMLElement {
  const line = h('ol', { class: 'route-line', style: `--stops:${stops.length}`, 'aria-label': 'Các bước chốt tiền tháng' });
  stops.forEach((s, i) => {
    const node = h('span', { class: 'node' }, s.state === 'done' ? icon('check', 17) : s.state === 'current' && s.stuck ? h('span', { class: 'stuck' }, String(s.stuck), h('small', null, s.stuckLabel ?? 'phòng kẹt')) : String(i + 1));
    const inner = [node, h('span', { class: 'name' }, s.name), h('span', { class: 'sub' }, s.sub)];
    const content = s.href ? h('a', { href: s.href }, ...inner) : h('div', { style: 'display:contents' }, ...inner);
    line.appendChild(h('li', { class: `stop ${s.state}`, style: `--i:${i}`, 'aria-current': s.state === 'current' ? 'step' : null }, content));
  });
  return line;
}
