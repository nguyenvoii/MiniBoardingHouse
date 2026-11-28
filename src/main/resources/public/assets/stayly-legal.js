const contents = document.querySelector('.legal-index details');
const summary = contents.querySelector('summary');
const headings = [...document.querySelectorAll('.legal-article h2[id]')];
const links = [...contents.querySelectorAll('a')];
const progress = document.querySelector('.legal-progress');
const header = document.querySelector('.legal-header');
const compact = matchMedia('(max-width: 760px)');
let active = -1;
let frame = 0;

function updateContents() {
  contents.open = !compact.matches;
  summary.tabIndex = compact.matches ? 0 : -1;
}

function updateReadingPosition() {
  frame = 0;
  const threshold = header.offsetHeight + 30;
  const available = document.documentElement.scrollHeight - innerHeight;
  const fraction = available > 0 ? Math.min(1, scrollY / available) : 0;
  progress.style.transform = `scaleX(${fraction})`;

  let next = headings.findLastIndex((heading) => heading.getBoundingClientRect().top <= threshold);
  if (fraction >= .995) next = headings.length - 1;
  if (next === active) return;
  links[active]?.removeAttribute('aria-current');
  links[next]?.setAttribute('aria-current', 'location');
  active = next;
}

function scheduleUpdate() {
  if (!frame) frame = requestAnimationFrame(updateReadingPosition);
}

links.forEach((link, index) => {
  headings[index].tabIndex = -1;
  link.addEventListener('click', (event) => {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    if (compact.matches) contents.open = false;
    headings[index].focus({ preventScroll: true });
  });
});

updateContents();
compact.addEventListener('change', updateContents);
window.addEventListener('scroll', scheduleUpdate, { passive: true });
window.addEventListener('resize', scheduleUpdate);
contents.addEventListener('toggle', scheduleUpdate);
document.fonts.ready.then(scheduleUpdate);
updateReadingPosition();
