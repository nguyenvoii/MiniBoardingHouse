import qrcode from 'qrcode-generator';
import { h } from '../lib/dom';

/** Vẽ mã QR (SVG) của một đoạn văn bản, nền trắng để máy quét đọc được cả ở giao diện tối. */
export function qrImage(text: string, label: string): HTMLElement {
  const qr = qrcode(0, 'M'); // kiểu 0: tự chọn cỡ nhỏ nhất đủ chứa
  qr.addData(text);
  qr.make();
  const box = h('div', { role: 'img', 'aria-label': label, style: 'background:#fff;padding:8px;border-radius:8px;width:220px;max-width:100%;align-self:center' });
  box.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true }); // SVG do thư viện sinh từ dữ liệu, không chèn chuỗi của người dùng
  return box;
}
