/* custom.js -- runs on EVERY Squash TM page (injected into index.html) */
(function () {
  'use strict';
  console.log('[DTN] custom.js loaded on', location.pathname);

  // 1) Example: tell which workspace we are on
  const workspace = () => (location.pathname.match(/\/squash\/([a-z-]+)/) || [])[1] || '?';

  // 2) Example: pin a "DTN" badge next to the logo, surviving SPA re-renders (MutationObserver)
  function addBadge() {
    const nav = document.querySelector('nz-avatar, .sqtm-nav-bar, ul');
    if (!nav || document.getElementById('dtn-badge')) return;
    const b = document.createElement('div');
    b.id = 'dtn-badge';
    b.textContent = 'DTN';
    b.style.cssText =
      'position:fixed;left:8px;bottom:120px;z-index:9999;background:#12b886;color:#fff;' +
      'font:11px/1 "Segoe UI",sans-serif;padding:5px 8px;border-radius:3px;letter-spacing:.06em;cursor:pointer';
    b.title = 'DTN custom script is running -- click for the summary';
    b.onclick = async () => {
      const r = await fetch('/squash/plugin/dtn-myfeature/api/summary', { credentials: 'same-origin' });
      console.log('[DTN] summary', r.ok ? await r.json() : 'HTTP ' + r.status);
      alert('See the console: [DTN] summary (current workspace: ' + workspace() + ')');
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
 * BDD step autocomplete (keyword + action word).
 * Stands in for the premium plugin we have no license for: the core
 * /backend/keyword-test-cases/autocomplete endpoint throws AccessDenied
 * without ActionWordService, so this calls the DTN plugin's own endpoint.
 * ------------------------------------------------------------------ */
(function () {
  'use strict';

  const API = id => `/squash/plugin/dtn-myfeature/api/test-case/${id}/action-words`;
  const testCaseId = () => (location.pathname.match(/\/test-case\/(\d+)/) || [])[1];
  const HOST = 'sqtm-app-keyword-test-steps, sqtm-app-keyword-step';
  const esc = t => t.replace(/[<>&]/g, c => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]));
  const sleep = ms => new Promise(r => setTimeout(r, ms));

  // The same container also holds the search input of the Given/When/Then dropdown
  // (ant-select-selection-search-input); without excluding it, typing a keyword
  // would pop the suggestions up as well.
  const isActionInput = el =>
    el && el.tagName === 'INPUT' &&
    !el.classList.contains('ant-select-selection-search-input') &&
    el.closest(HOST);

  let box, input, items = [], cursor = -1, timer, eatEnterKeyup = false, seq = 0;

  function panel() {
    if (box) return box;
    box = document.createElement('div');
    box.id = 'dtn-aw-suggest';
    box.style.cssText =
      'position:fixed;z-index:10000;display:none;max-height:260px;overflow:auto;background:#fff;' +
      'border:1px solid #d9d9d9;border-radius:4px;box-shadow:0 2px 8px rgba(0,0,0,.15);' +
      'font:13px/1.6 "Segoe UI",sans-serif;min-width:240px';
    box.addEventListener('mousedown', e => {          // mousedown fires before the input blurs
      const li = e.target.closest('[data-i]');
      e.preventDefault();                             // keep focus on the input, scrollbar drags included
      if (li) pick(+li.dataset.i);
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
    if (cursor >= 0) p.children[cursor].scrollIntoView({ block: 'nearest' });
  }

  /**
   * Switches the keyword dropdown next to the input over to `kw`.
   *
   * nz-select refuses a value set from outside, so the overlay has to be opened and the right
   * option clicked.
   */
  // ponytail: matches on the displayed label (title="Given"), which breaks once the keyword types
  //           are translated in conf/lang; read the map from
  //           /squash/assets/sqtm-core/i18n/translations_<lang>.json instead.
  async function setKeyword(host, kw) {
    const current = host.querySelector('.ant-select-selection-item');
    const same = t => (t.getAttribute('title') || t.textContent).trim().toUpperCase() === kw;
    if (!current || same(current)) return;

    const selector = host.querySelector('.ant-select-selector');
    if (!selector) return;
    selector.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    selector.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    for (let i = 0; i < 30; i++) {                    // the overlay renders asynchronously
      const opt = [...document.querySelectorAll('nz-option-item')].find(same);
      if (opt) { opt.dispatchEvent(new MouseEvent('click', { bubbles: true })); await sleep(60); return; }
      await sleep(30);
    }
    console.warn('[DTN] keyword option not found', kw);
  }

  async function pick(i) {
    const item = items[i];
    if (!item) return;
    const el = input, host = el.closest(HOST);
    clearTimeout(timer);                              // drop the search that is still pending
    hide();
    await setKeyword(host, item.keyword.toUpperCase());
    el.value = item.action;
    el.dispatchEvent(new Event('input', { bubbles: true }));   // let the Angular form pick the value up
    el.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true })); // Angular bind keyup.enter
    el.focus();
  }

  async function search() {
    const id = testCaseId();
    if (!id || !input || input.value.trim().length < 1) return hide();
    // A fetch is not cancelled when the next keystroke arrives, so responses can come back out
    // of order: ignore anything that is not the answer to the latest search.
    const mine = ++seq;
    try {
      const r = await fetch(`${API(id)}?q=${encodeURIComponent(input.value.trim())}`, { credentials: 'same-origin' });
      if (mine !== seq) return;
      if (!r.ok) return hide();
      const data = await r.json();
      if (mine !== seq) return;
      items = data;
      cursor = -1;
      render();
    } catch (_) { if (mine === seq) hide(); }
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
    e.preventDefault(); e.stopPropagation();          // stop Angular submitting the raw text
    if (items.length) render();
  }, true);

  /**
   * pick() fires its own keyup.enter to confirm. The REAL keyup of that same Enter press still
   * arrives after the keydown -> Angular submits a second time once the input has been cleared
   * -> "The action word cannot be empty".
   */
  document.addEventListener('keyup', e => {
    if (e.key === 'Enter' && eatEnterKeyup) {
      eatEnterKeyup = false;
      e.preventDefault(); e.stopPropagation();
    }
  }, true);

  // Angular reuses the input (after adding a step, for one), which makes it focus out and straight
  // back in. Without the activeElement check this delayed hide() would wipe the list that the
  // following keystrokes had just brought up.
  document.addEventListener('focusout', e => {
    if (e.target === input) setTimeout(() => { if (document.activeElement !== input) hide(); }, 120);
  }, true);
  // Capture phase, so a scroll inside the panel itself lands here too: without the exclusion one
  // wheel tick closes the list and its lower rows stay unreachable.
  window.addEventListener('scroll', e => { if (!box || !box.contains(e.target)) hide(); }, true);
})();

/* ------------------------------------------------------------------ *
 * The "Iteration report" page, shown INSIDE Squash's real chrome.
 *
 * Squash offers no extension point for the main nav bar, and a route of our
 * own (/dtn-dashboard and the like) has no Angular match -> it redirects
 * back to /squash/. So: park the page on a real SPA route and lay an iframe
 * over everything right of the nav bar. A fragment survives on
 * /home-workspace (every other workspace redirects to its first node and
 * loses the fragment -- tried it, /home-workspace is the only one that
 * works).
 *
 * What this buys: the REAL sqtm-core nav bar (theme, i18n, avatar,
 * Administration, Milestones...), a linkable URL, and a working F5 and Back
 * button. What it saves: patching and rebuilding the Angular frontend (make
 * front, ~5 minutes, with the patch to re-apply in 5 places on every Squash
 * upgrade).
 *
 * ponytail: /home-workspace still renders behind the iframe (hidden, and it
 *           costs one dashboard fetch). For something cleaner, add a real
 *           Angular route under front-patch/.
 * ------------------------------------------------------------------ */
(function () {
  'use strict';

  const ID = 'dtn-iteration-report-link';
  const FRAME = 'dtn-iteration-report-frame';
  const HOST = '/squash/home-workspace';
  const HASH = '#dtn-iteration-report';
  const PAGE = '/squash/plugin/dtn-myfeature/iteration-report';
  const LABEL = 'Iteration report';
  // Clone the Executions item (its icon fits, the report is about runs) and insert it after Reporting.
  const MODEL = 'li[data-test-element-id="campaign-link"]';
  const ANCHOR = 'li[data-test-element-id="custom-report-link"]';

  const active = () => location.pathname === HOST && location.hash === HASH;

  function navItem() {
    const existing = document.getElementById(ID);
    if (existing) return existing;

    const model = document.querySelector(MODEL);
    if (!model) return null;
    const li = model.cloneNode(true);
    li.id = ID;
    li.setAttribute('data-test-element-id', ID);
    // the clone carries the source item's "selected" state along -- drop it
    li.classList.remove('ant-menu-item-selected');

    const a = li.querySelector('a.nav-bar-title');
    if (!a) return null;
    a.setAttribute('href', HOST + HASH);
    a.setAttribute('title', LABEL);   // collapsed nav bar: no Angular tooltip binding here
    const span = a.querySelector('span');
    if (span) span.textContent = LABEL;

    (document.querySelector(ANCHOR) || model).after(li);
    return li;
  }

  function sync() {
    const li = navItem();
    const on = active();
    if (li) li.classList.toggle('ant-menu-item-selected', on);

    let frame = document.getElementById(FRAME);
    if (!on) {
      if (frame) frame.remove();
      return;
    }

    if (!frame) {
      // The wrapping div is required: an iframe is a replaced element, so its `width:auto` falls
      // back to the intrinsic 300x150 of the UA stylesheet instead of being derived from
      // left/right -- putting the inset on the iframe itself leaves a 300px frame. The div takes
      // the inset, the iframe fills 100% of it.
      frame = document.createElement('div');
      frame.id = FRAME;
      frame.style.cssText = 'position:fixed;top:0;right:0;bottom:0;z-index:900;background:#f0f2f5';
      const inner = document.createElement('iframe');
      inner.src = PAGE;
      inner.title = LABEL;
      inner.style.cssText = 'display:block;width:100%;height:100%;border:0';
      frame.appendChild(inner);
      document.body.appendChild(frame);
    }
    // the nav bar can collapse and expand -> track its right edge
    const nav = document.querySelector('sqtm-core-nav-bar');
    const left = (nav ? Math.round(nav.getBoundingClientRect().right) : 0) + 'px';
    if (frame.style.left !== left) frame.style.left = left;
  }

  // Angular re-renders the content area on a workspace change, so the nav item has to be
  // re-attached and the overlay re-evaluated. sync() only writes when a value actually differs, so
  // the mutations it causes itself do not loop forever.
  const start = () => {
    sync();
    new MutationObserver(sync).observe(document.body, { childList: true, subtree: true });
    addEventListener('hashchange', sync);
    addEventListener('popstate', sync);
    addEventListener('resize', sync);
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
