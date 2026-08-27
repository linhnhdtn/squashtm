/* custom.js — chay tren MOI trang cua Squash TM (duoc chen vao index.html) */
(function () {
  'use strict';
  console.log('[DTN] custom.js loaded on', location.pathname);

  // 1) Vi du: nhan biet dang o workspace nao
  const workspace = () => (location.pathname.match(/\/squash\/([a-z-]+)/) || [])[1] || '?';

  // 2) Vi du: gan badge "DTN" canh logo, chiu duoc SPA re-render (dung MutationObserver)
  function addBadge() {
    const nav = document.querySelector('nz-avatar, .sqtm-nav-bar, ul');
    if (!nav || document.getElementById('dtn-badge')) return;
    const b = document.createElement('div');
    b.id = 'dtn-badge';
    b.textContent = 'DTN';
    b.style.cssText =
      'position:fixed;left:8px;bottom:120px;z-index:9999;background:#12b886;color:#fff;' +
      'font:11px/1 "Segoe UI",sans-serif;padding:5px 8px;border-radius:3px;letter-spacing:.06em;cursor:pointer';
    b.title = 'DTN custom script dang chay — click de xem summary';
    b.onclick = async () => {
      const r = await fetch('/squash/plugin/dtn-myfeature/api/summary', { credentials: 'same-origin' });
      console.log('[DTN] summary', r.ok ? await r.json() : 'HTTP ' + r.status);
      alert('Xem console: [DTN] summary (workspace hien tai: ' + workspace() + ')');
    };
    document.body.appendChild(b);
  }

  const start = () => {
    addBadge();
    new MutationObserver(addBadge).observe(document.body, { childList: true, subtree: true });
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
