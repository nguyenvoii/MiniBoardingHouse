import './styles/tokens.css';
import './styles/base.css';
import './styles/home.css';

const home = document.getElementById('stayly-home')!;
const app = document.getElementById('app')!;
const homeTitle = document.title;

// Các liên kết QR được phát trước đây có thể chỉ mang query ?qr=…
if (new URLSearchParams(location.search).has('qr') && !location.hash.startsWith('#/')) {
  history.replaceState(history.state, '', '#/login');
}

let appLoaded: Promise<typeof import('./main')> | undefined;
function showRoute(): void {
  const inApp = location.hash.startsWith('#/');
  home.hidden = inApp;
  app.hidden = !inApp;
  if (inApp) {
    appLoaded ??= import('./main');
  } else {
    document.title = homeTitle;
  }
}

if (!location.hash.startsWith('#/')) document.documentElement.classList.add('m-enter');
window.addEventListener('hashchange', showRoute);
showRoute();

const steps = [
  {
    title: 'Chốt chỉ số điện nước',
    description: 'Nhập chỉ số đầu kỳ và cuối kỳ theo phòng để xác định lượng điện, nước sử dụng trong tháng.',
    rows: [['Chỉ số điện đầu kỳ', '328 kWh'], ['Chỉ số điện cuối kỳ', '374 kWh'], ['Điện tiêu thụ', '46 kWh']],
  },
  {
    title: 'Tính tiền theo hợp đồng',
    description: 'Tính tiền phòng theo số ngày thuê thực tế, cộng điện nước, dịch vụ và các khoản điều chỉnh của từng phòng.',
    rows: [['Tiền phòng', '2.300.000 ₫'], ['Điện, nước và dịch vụ', '290.000 ₫'], ['Tổng tiền', '2.590.000 ₫']],
  },
  {
    title: 'Kiểm tra và duyệt kỳ',
    description: 'Rà soát các dòng phí theo phòng, xử lý sai lệch và duyệt kỳ tính tiền trước khi phát hành hóa đơn.',
    rows: [['Kỳ tính tiền', 'Tháng 11'], ['Tổng tiền phòng P.203', '2.590.000 ₫'], ['Trạng thái kỳ', 'Đã duyệt']],
  },
  {
    title: 'Phát hành hóa đơn',
    description: 'Phát hành hóa đơn sau khi duyệt kỳ để người thuê xem từng khoản phí, hạn thanh toán và số tiền phải trả.',
    rows: [['Hóa đơn', 'Tháng 11'], ['Hạn thanh toán', '05/12'], ['Số tiền phải trả', '2.590.000 ₫']],
  },
  {
    title: 'Ghi nhận và theo dõi tiền thu',
    description: 'Ghi nhận tiền mặt hoặc đối soát chuyển khoản theo mã hóa đơn. Xem khoản đã thu và số tiền còn lại.',
    rows: [['Tiền hóa đơn', '2.590.000 ₫'], ['Đã thanh toán', '1.000.000 ₫'], ['Còn phải thu', '1.590.000 ₫']],
  },
];

const stops = Array.from(home.querySelectorAll<HTMLButtonElement>('.home-stops button'));
const stepTitle = home.querySelector<HTMLElement>('#home-step-title')!;
const stepDescription = home.querySelector<HTMLElement>('#home-step-copy')!;
const stepRows = home.querySelector<HTMLDListElement>('.home-step-preview dl')!;
stops.forEach((button, index) => {
  button.addEventListener('click', () => {
    if (button.getAttribute('aria-pressed') === 'true') return;
    const step = steps[index];
    stops.forEach((stop) => stop.setAttribute('aria-pressed', String(stop === button)));
    stepTitle.textContent = step.title;
    stepDescription.textContent = step.description;
    stepRows.replaceChildren(...step.rows.map(([label, value]) => {
      const row = document.createElement('div');
      const term = document.createElement('dt');
      const detail = document.createElement('dd');
      term.textContent = label;
      detail.textContent = value;
      row.append(term, detail);
      return row;
    }));
  });
});

const track = home.querySelector<HTMLElement>('.home-track')!;
if ('IntersectionObserver' in window && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
  const observer = new IntersectionObserver(([entry]) => {
    if (!entry.isIntersecting) return;
    track.classList.add('is-seen');
    observer.disconnect();
  }, { threshold: .45 });
  observer.observe(track);
}
