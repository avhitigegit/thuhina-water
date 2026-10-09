/* Thuhina Water prototype – small UI helpers shared by every screen. */
(function (global) {
  'use strict';
  var TW = global.TW;
  var D = TW.D, Q = TW.Q;

  var UI = {};
  UI.$ = function (sel, root) { return (root || document).querySelector(sel); };
  UI.$$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };
  UI.esc = function (s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; });
  };
  UI.money = function (n) { return TW.money(n); };
  UI.lkr = function (n) { return 'Rs. ' + Number(n || 0).toLocaleString('en-US', { maximumFractionDigits: 0 }); };
  UI.num = function (n) { return Number(n || 0).toLocaleString('en-US'); };
  UI.dmy = D.dmy;
  UI.dmyt = D.dmyt;
  UI.params = function () { var p = {}; new URLSearchParams(location.search).forEach(function (v, k) { p[k] = v; }); return p; };

  UI.badge = function (text, kind) { return '<span class="badge ' + (kind || '') + '">' + UI.esc(text) + '</span>'; };
  UI.statusBadge = function (s) {
    var map = {
      Active: 'ok', Inactive: '', Draft: '', Approved: 'info', Sent: 'info', 'Partly received': 'warn', Received: 'ok', Cancelled: 'bad',
      Open: 'info', 'Quotes received': 'warn', Selected: 'ok', Closed: '', Paid: 'ok', 'Part paid': 'warn', Unpaid: 'bad',
      'At factory': 'warn', 'Partly returned': 'warn', Returned: 'ok', Cash: '', Credit: 'info', 'Monthly bill': 'dark',
      Company: 'warn', Customer: 'bad', Overdue: 'bad', Entered: 'ok', Due: 'info',
      Accepted: 'ok', Rejected: 'bad', Expired: 'warn', Agreed: 'info', Standard: ''
    };
    return UI.badge(s, map[s] || '');
  };
  UI.kindBadge = function (k) {
    var map = { first: 'info', exchange: '', leftdoor: 'warn', collect: 'ok', extra: 'info', product: '', custdamage: 'bad', owedsettle: 'ok', opening: 'dark', reversal: 'bad' };
    return UI.badge(TW.KINDS[k] || k, map[k] || '');
  };

  /* ---------- toast & modal ---------- */
  UI.toast = function (msg, kind) {
    var host = document.getElementById('toast-host');
    if (!host) { host = document.createElement('div'); host.id = 'toast-host'; document.body.appendChild(host); }
    var t = document.createElement('div');
    t.className = 'toast ' + (kind || '');
    t.textContent = msg;
    host.appendChild(t);
    setTimeout(function () { t.remove(); }, kind === 'bad' ? 6000 : 3500);
  };
  UI.modal = function (opts) {
    var back = document.createElement('div');
    back.className = 'modal-back';
    // The body is a <form> so radio groups and fields are scoped to this popup.
    back.innerHTML = '<div class="modal ' + (opts.wide ? 'wide' : '') + '" role="dialog" aria-modal="true"><header><h2>' + UI.esc(opts.title) + '</h2><button class="x" type="button" aria-label="Close">&times;</button></header><form class="body" novalidate onsubmit="return false"></form><footer></footer></div>';
    var body = back.querySelector('.body');
    if (typeof opts.body === 'string') body.innerHTML = opts.body; else if (opts.body) body.appendChild(opts.body);
    var foot = back.querySelector('footer');
    function close() { back.remove(); document.removeEventListener('keydown', onKey); if (opts.onClose) opts.onClose(); }
    function onKey(e) { if (e.key === 'Escape') close(); }
    (opts.buttons || [{ label: 'Close' }]).forEach(function (b) {
      var btn = document.createElement('button');
      btn.className = 'btn ' + (b.cls || '');
      btn.textContent = b.label;
      btn.addEventListener('click', function () { if (b.onClick) { if (b.onClick(close, back) === false) return; } else close(); });
      foot.appendChild(btn);
    });
    back.querySelector('.x').addEventListener('click', close);
    back.addEventListener('mousedown', function (e) { if (e.target === back) close(); });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(back);
    var first = back.querySelector('input, select, textarea');
    if (first) first.focus(); else foot.lastChild && foot.lastChild.focus();
    return { el: back, close: close };
  };
  UI.confirm = function (title, html, okLabel, cls) {
    return new Promise(function (resolve) {
      var done = false;
      UI.modal({
        title: title, body: html,
        onClose: function () { if (!done) resolve(false); },
        buttons: [
          { label: 'Cancel' },
          { label: okLabel || 'Confirm', cls: cls || 'primary', onClick: function (close) { done = true; close(); resolve(true); } }
        ]
      });
    });
  };
  /* Ask for a reason (used for reversals, cancellations, deactivation). */
  UI.prompt = function (title, label, okLabel, cls) {
    return new Promise(function (resolve) {
      var done = false;
      UI.modal({
        title: title,
        body: '<div class="field"><label>' + UI.esc(label) + '</label><textarea id="ui-prompt-text" rows="3"></textarea></div>',
        onClose: function () { if (!done) resolve(null); },
        buttons: [
          { label: 'Cancel' },
          { label: okLabel || 'OK', cls: cls || 'primary', onClick: function (close, el) {
            var v = el.querySelector('#ui-prompt-text').value.trim();
            if (!v) { el.querySelector('#ui-prompt-text').classList.add('invalid'); return false; }
            done = true; close(); resolve(v);
          } }
        ]
      });
    });
  };

  /* Show the result of an Ops call; returns true when ok. */
  UI.result = function (r, okMsg) {
    if (r && r.ok) { if (okMsg) UI.toast(okMsg, 'ok'); return true; }
    UI.toast(r ? r.error : 'Something went wrong', 'bad');
    return false;
  };
  /* Credit-limit gate (BR-09): offers Admin approval and retries. */
  UI.withCreditApproval = function (run, okMsg, after) {
    var r = run(false);
    if (r.code === 'CREDIT_LIMIT') {
      var isAdmin = TW.currentUser().role === 'Admin';
      UI.modal({
        title: 'Credit limit exceeded',
        body: '<div class="banner bad">' + UI.esc(r.error) + '</div><div class="summary"><span>Credit limit</span><b class="num">' + UI.money(r.extra.limit) + '</b><span>Current balance</span><b class="num">' + UI.money(r.extra.balance) + '</b><span>Balance after this entry</span><b class="num">' + UI.money(r.extra.after) + '</b></div>' +
          (isAdmin ? '<p class="small muted" style="margin-top:10px">As Admin you can approve this sale. The approval is recorded in the audit log.</p>' : '<p class="small muted">Only an Admin can approve a sale over the credit limit.</p>'),
        buttons: [{ label: 'Cancel' }].concat(isAdmin ? [{ label: 'Approve and save', cls: 'primary', onClick: function (close) { close(); var r2 = run(true); if (UI.result(r2, okMsg) && after) after(r2); } }] : [])
      });
      return r;
    }
    if (UI.result(r, okMsg) && after) after(r);
    return r;
  };

  /* ---------- tables ---------- */
  UI.table = function (el, opts) {
    var cols = opts.columns, rows = opts.rows || [];
    var h = '<div class="table-wrap"><table' + (opts.id ? ' id="' + opts.id + '"' : '') + '><thead><tr>';
    cols.forEach(function (c) { h += '<th class="' + (c.cls || '') + '">' + (c.h || '') + '</th>'; });
    h += '</tr></thead><tbody>';
    if (!rows.length) h += '<tr><td colspan="' + cols.length + '" class="empty-state">' + (opts.empty || 'No records found.') + '</td></tr>';
    rows.forEach(function (r, i) {
      h += '<tr' + (opts.rowClass ? ' class="' + (opts.rowClass(r) || '') + '"' : '') + (opts.rowAttr ? ' ' + opts.rowAttr(r, i) : '') + '>';
      cols.forEach(function (c) {
        var v = c.f ? c.f(r, i) : UI.esc(r[c.k]);
        h += '<td class="' + (c.cls || '') + '">' + (v == null ? '' : v) + '</td>';
      });
      h += '</tr>';
    });
    h += '</tbody>';
    if (opts.foot) {
      h += '<tfoot><tr>';
      cols.forEach(function (c, i) { var v = opts.foot[i]; h += '<td class="' + (c.cls || '') + '">' + (v == null ? '' : v) + '</td>'; });
      h += '</tr></tfoot>';
    }
    h += '</table></div>';
    if (typeof el === 'string') el = UI.$(el);
    el.innerHTML = h;
    return el;
  };
  UI.sum = function (rows, f) { var s = 0; rows.forEach(function (r) { s += +f(r) || 0; }); return s; };

  /* ---------- forms ---------- */
  UI.formData = function (form) {
    var o = {};
    UI.$$('input, select, textarea', form).forEach(function (el) {
      if (!el.name) return;
      if (el.type === 'checkbox') o[el.name] = el.checked;
      else if (el.type === 'radio') { if (el.checked) o[el.name] = el.value; }
      else o[el.name] = el.value;
    });
    return o;
  };
  /* Form field by name (avoids clashes with form.id / form.name properties). */
  UI.fld = function (form, name) { return form.elements.namedItem(name); };
  /* Date fields use DD/MM/YYYY text (NFR-07). */
  UI.readDate = function (input) {
    var iso = D.fromDmy(input.value);
    input.classList.toggle('invalid', !iso);
    return iso;
  };
  UI.dateInput = function (name, iso, attrs) {
    return '<input type="text" name="' + name + '" value="' + (iso ? D.dmy(iso) : '') + '" placeholder="DD/MM/YYYY" maxlength="10" inputmode="numeric" class="date" ' + (attrs || '') + '>';
  };
  /* ---------- Date picker (all fields with class "date"; shows DD/MM/YYYY) ---------- */
  var dp = null, dpInput = null, dpMonth = null;
  function dpClose() { if (dp) { dp.remove(); dp = null; dpInput = null; } }
  function dpRender() {
    var sel = D.fromDmy(dpInput.value), td = TW.today();
    var y = +dpMonth.slice(0, 4), m = +dpMonth.slice(5, 7);
    var first = dpMonth + '-01', startDow = (D.dow(first) + 6) % 7; // Monday first
    var days = +D.monthEnd(dpMonth).slice(8);
    var h = '<div class="dp-head"><button type="button" data-nav="-1">‹</button><b>' + D.MONTHS[m - 1] + ' ' + y + '</b><button type="button" data-nav="1">›</button></div><div class="dp-grid">';
    ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'].forEach(function (d) { h += '<span class="dp-dow">' + d + '</span>'; });
    for (var i = 0; i < startDow; i++) h += '<span></span>';
    for (var d = 1; d <= days; d++) {
      var iso = dpMonth + '-' + (d < 10 ? '0' : '') + d;
      h += '<button type="button" data-day="' + iso + '" class="' + (iso === sel ? 'sel ' : '') + (iso === td ? 'today' : '') + '">' + d + '</button>';
    }
    h += '</div><div class="dp-foot"><button type="button" data-day="' + td + '">Today</button><button type="button" data-clear>Clear</button></div>';
    dp.innerHTML = h;
  }
  /* Keep the calendar under its field (also while the page or a popup scrolls). */
  function dpPlace() {
    if (!dp || !dpInput) return;
    var r = dpInput.getBoundingClientRect();
    if (r.bottom < 0 || r.top > window.innerHeight) { dpClose(); return; }
    var top = r.bottom + 4, left = r.left;
    if (top + 290 > window.innerHeight) top = Math.max(4, r.top - 294);
    if (left + 250 > window.innerWidth) left = window.innerWidth - 254;
    dp.style.top = top + 'px';
    dp.style.left = left + 'px';
  }
  function dpOpen(input) {
    if (dpInput === input) return;
    dpClose();
    dpInput = input;
    dpMonth = (D.fromDmy(input.value) || TW.today()).slice(0, 7);
    dp = document.createElement('div');
    dp.className = 'dp';
    document.body.appendChild(dp);
    dpRender();
    dpPlace();
    dp.addEventListener('mousedown', function (e) {
      e.preventDefault(); // keep focus in the input
      var t = e.target;
      if (t.hasAttribute('data-nav')) {
        var p = dpMonth.split('-'), mm = +p[1] + (+t.getAttribute('data-nav'));
        var yy = +p[0] + (mm > 12 ? 1 : mm < 1 ? -1 : 0);
        mm = mm > 12 ? 1 : mm < 1 ? 12 : mm;
        dpMonth = yy + '-' + (mm < 10 ? '0' : '') + mm;
        dpRender();
      } else if (t.hasAttribute('data-day') || t.hasAttribute('data-clear')) {
        var inp = dpInput;
        inp.value = t.hasAttribute('data-clear') ? '' : D.dmy(t.getAttribute('data-day'));
        inp.classList.remove('invalid');
        dpClose();
        inp.dispatchEvent(new Event('input', { bubbles: true }));
        inp.dispatchEvent(new Event('change', { bubbles: true }));
      }
    });
  }
  document.addEventListener('focusin', function (e) { if (e.target.classList && e.target.classList.contains('date') && !e.target.disabled && !e.target.readOnly) dpOpen(e.target); });
  document.addEventListener('click', function (e) { if (e.target.classList && e.target.classList.contains('date') && !e.target.disabled && !e.target.readOnly) dpOpen(e.target); });
  document.addEventListener('focusout', function (e) { if (e.target === dpInput) setTimeout(function () { if (dpInput === e.target && document.activeElement !== e.target) dpClose(); }, 120); });
  document.addEventListener('keydown', function (e) { if (dp && (e.key === 'Escape' || e.key === 'Tab' || e.key === 'Enter')) dpClose(); }, true);
  window.addEventListener('scroll', dpPlace, true);
  window.addEventListener('resize', dpClose);
  document.addEventListener('input', function (e) { if (dp && e.target === dpInput) { var iso = D.fromDmy(dpInput.value); if (iso) { dpMonth = iso.slice(0, 7); dpRender(); } } });

  /* Auto-insert slashes while typing a date. */
  document.addEventListener('input', function (e) {
    var el = e.target;
    if (!el.classList || !el.classList.contains('date') || e.inputType === 'deleteContentBackward') return;
    var v = el.value.replace(/[^\d]/g, '').slice(0, 8);
    if (v.length >= 5) v = v.slice(0, 2) + '/' + v.slice(2, 4) + '/' + v.slice(4);
    else if (v.length >= 3) v = v.slice(0, 2) + '/' + v.slice(2);
    el.value = v;
  });

  UI.options = function (list, selected, opts) {
    opts = opts || {};
    var h = opts.blank != null ? '<option value="">' + UI.esc(opts.blank) + '</option>' : '';
    list.forEach(function (it) {
      var v = typeof it === 'object' ? it.v : it, l = typeof it === 'object' ? it.l : it;
      h += '<option value="' + UI.esc(v) + '"' + (String(v) === String(selected) ? ' selected' : '') + '>' + UI.esc(l) + '</option>';
    });
    return h;
  };
  UI.btOptions = function (selected, all) {
    return UI.options((all ? TW.db.bottleTypes : Q.activeBottles()).map(function (b) { return { v: b.id, l: b.name }; }), selected);
  };

  /* Customer picker: datalist "C0012 – Name – phone". Accepts code, phone or exact name. */
  UI.custLabel = function (c) { return c.id + ' – ' + c.name + ' – ' + c.phone; };
  UI.custDatalist = function (id, filter) {
    var h = '<datalist id="' + id + '">';
    TW.db.customers.forEach(function (c) { if (!filter || filter(c)) h += '<option value="' + UI.esc(UI.custLabel(c)) + '">'; });
    return h + '</datalist>';
  };
  UI.findCustomer = function (text) {
    if (!text) return null;
    var t = text.trim();
    var m = /^(C\d{4})\b/i.exec(t);
    if (m) return Q.customer(m[1].toUpperCase()) || null;
    var digits = t.replace(/\D/g, '');
    if (digits.length >= 9) {
      var byPhone = TW.db.customers.filter(function (c) { return c.phone.replace(/\D/g, '') === digits; })[0];
      if (byPhone) return byPhone;
    }
    if (/^\d{1,4}$/.test(t)) { var id = 'C' + ('0000' + t).slice(-4); return Q.customer(id) || null; }
    var lower = t.toLowerCase();
    return TW.db.customers.filter(function (c) { return c.name.toLowerCase() === lower; })[0] || null;
  };

  /* Customer balance card – used on sales screens. `after` shows projected values. */
  UI.custCard = function (c, after) {
    if (!c) return '<p class="muted">Select a customer to see bottle balances.</p>';
    var bal = Q.balance(c.id);
    var h = '<div class="card-head"><div><h3 style="margin:0">' + App.link('master-data/customers', { id: c.id }, UI.esc(c.name)) + '</h3><div class="small muted">' + c.id + ' · ' + UI.esc(c.type) + ' · ' + UI.esc(c.area) + ' · ' + UI.esc(c.phone) + '</div></div>' +
      '<div>' + UI.statusBadge(c.payType) + (c.status !== 'Active' ? ' ' + UI.badge('Inactive', 'bad') : '') + '</div></div>';
    h += '<div class="balances">';
    Q.activeBottles().forEach(function (b) {
      ['held', 'toCollect', 'owed'].forEach(function (f) {
        var v = c[f][b.id] || 0;
        var a = after && after[f] ? (after[f][b.id] || 0) : v;
        if (!v && !a && f !== 'held' && b.id !== 'B20') return;
        if (f === 'held' && !v && !a && b.id !== 'B20') return;
        var label = { held: 'Bottles held', toCollect: 'Empties to collect', owed: 'Bottles owed' }[f] + ' (' + b.litres + 'L)';
        var cls = f === 'owed' && a > 0 ? 'bal bad' : f === 'toCollect' && a > 0 ? 'bal hl' : 'bal';
        var delta = a - v;
        h += '<div class="' + cls + '"><div class="label">' + label + '</div><div class="value">' + a + '</div>' +
          (delta ? '<div class="delta ' + (delta > 0 ? 'up' : 'down') + '">' + (delta > 0 ? '+' : '') + delta + ' (was ' + v + ')</div>' : '') + '</div>';
      });
    });
    var afterBal = after && after.balance != null ? after.balance : bal;
    var bd = afterBal - bal;
    h += '<div class="bal' + (afterBal > 0 ? ' hl' : '') + '"><div class="label">Outstanding</div><div class="value" style="font-size:1rem">' + UI.money(afterBal) + '</div>' + (Math.abs(bd) > 0.001 ? '<div class="delta ' + (bd > 0 ? 'up' : 'down') + '">' + (bd > 0 ? '+' : '') + UI.lkr(bd) + '</div>' : '') + '</div>';
    if (c.payType !== 'Cash') h += '<div class="bal' + (afterBal > c.limit ? ' bad' : '') + '"><div class="label">Credit limit</div><div class="value" style="font-size:1rem">' + UI.lkr(c.limit) + '</div><div class="small muted">' + c.terms + ' days</div></div>';
    h += '</div>';
    h += '<div class="small muted" style="margin-top:8px">Delivery: ' + c.day + ', every ' + c.cycle + ' week' + (c.cycle > 1 ? 's' : '') + ' · next ' + D.dmy(c.nextDelivery) + ' · usual ' + Q.activeBottles().filter(function (b) { return c.usual[b.id]; }).map(function (b) { return c.usual[b.id] + ' × ' + b.litres + 'L'; }).join(', ') +
      ' · price ' + Q.activeBottles().map(function (b) { var p = Q.price(c, b.id); return p ? b.litres + 'L ' + UI.lkr(p) + (Q.isAgreed(c, b.id) ? ' (agreed)' : '') : ''; }).filter(Boolean).join(', ') + '</div>';
    return h;
  };

  /* ---------- export ---------- */
  UI.downloadCSV = function (filename, rows) {
    var csv = rows.map(function (r) {
      return r.map(function (v) { v = v == null ? '' : String(v); return /[",\n]/.test(v) ? '"' + v.replace(/"/g, '""') + '"' : v; }).join(',');
    }).join('\r\n');
    var blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 500);
  };
  /* Export the first table inside `el` as CSV (opens in Excel). */
  UI.tableToCSV = function (el, filename) {
    var table = (typeof el === 'string' ? UI.$(el) : el);
    if (table.tagName !== 'TABLE') table = table.querySelector('table');
    var rows = UI.$$('tr', table).map(function (tr) { return UI.$$('th,td', tr).map(function (td) { return td.innerText.replace(/\s+/g, ' ').trim(); }); });
    UI.downloadCSV(filename, rows);
  };

  /* Date-range toolbar used by reports (Section 8: all reports filter by date range). */
  UI.rangeBar = function (el, from, to, onApply, extraHtml) {
    el.innerHTML = '<div class="toolbar no-print">' +
      '<div class="field"><label>From</label>' + UI.dateInput('from', from) + '</div>' +
      '<div class="field"><label>To</label>' + UI.dateInput('to', to) + '</div>' +
      (extraHtml || '') +
      '<div class="field"><label>Quick</label><select name="quick">' + UI.options([{ v: '', l: '—' }, { v: 'today', l: 'Today' }, { v: 'week', l: 'Last 7 days' }, { v: 'month', l: 'This month' }, { v: 'last', l: 'Last month' }, { v: 'all', l: 'Aug – Oct 2026' }]) + '</select></div>' +
      '<button class="btn primary" type="button" data-act="apply">Apply</button>' +
      '<span style="flex:1"></span>' +
      '<button class="btn" type="button" data-act="pdf">Export PDF</button>' +
      '<button class="btn" type="button" data-act="xls">Export Excel</button></div>';
    var f = el.querySelector('[name=from]'), t = el.querySelector('[name=to]');
    el.querySelector('[name=quick]').addEventListener('change', function (e) {
      var td = TW.today(), v = e.target.value;
      if (v === 'today') { f.value = D.dmy(td); t.value = D.dmy(td); }
      if (v === 'week') { f.value = D.dmy(D.add(td, -6)); t.value = D.dmy(td); }
      if (v === 'month') { f.value = D.dmy(D.monthStart(D.month(td))); t.value = D.dmy(td); }
      if (v === 'last') { var pm = D.prevMonth(D.month(td)); f.value = D.dmy(D.monthStart(pm)); t.value = D.dmy(D.monthEnd(pm)); }
      if (v === 'all') { f.value = '01/08/2026'; t.value = D.dmy(td); }
      apply();
    });
    function apply() {
      var a = UI.readDate(f), b = UI.readDate(t);
      if (!a || !b) { UI.toast('Enter dates as DD/MM/YYYY', 'bad'); return; }
      if (a > b) { UI.toast('From date is after To date', 'bad'); return; }
      onApply(a, b, el);
    }
    el.querySelector('[data-act=apply]').addEventListener('click', apply);
    el.querySelector('[data-act=pdf]').addEventListener('click', function () { window.print(); });
    el.querySelector('[data-act=xls]').addEventListener('click', function () {
      var tbl = document.querySelector('.report-table table') || document.querySelector('.content table');
      if (!tbl) return UI.toast('Nothing to export', 'warn');
      UI.tableToCSV(tbl, (document.title.split('·')[0].trim().replace(/\s+/g, '_') || 'report') + '_' + a2(f.value) + '_' + a2(t.value) + '.csv');
      UI.toast('Excel (CSV) file downloaded', 'ok');
    });
    function a2(s) { return s.replace(/\//g, ''); }
    UI.$$('input', el).forEach(function (i) { i.addEventListener('keydown', function (e) { if (e.key === 'Enter') apply(); }); });
    apply();
  };

  /* Simple bar chart (CSS). data: [{label, value, title}] */
  UI.bars = function (el, data, fmt) {
    var max = Math.max.apply(null, data.map(function (d) { return d.value; }).concat([1]));
    var h = '<div class="bars">';
    data.forEach(function (d) { h += '<div class="bar" style="height:' + Math.max(1, d.value / max * 100) + '%" title="' + UI.esc(d.title || d.label + ': ' + (fmt ? fmt(d.value) : d.value)) + '"><span>' + (fmt ? fmt(d.value) : d.value) + '</span></div>'; });
    h += '</div><div class="bar-labels">';
    data.forEach(function (d) { h += '<span>' + UI.esc(d.label) + '</span>'; });
    el.innerHTML = h + '</div>';
  };
  UI.hbars = function (el, data, fmt) {
    var max = Math.max.apply(null, data.map(function (d) { return d.value; }).concat([1]));
    el.innerHTML = data.map(function (d) {
      return '<div class="hbar"><span>' + UI.esc(d.label) + '</span><div class="track"><div class="fill" style="width:' + (d.value / max * 100) + '%"></div></div><span class="num">' + (fmt ? fmt(d.value) : d.value) + '</span></div>';
    }).join('');
  };

  /* Side panel (used for record details, e.g. a customer). Returns {el, close}. */
  UI.drawer = function (title, html, opts) {
    opts = opts || {};
    var old = document.querySelector('.drawer-back');
    if (old) old.remove();
    var back = document.createElement('div');
    back.className = 'drawer-back';
    back.innerHTML = '<aside class="drawer" role="dialog"><header><h2>' + UI.esc(title) + '</h2><button class="x" aria-label="Close">&times;</button></header><div class="body"></div></aside>';
    back.querySelector('.body').innerHTML = html;
    function close() { back.remove(); document.removeEventListener('keydown', onKey); if (opts.onClose) opts.onClose(); }
    function onKey(e) { if (e.key === 'Escape' && !document.querySelector('.modal-back')) close(); }
    back.querySelector('.x').addEventListener('click', close);
    back.addEventListener('mousedown', function (e) { if (e.target === back) close(); });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(back);
    return { el: back.querySelector('.body'), close: close };
  };

  /* Tabs. tabs: [{id, label}]; remembers the tab in the URL hash. Calls onChange(id). */
  UI.tabs = function (el, tabs, onChange) {
    if (typeof el === 'string') el = UI.$(el);
    var want = (location.hash || '').slice(1);
    var cur = tabs.some(function (t) { return t.id === want; }) ? want : tabs[0].id;
    el.className = 'tabs';
    el.innerHTML = tabs.map(function (t) { return '<button type="button" data-tab="' + t.id + '"' + (t.id === cur ? ' class="active"' : '') + '>' + UI.esc(t.label) + '</button>'; }).join('');
    function show(id) {
      cur = id;
      UI.$$('[data-tab]', el).forEach(function (b) { b.classList.toggle('active', b.getAttribute('data-tab') === id); });
      UI.$$('[data-pane]').forEach(function (p) { p.classList.toggle('hidden', p.getAttribute('data-pane') !== id); });
      try { history.replaceState(null, '', '#' + id); } catch (e) { /* file:// may refuse */ }
      if (onChange) onChange(id);
    }
    el.addEventListener('click', function (e) { var id = e.target.getAttribute('data-tab'); if (id) show(id); });
    show(cur);
    return { show: show, current: function () { return cur; } };
  };

  /* "+ New" style dropdown. items: [{label, onClick}] */
  UI.menuButton = function (btn, items) {
    var wrap = document.createElement('span');
    wrap.className = 'menu-wrap';
    btn.parentNode.insertBefore(wrap, btn);
    wrap.appendChild(btn);
    var menu = document.createElement('div');
    menu.className = 'menu hidden';
    items.forEach(function (it) {
      var b = document.createElement('button');
      b.type = 'button';
      b.innerHTML = it.label;
      b.addEventListener('click', function () { menu.classList.add('hidden'); it.onClick(); });
      menu.appendChild(b);
    });
    wrap.appendChild(menu);
    btn.addEventListener('click', function (e) { e.stopPropagation(); menu.classList.toggle('hidden'); });
    document.addEventListener('click', function () { menu.classList.add('hidden'); });
  };

  /* Open a printable document (PO, invoice, statement, receipt, delivery sheet) in a new tab and print it. */
  UI.printDoc = function (title, html, landscape) {
    var w = window.open('', '_blank');
    if (!w) { UI.toast('Please allow pop-ups to print', 'warn'); return; }
    var css = document.querySelector('link[rel=stylesheet]').href;
    w.document.write('<!doctype html><html lang="en"><head><meta charset="utf-8"><title>' + UI.esc(title) + '</title><link rel="stylesheet" href="' + css + '">' +
      '<style>body{background:#fff;padding:16px}' + (landscape ? '@page{size:A4 landscape;margin:10mm}.doc{max-width:none}' : '') + '</style></head><body>' + html +
      '<p class="no-print" style="text-align:center;margin-top:16px"><button class="btn primary" onclick="window.print()">Print / Save as PDF</button></p></body></html>');
    w.document.close();
    setTimeout(function () { try { w.focus(); w.print(); } catch (e) { /* ignore */ } }, 500);
  };
  /* Standard document header used on printed documents. */
  UI.docHead = function (title, metaHtml) {
    var co = TW.db.settings.company;
    return '<div class="doc-head"><div class="co"><b>' + UI.esc(co.name) + '</b>' + UI.esc(co.address) + '<br>Tel ' + UI.esc(co.phone) + ' · ' + UI.esc(co.email) + '<br>Reg. ' + UI.esc(co.regNo) + '</div><div class="meta"><h1>' + UI.esc(title) + '</h1>' + (metaHtml || '') + '</div></div>';
  };

  /* Printable bill for one sale (first purchase, exchange, extra order, product sale …). */
  UI.printBill = function (t) {
    var c = t.custId ? Q.customer(t.custId) : null, rows = [];
    (t.lines || []).forEach(function (l) {
      if (l.pid) { rows.push([Q.product(l.pid).name, l.qty, l.price, l.qty * l.price]); return; }
      if (l.filled && l.price && !(t.kind === 'custdamage' && !t.split.water)) rows.push(['Water – ' + Q.btName(l.bt), l.filled, l.price, l.filled * l.price]);
      if (l.deposits) rows.push(['Bottle deposit – ' + Q.btShort(l.bt) + ' (not refundable)', l.deposits, l.deposit, l.deposits * l.deposit]);
      if (l.damaged && t.mode !== 'reduce') rows.push(['New bottle – damaged bottle replaced', l.damaged, l.deposit, l.damaged * l.deposit]);
      if (l.old) rows.push(['Old ' + ((TW.db.oldBrands.filter(function (b) { return b.id === l.brandId; })[0] || {}).name || '') + ' bottles handed in – no deposit', l.old, '', 0]);
      if (l.empties) rows.push(['Empty ' + Q.btShort(l.bt) + ' bottles returned', l.empties, '', 0]);
    });
    var bal = c ? Q.balance(c.id) : 0;
    UI.printDoc('Bill ' + t.billNo, '<div class="doc" style="max-width:620px">' + UI.docHead('BILL', '<div class="mono"><b>' + UI.esc(t.billNo) + '</b></div><div>' + D.dmy(t.date) + '</div>' + (t.paperNo ? '<div class="small">Paper bill ' + UI.esc(t.paperNo) + '</div>' : '')) +
      '<p><b>' + UI.esc(c ? c.name : t.walkIn || '') + '</b>' + (c ? ' (' + c.id + ')<br>' + UI.esc(c.address) + ', ' + UI.esc(c.area) + '<br>' + UI.esc(c.phone) + ' · ' + c.payType : '') + '</p>' +
      '<p class="small muted">' + UI.esc(TW.KINDS[t.kind]) + (t.note ? ' – ' + UI.esc(t.note) : '') + '</p>' +
      '<table><thead><tr><th>Description</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Amount (Rs.)</th></tr></thead><tbody>' +
      rows.map(function (r) { return '<tr><td>' + UI.esc(r[0]) + '</td><td class="num">' + r[1] + '</td><td class="num">' + (r[2] !== '' ? UI.num(Number(r[2]).toFixed(2)) : '') + '</td><td class="num">' + (r[3] ? UI.num(r[3].toFixed(2)) : '') + '</td></tr>'; }).join('') +
      '</tbody></table><div style="display:flex;justify-content:flex-end;margin-top:10px"><div class="summary" style="min-width:260px"><span class="total">Total</span><b class="num total">' + UI.money(t.amount) + '</b><span>Paid</span><b class="num">' + UI.money(t.paid) + '</b>' +
      (c ? '<span>Account balance now</span><b class="num">' + UI.money(bal) + '</b>' : '') + '</div></div>' +
      (c ? '<p class="small">Bottles held: ' + Q.activeBottles().map(function (b) { return (c.held[b.id] || 0) + ' × ' + b.litres + 'L'; }).join(', ') + ' · next delivery ' + D.dayName(c.nextDelivery) + ' ' + D.dmy(c.nextDelivery) + '</p>' : '') +
      '<div class="sig-row"><div>Issued by<br>' + UI.esc(t.user || '') + '</div><div>Customer signature</div></div></div>');
  };

  /* Read form fields inside a modal by name. */
  UI.mval = function (root, name) { var el = root.querySelector('[name="' + name + '"]'); if (!el) return ''; return el.type === 'checkbox' ? el.checked : el.value; };

  /* Wire Enter key to move to the next field (NFR-04 keyboard entry). */
  UI.enterToNext = function (form, onLast) {
    form.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'BUTTON') return;
      var fields = UI.$$('input:not([type=hidden]):not([readonly]):not(:disabled), select:not(:disabled), textarea:not(:disabled)', form).filter(function (x) { return x.offsetParent !== null; });
      var i = fields.indexOf(e.target);
      if (i < 0) return;
      e.preventDefault();
      if (i === fields.length - 1) { if (onLast) onLast(); }
      else { fields[i + 1].focus(); if (fields[i + 1].select) fields[i + 1].select(); }
    });
  };

  global.UI = UI;
})(window);
