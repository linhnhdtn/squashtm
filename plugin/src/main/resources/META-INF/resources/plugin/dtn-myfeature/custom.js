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
/* ------------------------------------------------------------------ *
 * Autocomplete step BDD (keyword + action word).
 * Thay cho tinh nang cua plugin premium (khong co license): endpoint
 * /backend/keyword-test-cases/autocomplete cua core nem AccessDenied vi
 * thieu ActionWordService, nen ta goi endpoint rieng cua plugin DTN.
 * ------------------------------------------------------------------ */
(function () {
  'use strict';

  const API = id => `/squash/plugin/dtn-myfeature/api/test-case/${id}/action-words`;
  const testCaseId = () => (location.pathname.match(/\/test-case\/(\d+)/) || [])[1];
  const HOST = 'sqtm-app-keyword-test-steps, sqtm-app-keyword-step';
  const esc = t => t.replace(/[<>&]/g, c => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]));
  const sleep = ms => new Promise(r => setTimeout(r, ms));

  // Trong container con co ca o search cua dropdown Given/When/Then (ant-select-selection-search-input),
  // phai loai ra neu khong go keyword cung bung goi y.
  const isActionInput = el =>
    el && el.tagName === 'INPUT' &&
    !el.classList.contains('ant-select-selection-search-input') &&
    el.closest(HOST);

  let box, input, items = [], cursor = -1, timer, eatEnterKeyup = false;

  function panel() {
    if (box) return box;
    box = document.createElement('div');
    box.id = 'dtn-aw-suggest';
    box.style.cssText =
      'position:fixed;z-index:10000;display:none;max-height:260px;overflow:auto;background:#fff;' +
      'border:1px solid #d9d9d9;border-radius:4px;box-shadow:0 2px 8px rgba(0,0,0,.15);' +
      'font:13px/1.6 "Segoe UI",sans-serif;min-width:240px';
    box.addEventListener('mousedown', e => {          // mousedown: chay truoc blur cua input
      const li = e.target.closest('[data-i]');
      if (li) { e.preventDefault(); pick(+li.dataset.i); }
    });
    document.body.appendChild(box);
    return box;
  }

  function hide() { if (box) box.style.display = 'none'; items = []; cursor = -1; }

  function render() {
    const p = panel();
    if (!items.length) return hide();
    p.innerHTML = items
      .map(({ keyword, action }, i) =>
        `<div data-i="${i}" style="padding:5px 10px;cursor:pointer;white-space:nowrap;background:${i === cursor ? '#e6f4ff' : '#fff'}">` +
        `<span style="color:#c00;font-weight:600;margin-right:6px">${esc(keyword)}</span>${esc(action)}</div>`)
      .join('');
    const r = input.getBoundingClientRect();
    p.style.left = r.left + 'px';
    p.style.top = r.bottom + 2 + 'px';
    p.style.minWidth = r.width + 'px';
    p.style.display = 'block';
  }

  /**
   * Doi dropdown keyword ben canh o input sang `kw`.
   *
   * nz-select khong cho set value tu ngoai, phai mo overlay roi click dung option.
   */
  // ponytail: so khop theo nhan hien thi (title="Given"), se hut neu keyword type duoc dich trong
  //           conf/lang; luc do doc map tu /squash/assets/sqtm-core/i18n/translations_<lang>.json.
  async function setKeyword(host, kw) {
    const current = host.querySelector('.ant-select-selection-item');
    const same = t => (t.getAttribute('title') || t.textContent).trim().toUpperCase() === kw;
    if (!current || same(current)) return;

    const selector = host.querySelector('.ant-select-selector');
    if (!selector) return;
    selector.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    selector.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    for (let i = 0; i < 30; i++) {                    // overlay render bat dong bo
      const opt = [...document.querySelectorAll('nz-option-item')].find(same);
      if (opt) { opt.dispatchEvent(new MouseEvent('click', { bubbles: true })); await sleep(60); return; }
      await sleep(30);
    }
    console.warn('[DTN] khong tim thay option keyword', kw);
  }

  async function pick(i) {
    const item = items[i];
    if (!item) return;
    const el = input, host = el.closest(HOST);
    clearTimeout(timer);                              // huy lan search dang cho
    hide();
    await setKeyword(host, item.keyword.toUpperCase());
    el.value = item.action;
    el.dispatchEvent(new Event('input', { bubbles: true }));   // Angular form nhan gia tri
    el.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true })); // Angular bind keyup.enter
    el.focus();
  }

  async function search() {
    const id = testCaseId();
    if (!id || !input || input.value.trim().length < 1) return hide();
    try {
      const r = await fetch(`${API(id)}?q=${encodeURIComponent(input.value.trim())}`, { credentials: 'same-origin' });
      if (!r.ok) return hide();
      items = await r.json();
      cursor = -1;
      render();
    } catch (_) { hide(); }
  }

  document.addEventListener('input', e => {
    if (!isActionInput(e.target)) return;
    input = e.target;
    clearTimeout(timer);
    timer = setTimeout(search, 200);
  }, true);

  document.addEventListener('keydown', e => {
    if (!items.length || e.target !== input) return;
    if (e.key === 'ArrowDown') { cursor = (cursor + 1) % items.length; }
    else if (e.key === 'ArrowUp') { cursor = (cursor - 1 + items.length) % items.length; }
    else if (e.key === 'Enter' && cursor >= 0) { eatEnterKeyup = true; pick(cursor); }
    else if (e.key === 'Escape') { hide(); }
    else return;
    e.preventDefault(); e.stopPropagation();          // chan Angular submit text tho
    if (items.length) render();
  }, true);

  /**
   * pick() tu ban keyup.enter de confirm. Keyup THAT cua chinh lan bam Enter do van no sau keydown
   * -> Angular submit lan hai khi o input da bi xoa trang -> "The action word cannot be empty".
   */
  document.addEventListener('keyup', e => {
    if (e.key === 'Enter' && eatEnterKeyup) {
      eatEnterKeyup = false;
      e.preventDefault(); e.stopPropagation();
    }
  }, true);

  document.addEventListener('focusout', e => { if (e.target === input) setTimeout(hide, 120); }, true);
  window.addEventListener('scroll', hide, true);
})();
