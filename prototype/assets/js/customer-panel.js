/* Thuhina Water prototype – customer details side panel (FR-11, FR-13) and the agreed-price popup (FR-05).
   Shows bottles held, empties to collect, bottles owed, balance, delivery details and full history. */
(function (global) {
  'use strict';
  var TW = global.TW, UI = global.UI, App = global.App;
  var Q = TW.Q, D = TW.D;

  function lineText(t) {
    if (t.kind === 'product' || t.origKind === 'product') return (t.lines || []).map(function (l) { return l.qty + ' × ' + Q.product(l.pid).name; }).join(', ');
    return (t.lines || []).map(function (l) {
      var s = [];
      if (l.filled) s.push(l.filled + ' filled');
      if (l.empties) s.push(l.empties + ' empties back');
      if (l.toCollect) s.push(l.toCollect + ' to collect');
      if (l.owed) s.push('<b style="color:var(--bad)">' + l.owed + ' owed</b>');
      if (l.deposits) s.push(l.deposits + ' deposit(s)');
      if (l.old) s.push(l.old + ' old bottle(s) in');
      if (l.damaged) s.push(l.damaged + ' damaged');
      return s.length ? Q.btShort(l.bt) + ': ' + s.join(', ') : '';
    }).filter(Boolean).join('; ');
  }

  /* Deactivate / reactivate a customer (FR-13). Deactivating asks for the reason and the date, and warns about
     bottles still with the customer and money still owed – these stay on the account. onDone() runs after saving. */
  UI.DEACT_REASONS = ['Stopped buying', 'Moved away', 'Business closed', 'Went to another supplier', 'Not paying', 'Other'];
  /* Things still open on the account, shown as a warning when deactivating. */
  UI.openItems = function (c) {
    var bal = Q.balance(c.id), tc = Q.sumBt(c.toCollect), ow = Q.sumBt(c.owed), warn = [];
    if (Q.sumBt(c.held)) warn.push('still holds <b>' + Q.activeBottles().filter(function (b) { return c.held[b.id]; }).map(function (b) { return c.held[b.id] + ' × ' + b.litres + 'L'; }).join(', ') + '</b> bottles');
    if (tc) warn.push('has <b>' + tc + '</b> empties to collect');
    if (ow) warn.push('owes <b>' + ow + '</b> bottles');
    if (bal > 0) warn.push('owes <b>' + UI.money(bal) + '</b>');
    return warn.length ? '<div class="banner warn">' + UI.esc(c.name) + ' ' + warn.join(', ') + '. These stay on the account – collect the bottles and the money before or after deactivating.</div>' : '';
  };
  UI.customerStatus = function (c, onDone) {
    if (c.status !== 'Active') {
      return UI.confirm('Reactivate ' + c.name + '?', '<p>' + UI.esc(c.name) + ' was made inactive' + (c.statusDate ? ' on ' + D.dmy(c.statusDate) : '') + (c.statusReason ? ' – ' + UI.esc(c.statusReason) : '') + '.</p><p>They will show on the delivery lists again from the next ' + c.day + '.</p>', 'Reactivate').then(function (y) {
        if (y && UI.result(TW.Ops.setCustomerStatus(c.id, true), 'Customer reactivated') && onDone) onDone();
      });
    }
    var m = UI.modal({
      title: 'Deactivate ' + c.name + '?',
      body: '<p>An inactive customer is taken off the delivery lists and cannot get new bills. All history is kept and you can reactivate at any time.</p>' +
        UI.openItems(c) +
        '<div class="form-grid" style="grid-template-columns:1fr 2fr"><div class="field req"><label>Inactive from</label>' + UI.dateInput('sdate', TW.today()) + '</div>' +
        '<div class="field req"><label>Reason</label><select name="sreason">' + UI.options(UI.DEACT_REASONS, '', { blank: 'Select' }) + '</select></div>' +
        '<div class="field full"><label>Details</label><input name="snote" placeholder="e.g. Moved to Kandy – will call if they come back"></div></div>',
      buttons: [{ label: 'Cancel' }, { label: 'Deactivate', cls: 'danger', onClick: function (close, el) {
        var r = UI.mval(el, 'sreason'), n = UI.mval(el, 'snote').trim();
        if (r === 'Other' && !n) return UI.toast('Enter the details for "Other"', 'bad'), false;
        var res = TW.Ops.setCustomerStatus(c.id, false, r ? r + (n ? ' – ' + n : '') : n, D.fromDmy(UI.mval(el, 'sdate')));
        if (UI.result(res, 'Customer deactivated')) { close(); if (onDone) onDone(); }
        return false;
      } }]
    });
    return m;
  };

  /* Price shown for a customer and bottle type, e.g. "Rs. 270 agreed (standard Rs. 300)". */
  UI.priceText = function (c, bt, date) {
    var p = Q.price(c, bt, date), std = Q.stdPrice(bt, c.type, date);
    if (p == null) return '–';
    return UI.lkr(p) + (Q.isAgreed(c, bt, date) ? ' <span class="badge info" title="Agreed price for this customer">agreed</span> <span class="small muted">standard ' + UI.lkr(std) + '</span>' : '');
  };

  /* FR-05 – agreed water prices for one customer (Admin). One row per bottle type: standard price, price now,
     bottles a month at the usual quantity, filling charge (as a guide), and the new agreed price.
     Leave the new price blank to keep it; tick "standard" to go back to the price list. */
  UI.custPriceDialog = function (c, onDone) {
    var td = TW.today(), fac = Q.activeFactories()[0] || { charges: {} };
    var rows = Q.activeBottles().map(function (b) {
      var nx = Q.nextCustPrice(c.id, b.id);
      return '<tr data-bt="' + b.id + '"><td><b>' + UI.esc(b.name) + '</b><div class="small muted">about ' + Q.monthlyBottles(c, b.id) + ' a month</div></td>' +
        '<td class="num">' + UI.lkr(Q.stdPrice(b.id, c.type)) + '</td><td class="num">' + UI.priceText(c, b.id).replace(/ <span class="small muted">.*$/, '') + (nx ? '<div class="small" style="color:var(--brand)">→ ' + UI.lkr(nx.price) + ' from ' + D.dmy(nx.from) + '</div>' : '') + '</td>' +
        '<td class="num">' + (fac.charges[b.id] ? UI.lkr(fac.charges[b.id]) : '–') + '</td>' +
        '<td><input type="number" min="1" step="0.5" name="p_' + b.id + '" style="width:100px" placeholder="no change"><div class="small" data-diff></div></td>' +
        '<td>' + (Q.isAgreed(c, b.id) || nx ? '<label class="check small"><input type="checkbox" name="s_' + b.id + '"> back to standard</label>' : '') + '</td></tr>';
    }).join('');
    var m = UI.modal({
      title: 'Agreed prices – ' + c.name, wide: true,
      body: '<p class="muted">' + UI.esc(c.type) + ' customer. An agreed price replaces the standard ' + UI.esc(c.type) + ' price for this customer only. Bills already saved keep their price.</p>' +
        '<div class="table-wrap"><table><thead><tr><th>Bottle</th><th class="num">Standard</th><th class="num">Price now</th><th class="num" title="Filling factory charge per bottle – a guide for the lowest sensible price">Filling cost</th><th>New agreed price (Rs.)</th><th></th></tr></thead><tbody>' + rows + '</tbody></table></div>' +
        '<div class="form-grid" style="margin-top:12px"><div class="field req"><label>From</label>' + UI.dateInput('from', td) + '</div><div class="field"><label>Until (optional)</label>' + UI.dateInput('until', '') + '<span class="help">e.g. end of a tender or trial</span></div>' +
        '<div class="field req span2"><label>Reason</label><input name="reason" placeholder="e.g. Volume price – about 3,500 bottles a month"></div></div>' +
        '<p class="small muted">Only the Admin can set agreed prices. Every change is kept in the customer\'s price history and the audit log.</p>',
      buttons: [{ label: 'Cancel' }, { label: 'Save prices', cls: 'primary', onClick: function (close, el) {
        var from = D.fromDmy(UI.mval(el, 'from')), until = UI.mval(el, 'until') ? D.fromDmy(UI.mval(el, 'until')) : '', reason = UI.mval(el, 'reason').trim(), done = 0;
        if (UI.mval(el, 'until') && !until) return UI.toast('Enter the end date as DD/MM/YYYY', 'bad'), false;
        var todo = Q.activeBottles().filter(function (b) { return UI.mval(el, 'p_' + b.id) || UI.mval(el, 's_' + b.id); });
        if (!todo.length) return UI.toast('Enter a new price or tick "back to standard"', 'bad'), false;
        for (var i = 0; i < todo.length; i++) {
          var b = todo[i];
          var r = UI.mval(el, 's_' + b.id) ? TW.Ops.endCustPrice(c.id, b.id, from, reason) : TW.Ops.setCustPrice({ custId: c.id, bt: b.id, price: UI.mval(el, 'p_' + b.id), from: from, until: until, reason: reason });
          if (!UI.result(r)) return false;
          done++;
        }
        UI.toast(done + ' price' + (done > 1 ? 's' : '') + ' saved for ' + c.name, 'ok');
        close(); if (onDone) onDone();
        return false;
      } }]
    });
    m.el.addEventListener('input', function () {
      Q.activeBottles().forEach(function (b) {
        var v = +UI.mval(m.el, 'p_' + b.id), std = Q.stdPrice(b.id, c.type), ch = fac.charges[b.id] || 0, el = m.el.querySelector('tr[data-bt="' + b.id + '"] [data-diff]');
        if (!el) return;
        el.innerHTML = v ? (v < std ? '<span style="color:var(--warn)">' + UI.lkr(std - v) + ' (' + Math.round((std - v) / std * 100) + '%) below standard</span>' : v > std ? UI.lkr(v - std) + ' above standard' : 'same as standard') +
          (ch && v <= ch ? ' · <b style="color:var(--bad)">not above the filling cost</b>' : ch ? ' · ' + UI.lkr((v - ch) * Q.monthlyBottles(c, b.id)) + ' a month after filling' : '') : '';
      });
    });
    return m;
  };

  /* opts: { onEdit(c), onChange() } */
  UI.customerPanel = function (id, opts) {
    opts = opts || {};
    var c = Q.customer(id);
    if (!c) return;
    var bal = Q.balance(c.id);
    var canSell = App.can('sales/sales-bills') === 'full';
    var canEdit = App.can('master-data/customers') === 'full';
    var canBill = !!App.can('billing/billing');
    var h = '';
    if (c.status !== 'Active') h += '<div class="banner warn"><b>Inactive' + (c.statusDate ? ' since ' + D.dmy(c.statusDate) : '') + '.</b> ' + UI.esc(c.statusReason || '') + ' History is kept.</div>';
    if (c.payType !== 'Cash' && bal > c.limit) h += '<div class="banner bad">Over the credit limit of ' + UI.money(c.limit) + '. New sales need Admin approval.</div>';
    if (c.status === 'Active' && c.nextDelivery < TW.today()) h += '<div class="banner warn">Delivery overdue since ' + D.dmy(c.nextDelivery) + '.</div>';
    h += '<div class="card"><div class="card-head"><div><div class="muted">' + c.id + ' · ' + UI.esc(c.address) + ', ' + UI.esc(c.area) + ' · ' + UI.esc(c.phone) + '</div>' +
      '<div style="margin-top:4px">' + UI.statusBadge(c.status) + ' ' + UI.badge(c.type) + ' ' + UI.statusBadge(c.payType) + (Q.hasAgreed(c) ? ' ' + UI.badge('Agreed price', 'info') : '') + '</div></div></div>';
    var acts = [];
    if (canEdit && opts.onEdit) acts.push('<button class="btn" data-a="edit">Edit</button>');
    if (canSell && c.status === 'Active') {
      acts.push('<a class="btn primary" href="' + App.url('sales/enter-bills', { cust: c.id }) + '">Enter bill</a>');
      acts.push(Q.sumBt(c.held) ? '<a class="btn" href="' + App.url('sales/sales-bills', { 'new': 'extra', cust: c.id }) + '">Extra order</a>' : '<a class="btn primary" href="' + App.url('sales/sales-bills', { 'new': 'first', cust: c.id }) + '">First purchase</a>');
      if (Q.sumBt(c.toCollect)) acts.push('<a class="btn" href="' + App.url('sales/sales-bills', { 'new': 'collect', cust: c.id }) + '">Collect empties</a>');
      if (Q.sumBt(c.owed)) acts.push('<a class="btn" href="' + App.url('sales/sales-bills', { 'new': 'owed', cust: c.id }) + '">Settle owed</a>');
    }
    if (canBill) {
      if (bal > 0) acts.push('<a class="btn" href="' + App.url('billing/billing', { pay: c.id }) + '">Receive payment</a>');
      acts.push('<a class="btn" href="' + App.url('billing/billing', { statement: c.id }) + '">Statement</a>');
    }
    if (App.can('sales/quotations') === 'full') acts.push('<a class="btn" href="' + App.url('sales/quotations', { 'new': 1, cust: c.id }) + '">Quotation</a>');
    if (canEdit) acts.push('<button class="btn" data-a="prices">Prices</button>');
    if (canEdit) acts.push('<button class="btn ' + (c.status === 'Active' ? 'danger' : '') + '" data-a="status">' + (c.status === 'Active' ? 'Deactivate' : 'Reactivate') + '</button>');
    h += '<div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px">' + acts.join('') + '</div>';
    h += '<div class="balances">';
    Q.activeBottles().forEach(function (b) {
      var held = c.held[b.id] || 0, tc = c.toCollect[b.id] || 0, ow = c.owed[b.id] || 0;
      if (!held && !tc && !ow && b.id !== 'B20') return;
      h += '<div class="bal"><div class="label">Bottles held (' + b.litres + 'L)</div><div class="value">' + held + '</div></div>';
      h += '<div class="bal' + (tc ? ' hl' : '') + '"><div class="label">Empties to collect (' + b.litres + 'L)</div><div class="value">' + tc + '</div></div>';
      h += '<div class="bal' + (ow ? ' bad' : '') + '"><div class="label">Bottles owed (' + b.litres + 'L)</div><div class="value">' + ow + '</div></div>';
    });
    h += '<div class="bal' + (bal > 0 ? ' hl' : '') + '"><div class="label">Outstanding</div><div class="value" style="font-size:1rem">' + UI.money(bal) + '</div></div>';
    if (c.payType !== 'Cash') h += '<div class="bal"><div class="label">Credit limit</div><div class="value" style="font-size:1rem">' + UI.lkr(c.limit) + '</div><div class="small muted">' + c.terms + ' days</div></div>';
    h += '</div>';
    h += '<div class="summary" style="margin-top:12px"><span class="muted">Delivery</span><b>' + c.day + ', ' + (c.cycle === 1 ? 'every week' : 'every ' + c.cycle + ' weeks') + ' · next ' + D.dmy(c.nextDelivery) + '</b>' +
      '<span class="muted">Usual quantity</span><b>' + (Q.activeBottles().filter(function (b) { return c.usual[b.id]; }).map(function (b) { return c.usual[b.id] + ' × ' + b.litres + 'L'; }).join(', ') || '–') + '</b>' +
      '<span class="muted">Water price</span><b>' + Q.activeBottles().map(function (b) { return Q.price(c, b.id) ? b.litres + 'L ' + UI.priceText(c, b.id) : ''; }).filter(Boolean).join('<br>') + '</b>' +
      '<span class="muted">Customer since</span><b>' + D.dmy(c.since) + '</b>' + (c.notes ? '<span class="muted">Notes</span><b>' + UI.esc(c.notes) + '</b>' : '') + '</div></div>';
    h += '<div class="card"><div id="cp-tabs"></div><div id="cp-hist"></div><div id="cp-pay"></div><div id="cp-inv"></div><div id="cp-price"></div></div>';
    var dr = UI.drawer(c.name, h);

    var rows = [];
    Q.custTxns(c.id).forEach(function (t) { rows.push({ date: t.date, s: t.created || '', t: t }); });
    Q.custPayments(c.id).forEach(function (p) { rows.push({ date: p.date, s: p.created || '', p: p }); });
    rows.sort(function (a, b) { return a.date < b.date ? -1 : a.date > b.date ? 1 : a.s < b.s ? -1 : 1; });
    var run = 0;
    rows.forEach(function (r) { run += r.t ? r.t.amount - r.t.paid : -r.p.amount; r.bal = run; });
    rows.reverse();
    UI.table(dr.el.querySelector('#cp-hist'), {
      columns: [
        { h: 'Date', f: function (r) { return D.dmy(r.date); } },
        { h: 'Entry', f: function (r) { return r.t ? UI.kindBadge(r.t.kind) + (r.t.reversedBy ? ' ' + UI.badge('reversed', 'bad') : '') + '<div class="small mono muted">' + UI.esc(r.t.billNo || r.t.id) + '</div>' : UI.badge('Payment – ' + r.p.mode, 'ok') + '<div class="small mono muted">' + r.p.id + '</div>'; } },
        { h: 'Details', f: function (r) { return r.p ? UI.esc(r.p.ref || '') + (r.p.note ? ' ' + UI.esc(r.p.note) : '') : lineText(r.t) + (r.t.note ? '<div class="small muted">' + UI.esc(r.t.note) + '</div>' : ''); } },
        { h: 'Charge', cls: 'num', f: function (r) { return r.t && r.t.amount ? UI.lkr(r.t.amount) : ''; } },
        { h: 'Paid', cls: 'num', f: function (r) { return r.t ? (r.t.paid ? UI.lkr(r.t.paid) : '') : UI.lkr(r.p.amount); } },
        { h: 'Balance', cls: 'num', f: function (r) { return UI.lkr(r.bal); } }
      ],
      rows: rows, empty: 'No transactions yet.'
    });
    UI.table(dr.el.querySelector('#cp-pay'), {
      columns: [{ h: 'Receipt', k: 'id' }, { h: 'Date', f: function (p) { return D.dmy(p.date); } }, { h: 'Method', k: 'mode' }, { h: 'Reference', k: 'ref' }, { h: 'Amount', cls: 'num', f: function (p) { return UI.money(p.amount); } }],
      rows: Q.custPayments(c.id).slice().reverse(), empty: 'No payments.'
    });
    UI.table(dr.el.querySelector('#cp-inv'), {
      columns: [{ h: 'Invoice', f: function (i) { return canBill ? '<a href="' + App.url('billing/billing', { invoice: i.id }) + '">' + i.id + '</a>' : i.id; } }, { h: 'Month', f: function (i) { return D.monthLabel(i.period); } }, { h: 'This month', cls: 'num', f: function (i) { return UI.money(i.amount); } }, { h: 'Total due', cls: 'num', f: function (i) { return UI.money(i.total); } }],
      rows: TW.db.invoices.filter(function (i) { return i.custId === c.id; }).reverse(), empty: c.payType === 'Monthly bill' ? 'No invoices yet.' : 'Only Monthly bill customers get invoices.'
    });
    UI.table(dr.el.querySelector('#cp-price'), {
      columns: [{ h: 'Bottle', f: function (e) { return Q.btShort(e.bt); } }, { h: 'Price', cls: 'num', f: function (e) { return '<b>' + UI.lkr(e.price) + '</b>' + (e.std ? '<div class="small muted">standard ' + UI.lkr(e.std) + '</div>' : ''); } },
        { h: 'From', f: function (e) { return D.dmy(e.from); } }, { h: 'Until', f: function (e) { return e.until ? D.dmy(e.until) : '–'; } }, { h: 'Reason', f: function (e) { return UI.esc(e.reason) + (e.quoteId ? ' ' + App.link('sales/quotations', { id: e.quoteId }, e.quoteId) : '') + '<div class="small muted">' + UI.esc(e.by) + '</div>'; } },
        { h: '', f: function (e) { var td = TW.today(); if (e.cancelled) return UI.badge('Cancelled'); if (e.from > td) return UI.badge('Scheduled', 'info'); if (e.until && e.until < td) return UI.badge('Ended'); return Q.custPriceEntry(c.id, e.bt) === e ? UI.badge('In force', 'ok') : UI.badge('Replaced'); } }],
      rows: Q.custPriceHistory(c.id), empty: 'Standard ' + UI.esc(c.type) + ' prices – no agreed prices.'
    });
    // tabs scoped to the drawer
    var tabEl = dr.el.querySelector('#cp-tabs');
    tabEl.className = 'tabs';
    tabEl.innerHTML = '<button data-t="hist" class="active">History</button><button data-t="pay">Payments</button><button data-t="inv">Invoices</button><button data-t="price">Prices</button>';
    function show(t) {
      UI.$$('button', tabEl).forEach(function (b) { b.classList.toggle('active', b.getAttribute('data-t') === t); });
      ['hist', 'pay', 'inv', 'price'].forEach(function (k) { dr.el.querySelector('#cp-' + k).classList.toggle('hidden', k !== t); });
    }
    tabEl.addEventListener('click', function (e) { var t = e.target.getAttribute('data-t'); if (t) show(t); });
    show('hist');

    dr.el.addEventListener('click', function (e) {
      var a = e.target.getAttribute('data-a');
      if (a === 'edit') { dr.close(); opts.onEdit(c); }
      if (a === 'prices') UI.custPriceDialog(c, function () { dr.close(); if (opts.onChange) opts.onChange(); UI.customerPanel(c.id, opts); });
      if (a === 'status') UI.customerStatus(c, function () { dr.close(); if (opts.onChange) opts.onChange(); });
    });
    return dr;
  };
})(window);
