import { h } from '../lib/dom';
import { icon } from '../lib/icons';
import { api, Page } from '../lib/api';
import { dateTime } from '../lib/format';
import type { Notification } from '../data/types';

/** Chuông thông báo cho nhân viên: số chưa đọc, danh sách 8 thông báo mới nhất, đánh dấu đã đọc. */
export function notificationBell(): HTMLElement {
  const dot = h('span', { class: 'dot', hidden: true }, '0');
  const btn = h('button', { class: 'icon-btn', 'aria-label': 'Thông báo', 'aria-haspopup': 'true', 'aria-expanded': 'false' }, icon('bell'), dot);
  const pop = h('div', { class: 'bell-pop', role: 'dialog', 'aria-label': 'Thông báo', hidden: true });
  const wrap = h('div', { style: 'position:relative' }, btn, pop);

  const refreshCount = async () => {
    try {
      const r = await api.get<{ unread: number }>('/api/notifications/unread-count');
      dot.textContent = r.unread > 99 ? '99+' : String(r.unread);
      dot.hidden = r.unread === 0;
    } catch { /* không có quyền hoặc mất mạng: ẩn chấm */ }
  };
  const paint = async () => {
    pop.replaceChildren(h('div', { class: 'panel-body' }, h('div', { class: 'skel', style: 'height:16px;margin:10px 0' }), h('div', { class: 'skel', style: 'height:16px;margin:10px 0' })));
    try {
      const r = await api.get<Page<Notification>>('/api/notifications?size=8');
      pop.replaceChildren(
        h('div', { class: 'bell-head' }, h('b', null, 'Thông báo'), h('button', { class: 'link-btn', onclick: async () => { await api.patch('/api/notifications/read-all'); await paint(); void refreshCount(); } }, 'Đánh dấu đã đọc hết')),
        r.items.length
          ? h('ul', null, ...r.items.map((n) => h('li', { class: `bell-item ${n.isRead ? '' : 'unread'}` },
              h('b', null, n.title), h('span', null, n.content), h('small', null, dateTime(n.createdAt)))))
          : h('p', { class: 'muted', style: 'padding:18px' }, 'Chưa có thông báo nào.'),
      );
    } catch {
      pop.replaceChildren(h('p', { class: 'muted', style: 'padding:18px' }, 'Chưa tải được thông báo.'));
    }
  };
  const close = () => { pop.hidden = true; btn.setAttribute('aria-expanded', 'false'); };
  btn.addEventListener('click', async (e) => {
    e.stopPropagation();
    if (pop.hidden) { pop.hidden = false; btn.setAttribute('aria-expanded', 'true'); await paint(); } else close();
  });
  document.addEventListener('click', (e) => { if (!wrap.contains(e.target as Node)) close(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
  void refreshCount();
  setInterval(() => void refreshCount(), 60000);
  return wrap;
}
