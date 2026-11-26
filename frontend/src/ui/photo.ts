// Xem ảnh nằm sau API cần token (ảnh công tơ, hiện trạng phòng, sự cố): tải bằng token rồi gắn vào <img>.
import { h } from '../lib/dom';
import { api } from '../lib/api';
import { openDialog } from './kit';

export function showPhotos(title: string, items: Array<{ label: string; url: string }>): void {
  const d = openDialog(title, { width: 560 });
  d.foot.append(h('button', { class: 'btn', onclick: d.close }, 'Đóng'));
  if (!items.length) { d.body.append(h('p', { class: 'muted' }, 'Chưa có ảnh.')); return; }
  for (const it of items) {
    const slot = h('div', { class: 'muted' }, 'Đang tải ảnh…');
    d.body.append(h('figure', { style: 'margin:0 0 12px' }, slot, h('figcaption', { class: 'muted' }, it.label)));
    void api.blobUrl(it.url).then((src) => slot.replaceWith(src ? h('img', { src, alt: it.label, style: 'max-width:100%;border:1px solid var(--line);border-radius:8px' }) : h('span', { class: 'muted' }, 'Không mở được ảnh (hết quyền hoặc tệp đã mất).')));
  }
}
