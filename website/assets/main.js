// DocuScan OCR landing — interactions

// Mobile nav drawer
const toggle = document.getElementById('navToggle');
const menu = document.getElementById('navMenu');
if (toggle && menu) {
  const setOpen = (open) => {
    menu.classList.toggle('open', open);
    menu.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
  };
  toggle.addEventListener('click', () => setOpen(!menu.classList.contains('open')));
  menu.querySelectorAll('a').forEach(a => a.addEventListener('click', () => setOpen(false)));
}

// Nav shadow on scroll
const nav = document.getElementById('nav');
const onScroll = () => nav && nav.classList.toggle('scrolled', window.scrollY > 8);
onScroll();
window.addEventListener('scroll', onScroll, { passive: true });
