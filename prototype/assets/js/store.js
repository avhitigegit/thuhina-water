/* Thuhina Water prototype – demo data store and business rules.
   - Keeps all demo data in localStorage (falls back to memory if unavailable).
   - Seeds from TW_DATA on first load, then simulates Aug–Sep 2026 history through
     the same operations the screens use, so every number is consistent.
   - Ops.* functions apply the BRD rules (BR-01 … BR-15) and return {ok, error}. */
(function (global) {
  'use strict';

  var DATA = global.TW_DATA;
  var KEY = 'thuhina.demo.v1';
  var SKEY = 'thuhina.session.v1';
  var VERSION = 8;

  /* ---------------- storage (try/catch wrapped) ---------------- */
  var mem = {};
  var storageOK = true;
  function lsGet(k) {
    try { return global.localStorage.getItem(k); } catch (e) { storageOK = false; return Object.prototype.hasOwnProperty.call(mem, k) ? mem[k] : null; }
  }
  function lsSet(k, v) {
    try { global.localStorage.setItem(k, v); } catch (e) { storageOK = false; mem[k] = v; }
  }
  function lsDel(k) {
    try { global.localStorage.removeItem(k); } catch (e) { storageOK = false; }
    delete mem[k];
  }

  /* ---------------- dates (ISO yyyy-mm-dd, UTC maths) ---------------- */
  var DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  var D = {
    DAYS: DAYS,
    MONTHS: MONTHS,
    parse: function (iso) { var p = iso.split('-'); return Date.UTC(+p[0], +p[1] - 1, +p[2]); },
    iso: function (ms) { var d = new Date(ms); return d.getUTCFullYear() + '-' + pad(d.getUTCMonth() + 1) + '-' + pad(d.getUTCDate()); },
    add: function (iso, n) { return D.iso(D.parse(iso) + n * 864e5); },
    diff: function (a, b) { return Math.round((D.parse(a) - D.parse(b)) / 864e5); },
    dow: function (iso) { return new Date(D.parse(iso)).getUTCDay(); },
    dayName: function (iso) { return DAYS[D.dow(iso)]; },
    dmy: function (iso) { if (!iso) return ''; var p = String(iso).slice(0, 10).split('-'); return p[2] + '/' + p[1] + '/' + p[0]; },
    dmyt: function (ts) { if (!ts) return ''; return D.dmy(ts.slice(0, 10)) + (ts.length > 10 ? ' ' + ts.slice(11, 16) : ''); },
    fromDmy: function (s) {
      var m = /^\s*(\d{1,2})\/(\d{1,2})\/(\d{4})\s*$/.exec(s || '');
      if (!m) return null;
      var iso = m[3] + '-' + pad(+m[2]) + '-' + pad(+m[1]);
      if (+m[2] < 1 || +m[2] > 12) return null;
      return D.iso(D.parse(iso)) === iso ? iso : null;
    },
    month: function (iso) { return iso.slice(0, 7); },
    monthLabel: function (ym) { var p = ym.split('-'); return MONTHS[+p[1] - 1] + ' ' + p[0]; },
    monthStart: function (ym) { return ym + '-01'; },
    monthEnd: function (ym) { var p = ym.split('-'); return D.iso(Date.UTC(+p[0], +p[1], 0)); },
    prevMonth: function (ym) { var p = ym.split('-'); return D.iso(Date.UTC(+p[0], +p[1] - 2, 1)).slice(0, 7); }
  };

  /* ---------------- labels ---------------- */
  var BUCKETS = ['empty', 'factory', 'filled', 'customers', 'writtenOff'];
  var BUCKET_LABEL = { empty: 'Empty in store', factory: 'At factory', filled: 'Filled in store', customers: 'With customers', writtenOff: 'Written off' };
  var CUST_LABEL = { held: 'Bottles held', toCollect: 'Empties to collect', owed: 'Bottles owed' };
  var KINDS = {
    opening: 'Opening balance', first: 'First purchase', exchange: 'Exchange', leftdoor: 'Left at door',
    collect: 'Empties collected', extra: 'Extra order', product: 'Product sale', custdamage: 'Customer damage',
    owedsettle: 'Bottles owed settled', reversal: 'Reversal'
  };
  var PAY_TYPES = ['Cash', 'Credit', 'Monthly bill'];
  var PAY_MODES = ['Cash', 'Bank transfer', 'Cheque'];
  var TERMS = [{ v: 0, l: 'Cash on delivery' }, { v: 7, l: '7 days' }, { v: 14, l: '14 days' }, { v: 30, l: '30 days' }, { v: 45, l: '45 days' }, { v: 60, l: '60 days' }];
  var PO_STATUS = ['Draft', 'Approved', 'Sent', 'Partly received', 'Received', 'Cancelled'];
  var CQ_STATUS = ['Draft', 'Sent', 'Accepted', 'Rejected'];
  var SENT_VIA = ['Email', 'By hand', 'Post', 'WhatsApp'];

  /* ---------------- state ---------------- */
  var db = null;
  var cache = {};
  var ctx = { sim: false, user: null, time: null };

  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function today() { return db.settings.today; }
  /* Business date of the operation: during seeding it is the simulated day. */
  function bizDate() { return ctx.sim && ctx.time ? ctx.time.slice(0, 10) : today(); }
  function nowTs() {
    if (ctx.time) return ctx.time;
    var d = new Date();
    return today() + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }
  function seq(name, prefix, width) {
    db.seq[name] = (db.seq[name] || 0) + 1;
    var s = String(db.seq[name]);
    while (s.length < width) s = '0' + s;
    return prefix + s;
  }
  function save() {
    cache = {};
    if (ctx.sim) return;
    lsSet(KEY, JSON.stringify(db));
  }
  function load() {
    var raw = lsGet(KEY);
    if (raw) {
      try {
        var parsed = JSON.parse(raw);
        if (parsed && parsed.version === VERSION) { db = parsed; cache = {}; return db; }
      } catch (e) { /* fall through to reseed */ }
    }
    db = buildSeed();
    save();
    return db;
  }
  function reset() {
    lsDel(KEY);
    db = buildSeed();
    save();
  }

  /* ---------------- session ---------------- */
  function getSession() {
    var raw = lsGet(SKEY);
    if (!raw) return null;
    try { return JSON.parse(raw); } catch (e) { return null; }
  }
  function setSession(s) { if (s) lsSet(SKEY, JSON.stringify(s)); else lsDel(SKEY); }
  function currentUser() {
    if (ctx.user) return ctx.user;
    var s = getSession();
    var u = s && db.users.filter(function (x) { return x.id === s.userId; })[0];
    if (u) return { id: u.id, name: u.name, role: s.role || u.role };
    return { id: 'U01', name: 'Nimal Perera', role: 'Admin' };
  }

  /* ---------------- audit ---------------- */
  function audit(action, entity, ref, details) {
    var u = currentUser();
    db.audit.push({ id: seq('audit', 'A', 6), ts: nowTs(), user: u.name, role: u.role, action: action, entity: entity, ref: ref || '', details: details || '' });
  }

  /* ---------------- queries ---------------- */
  var Q = {};
  Q.bt = function (id) { return db.bottleTypes.filter(function (b) { return b.id === id; })[0]; };
  Q.btName = function (id) { var b = Q.bt(id); return b ? b.name : id; };
  Q.btShort = function (id) { var b = Q.bt(id); return b ? b.litres + 'L' : id; };
  Q.activeBottles = function () { return db.bottleTypes.filter(function (b) { return b.active; }); };
  Q.product = function (id) { return db.products.filter(function (p) { return p.id === id; })[0]; };
  Q.itemName = function (code) { var b = Q.bt(code); if (b) return 'Empty ' + b.name; var p = Q.product(code); return p ? p.name : code; };
  Q.isBottle = function (code) { return !!Q.bt(code); };
  Q.customer = function (id) { return db.customers.filter(function (c) { return c.id === id; })[0]; };
  Q.supplier = function (id) { return db.suppliers.filter(function (s) { return s.id === id; })[0]; };
  Q.user = function (id) { return db.users.filter(function (u) { return u.id === id; })[0]; };
  Q.txn = function (id) { return db.txns.filter(function (t) { return t.id === id; })[0]; };
  Q.po = function (id) { return db.pos.filter(function (p) { return p.id === id; })[0]; };
  Q.qr = function (id) { return db.qrs.filter(function (q) { return q.id === id; })[0]; };
  Q.quote = function (id) { return db.quotes.filter(function (q) { return q.id === id; })[0]; };
  Q.batch = function (id) { return db.batches.filter(function (b) { return b.id === id; })[0]; };
  Q.invoice = function (id) { return db.invoices.filter(function (i) { return i.id === id; })[0]; };
  Q.expense = function (id) { return db.expenses.filter(function (e) { return e.id === id; })[0]; };
  Q.cquote = function (id) { return db.cquotes.filter(function (q) { return q.id === id; })[0]; };

  /* Price lookup from the dated price history (FR-07). */
  Q.priceEntry = function (key, date) {
    var best = null;
    db.priceHistory.forEach(function (p) {
      if (p.key === key && p.effective <= date && (!best || p.effective >= best.effective)) best = p;
    });
    return best;
  };
  Q.stdPrice = function (bt, type, date) { var e = Q.priceEntry('water|' + bt + '|' + type, date || today()); return e ? e.price : null; };
  Q.deposit = function (bt, date) { var e = Q.priceEntry('deposit|' + bt, date || today()); return e ? e.price : null; };
  /* FR-05 / BR-08 (client feedback 08/10/2026): a customer can have an agreed water price per bottle type that
     overrides the standard price list. Agreed prices are dated (from, optional until) and kept as history.
     The agreed price in force on the bill date wins; otherwise the standard price for the customer type. */
  Q.custPriceEntry = function (custId, bt, date) {
    date = date || today();
    var best = null;
    db.custPrices.forEach(function (e) {
      if (e.custId !== custId || e.bt !== bt || e.cancelled || e.from > date || (e.until && e.until < date)) return;
      if (!best || e.from > best.from || (e.from === best.from && e.id > best.id)) best = e;
    });
    return best;
  };
  Q.price = function (c, bt, date) {
    var e = c && Q.custPriceEntry(c.id, bt, date);
    return e ? e.price : Q.stdPrice(bt, c ? c.type : 'Household', date);
  };
  Q.isAgreed = function (c, bt, date) { return !!(c && Q.custPriceEntry(c.id, bt, date)); };
  Q.hasAgreed = function (c, date) { return !!c && Q.activeBottles().some(function (b) { return Q.isAgreed(c, b.id, date); }); };
  /* Next agreed-price change after today (scheduled), if any. */
  Q.nextCustPrice = function (custId, bt) { return db.custPrices.filter(function (e) { return e.custId === custId && e.bt === bt && !e.cancelled && e.from > today(); }).sort(function (a, b) { return a.from < b.from ? -1 : 1; })[0] || null; };
  Q.custPriceHistory = function (custId) { return db.custPrices.filter(function (e) { return e.custId === custId; }).sort(function (a, b) { return a.from < b.from ? 1 : a.from > b.from ? -1 : a.id < b.id ? 1 : -1; }); };
  /* Bottles a month at the usual quantity and cycle – a guide when agreeing a price. */
  Q.monthlyBottles = function (c, bt) { return Math.round((c.usual[bt] || 0) * 52 / 12 / (c.cycle || 1)); };
  Q.scheduledPrices = function () { return db.priceHistory.filter(function (p) { return p.effective > today(); }); };

  /* Money balances: charges (amount - paid on bill) minus payments received. */
  Q.balances = function () {
    if (cache.bal) return cache.bal;
    var m = {};
    db.customers.forEach(function (c) { m[c.id] = 0; });
    db.txns.forEach(function (t) { if (t.custId) m[t.custId] = (m[t.custId] || 0) + (t.amount - t.paid); });
    db.payments.forEach(function (p) { m[p.custId] = (m[p.custId] || 0) - p.amount; });
    cache.bal = m;
    return m;
  };
  Q.balance = function (id) { return Q.balances()[id] || 0; };
  Q.balanceAt = function (id, date, exclusive) {
    var b = 0;
    db.txns.forEach(function (t) { if (t.custId === id && (exclusive ? t.date < date : t.date <= date)) b += t.amount - t.paid; });
    db.payments.forEach(function (p) { if (p.custId === id && (exclusive ? p.date < date : p.date <= date)) b -= p.amount; });
    return b;
  };
  Q.totalOutstanding = function () {
    var m = Q.balances(), s = 0;
    Object.keys(m).forEach(function (k) { if (m[k] > 0) s += m[k]; });
    return s;
  };
  Q.custTxns = function (id) { return db.txns.filter(function (t) { return t.custId === id; }); };
  Q.custPayments = function (id) { return db.payments.filter(function (p) { return p.custId === id; }); };
  Q.sumBt = function (obj) { var s = 0; Object.keys(obj || {}).forEach(function (k) { s += obj[k] || 0; }); return s; };

  /* FR-51 aging: oldest charges are settled first. */
  Q.aging = function (asOf) {
    asOf = asOf || today();
    var out = [];
    db.customers.forEach(function (c) {
      var charges = [], credit = 0;
      db.txns.forEach(function (t) {
        if (t.custId !== c.id || t.date > asOf) return;
        var net = t.amount - t.paid;
        if (net > 0) charges.push({ date: t.date, amt: net });
        else if (net < 0) credit += -net;
      });
      db.payments.forEach(function (p) { if (p.custId === c.id && p.date <= asOf) credit += p.amount; });
      charges.sort(function (a, b) { return a.date < b.date ? -1 : a.date > b.date ? 1 : 0; });
      var r = { cust: c, b0: 0, b30: 0, b60: 0, b90: 0, total: 0, oldest: null };
      charges.forEach(function (ch) {
        var use = Math.min(credit, ch.amt);
        credit -= use;
        var left = ch.amt - use;
        if (left <= 0.0001) return;
        var age = D.diff(asOf, ch.date);
        if (age <= 30) r.b0 += left; else if (age <= 60) r.b30 += left; else if (age <= 90) r.b60 += left; else r.b90 += left;
        r.total += left;
        if (!r.oldest) r.oldest = ch.date;
      });
      r.total -= credit; // advance payments show as negative
      if (Math.abs(r.total) > 0.0001) out.push(r);
    });
    return out;
  };

  Q.creditCheck = function (c, add) {
    var bal = Q.balance(c.id);
    var limited = c.payType !== 'Cash' && c.limit > 0;
    return { limited: limited, limit: c.limit, balance: bal, after: bal + add, exceeds: limited && bal + add > c.limit };
  };

  /* Delivery schedule (FR-42, FR-46) */
  Q.isDue = function (c, d) {
    if (c.status !== 'Active' || !c.nextDelivery) return false;
    if (d < c.since) return false;
    if (DAYS[D.dow(d)] !== c.day) return false;
    var p = 7 * (c.cycle || 1);
    var diff = D.diff(d, c.nextDelivery);
    return ((diff % p) + p) % p === 0;
  };
  Q.deliveredOn = function (custId, d) {
    return db.txns.filter(function (t) {
      return t.custId === custId && t.date === d && !t.reversedBy && ['exchange', 'leftdoor', 'first', 'extra'].indexOf(t.kind) >= 0;
    });
  };
  Q.dueList = function (d) {
    var list = [];
    db.customers.forEach(function (c) {
      var due = Q.isDue(c, d);
      var overdue = d === today() && c.status === 'Active' && !!c.nextDelivery && c.nextDelivery < d;
      if (due || overdue) list.push({ cust: c, due: due, overdue: overdue, entered: Q.deliveredOn(c.id, d) });
    });
    var dayOrder = function (x) { return x.cust.area + x.cust.name; };
    list.sort(function (a, b) { return dayOrder(a) < dayOrder(b) ? -1 : 1; });
    return list;
  };
  Q.nextAligned = function (c, after) {
    var n = c.nextDelivery;
    var p = 7 * (c.cycle || 1);
    if (!n) return null;
    while (n <= after) n = D.add(n, p);
    return n;
  };

  /* Stock */
  Q.stock = function (bt) { return db.stock[bt]; };
  Q.pool = function (bt) { var s = db.stock[bt]; return s.empty + s.factory + s.filled + s.customers; };
  Q.lowStock = function () {
    var out = [];
    Object.keys(db.minLevels).forEach(function (bt) {
      var b = Q.bt(bt);
      if (b && b.active && db.stock[bt].filled < db.minLevels[bt]) out.push({ bt: bt, filled: db.stock[bt].filled, min: db.minLevels[bt] });
    });
    return out;
  };
  Q.custTotals = function () {
    var t = { held: {}, toCollect: {}, owed: {} };
    db.customers.forEach(function (c) {
      ['held', 'toCollect', 'owed'].forEach(function (f) {
        Object.keys(c[f]).forEach(function (bt) { t[f][bt] = (t[f][bt] || 0) + c[f][bt]; });
      });
    });
    return t;
  };

  /* Purchasing */
  Q.quotesFor = function (qrId) { return db.quotes.filter(function (q) { return q.qrId === qrId; }); };
  Q.quoteTotal = function (q) {
    var qr = Q.qr(q.qrId), s = 0;
    q.lines.forEach(function (l) {
      var it = qr.items.filter(function (i) { return i.item === l.item; })[0];
      if (it && l.price != null) s += it.qty * l.price;
    });
    return s;
  };
  Q.poTotal = function (po) { var s = 0; po.lines.forEach(function (l) { s += l.qty * l.price; }); return s; };
  Q.poReceivedValue = function (po) { var s = 0; po.lines.forEach(function (l) { s += (l.received - l.damaged) * l.price; }); return s; };
  Q.supplierBilled = function (sid) {
    var s = 0;
    db.grns.forEach(function (g) { if (g.supplierId === sid) s += g.value; });
    return s;
  };
  Q.supplierPaid = function (sid) {
    var s = 0;
    db.supplierPayments.forEach(function (p) { if (p.supplierId === sid) s += p.amount; });
    return s;
  };
  Q.supplierOwed = function (sid) { return Q.supplierBilled(sid) - Q.supplierPaid(sid); };

  /* Production */
  Q.batchReturned = function (b) { var f = 0, r = 0; b.returns.forEach(function (x) { f += x.filled; r += x.rejected; }); return { filled: f, rejected: r, outstanding: b.qty - f - r }; };
  Q.batchCost = function (b) { var c = 0; b.returns.forEach(function (x) { c += x.cost; }); return c; };
  /* All batches sent together in one dispatch (one entry can send every bottle type). */
  Q.dispatchBatches = function (dispatchNo) { return db.batches.filter(function (b) { return b.dispatchNo === dispatchNo; }); };
  Q.factory = function (id) { return db.factories.filter(function (f) { return f.id === id; })[0]; };
  Q.activeFactories = function () { return db.factories.filter(function (f) { return f.active; }); };
  Q.factoryBilled = function (fid) { var s = 0; db.batches.forEach(function (b) { if (!fid || b.factoryId === fid) s += Q.batchCost(b); }); return s; };
  Q.factoryPaid = function (fid) { var s = 0; db.factoryPayments.forEach(function (p) { if (!fid || p.factoryId === fid) s += p.amount; }); return s; };
  Q.factoryOwed = function (fid) { return Q.factoryBilled(fid) - Q.factoryPaid(fid); };
  /* Running expenses (client request 08/10/2026) – grouped by the month they are for. */
  Q.expensesFor = function (ym) { return db.expenses.filter(function (e) { return e.month === ym; }); };
  Q.expenseTotal = function (ym, cat) { var s = 0; db.expenses.forEach(function (e) { if ((!ym || e.month === ym) && (!cat || e.category === cat)) s += e.amount; }); return s; };
  Q.activeExpenseCats = function () { return db.expenseCategories.filter(function (c) { return c.active; }); };
  /* Customer quotations (client request 08/10/2026). */
  Q.cquoteTotal = function (q) { var s = 0; q.lines.forEach(function (l) { s += l.qty * l.price; }); return s; };
  /* Shown status: a sent quotation past its valid-until date is Expired. */
  Q.cquoteStatus = function (q) { return q.status === 'Sent' && q.validUntil < today() ? 'Expired' : q.status; };
  Q.viaText = function (via) { return via === 'By hand' ? 'by hand' : 'by ' + (via === 'WhatsApp' ? via : String(via || '').toLowerCase()); };
  Q.termsLabel = function (days) { return TERMS.filter(function (x) { return x.v === +days; }).map(function (x) { return x.l; })[0] || (days + ' days'); };

  /* Finds a bill by system bill number or paper bill number (BR-13). */
  Q.billExists = function (no) {
    if (!no) return null;
    var b = String(no).trim().toUpperCase();
    return db.txns.filter(function (t) { return t.kind !== 'reversal' && !t.reversedBy && ((t.billNo && String(t.billNo).toUpperCase() === b) || (t.paperNo && String(t.paperNo).toUpperCase() === b)); })[0] || null;
  };

  /* ---------------- effects engine ---------------- */
  function newFx(custId) { return { stock: {}, cust: custId || null, c: {}, prod: {} }; }
  function fxS(fx, bt, bucket, d) {
    if (!d) return;
    fx.stock[bt] = fx.stock[bt] || {};
    fx.stock[bt][bucket] = (fx.stock[bt][bucket] || 0) + d;
  }
  function fxC(fx, field, bt, d) {
    if (!d) return;
    fx.c[field] = fx.c[field] || {};
    fx.c[field][bt] = (fx.c[field][bt] || 0) + d;
  }
  function fxP(fx, pid, d) { if (d) fx.prod[pid] = (fx.prod[pid] || 0) + d; }

  function checkFx(fx, sign) {
    var errs = [];
    Object.keys(fx.stock).forEach(function (bt) {
      Object.keys(fx.stock[bt]).forEach(function (k) {
        var cur = db.stock[bt][k], d = sign * fx.stock[bt][k];
        if (cur + d < 0) errs.push(BUCKET_LABEL[k] + ' (' + Q.btShort(bt) + ') cannot go below zero – available ' + cur + ', needed ' + (-d) + '.');
      });
    });
    if (fx.cust) {
      var c = Q.customer(fx.cust);
      Object.keys(fx.c).forEach(function (f) {
        Object.keys(fx.c[f]).forEach(function (bt) {
          var cur = c[f][bt] || 0, d = sign * fx.c[f][bt];
          if (cur + d < 0) errs.push(CUST_LABEL[f] + ' (' + Q.btShort(bt) + ') for ' + c.name + ' cannot go below zero – currently ' + cur + '.');
        });
      });
    }
    Object.keys(fx.prod).forEach(function (pid) {
      var p = Q.product(pid), d = sign * fx.prod[pid];
      if (p.stock + d < 0) errs.push(p.name + ' stock cannot go below zero – available ' + p.stock + '.');
    });
    return errs;
  }
  function applyFx(fx, sign) {
    Object.keys(fx.stock).forEach(function (bt) {
      Object.keys(fx.stock[bt]).forEach(function (k) { db.stock[bt][k] += sign * fx.stock[bt][k]; });
    });
    if (fx.cust) {
      var c = Q.customer(fx.cust);
      Object.keys(fx.c).forEach(function (f) {
        Object.keys(fx.c[f]).forEach(function (bt) { c[f][bt] = (c[f][bt] || 0) + sign * fx.c[f][bt]; });
      });
    }
    Object.keys(fx.prod).forEach(function (pid) { Q.product(pid).stock += sign * fx.prod[pid]; });
  }
  function negFx(fx) {
    var n = clone(fx);
    Object.keys(n.stock).forEach(function (bt) { Object.keys(n.stock[bt]).forEach(function (k) { n.stock[bt][k] = -n.stock[bt][k]; }); });
    Object.keys(n.c).forEach(function (f) { Object.keys(n.c[f]).forEach(function (bt) { n.c[f][bt] = -n.c[f][bt]; }); });
    Object.keys(n.prod).forEach(function (p) { n.prod[p] = -n.prod[p]; });
    return n;
  }
  function logMovement(date, ref, desc, fx) {
    var u = currentUser().name;
    Object.keys(fx.stock).forEach(function (bt) {
      var d = fx.stock[bt], any = false;
      BUCKETS.forEach(function (k) { if (d[k]) any = true; });
      if (any) db.movements.push({ id: seq('mov', 'M', 6), date: date, ref: ref, desc: desc, item: bt, d: clone(d), user: u, ts: nowTs() });
    });
    Object.keys(fx.prod).forEach(function (pid) {
      db.movements.push({ id: seq('mov', 'M', 6), date: date, ref: ref, desc: desc, item: pid, d: { stock: fx.prod[pid] }, user: u, ts: nowTs() });
    });
  }

  function fail(msg, code, extra) { var r = { ok: false, error: msg, code: code || 'INVALID' }; if (extra) r.extra = extra; return r; }
  function ok(o) { o = o || {}; o.ok = true; return o; }
  function int(v) { var n = parseInt(v, 10); return isNaN(n) ? 0 : n; }
  function num(v) { var n = parseFloat(v); return isNaN(n) ? 0 : n; }

  /* Common checks for a customer sale. */
  function checkCustomer(custId) {
    var c = Q.customer(custId);
    if (!c) return { err: fail('Select a customer.') };
    if (c.status !== 'Active') return { err: fail(c.name + ' is inactive. Reactivate the customer before entering sales.') };
    return { c: c };
  }
  /* System bill number – always generated, never typed (B000001 …). */
  function sysBill() { return seq('bill', 'B', 6); }
  function paper(input) { return input.paperNo ? String(input.paperNo).trim() : ''; }
  function checkBill(billNo, required) {
    if (!billNo) return required ? fail('Enter the paper bill number.') : null;
    var dup = Q.billExists(billNo);
    if (dup) return fail('Paper bill ' + billNo + ' was already entered on ' + D.dmy(dup.date) + ' (' + KINDS[dup.kind] + ', ' + (Q.customer(dup.custId) || {}).name + '). A bill number can be entered only once.', 'DUPLICATE', { txnId: dup.id });
    return null;
  }
  function checkDate(date) {
    if (!date) return fail('Enter a valid date (DD/MM/YYYY).');
    if (date > today()) return fail('Date cannot be after today (' + D.dmy(today()) + ').');
    return null;
  }
  function creditGate(c, amount, paid, input) {
    var chk = Q.creditCheck(c, amount - paid);
    if (chk.exceeds && !input.approveOverLimit) {
      return fail(c.name + ' would exceed the credit limit (' + money(c.limit) + '). Balance after this bill: ' + money(chk.after) + '. Admin approval is required.', 'CREDIT_LIMIT', chk);
    }
    return null;
  }
  function money(n) { return 'Rs. ' + Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }

  function commit(txn, fx, auditDetails) {
    var errs = checkFx(fx, 1);
    if (errs.length) return fail(errs.join(' ') + ' Record a stock adjustment first if the physical count differs (BR-12).', 'STOCK');
    applyFx(fx, 1);
    txn.fx = fx;
    txn.user = currentUser().name;
    txn.created = nowTs();
    db.txns.push(txn);
    logMovement(txn.date, txn.billNo ? 'Bill ' + txn.billNo : txn.id, KINDS[txn.kind] + (txn.custId ? ' – ' + Q.customer(txn.custId).name : (txn.walkIn ? ' – ' + txn.walkIn : '')), fx);
    audit('Create', KINDS[txn.kind], txn.billNo ? 'Bill ' + txn.billNo : txn.id, auditDetails || '');
    if (txn.override) audit('Approve', 'Credit limit', txn.billNo ? 'Bill ' + txn.billNo : txn.id, 'Over-limit sale approved by ' + txn.override);
    save();
    return ok({ txn: txn });
  }
  function advanceSchedule(c, date) {
    var before = c.nextDelivery;
    var n = Q.nextAligned(c, date);
    if (n && n !== before) c.nextDelivery = n;
    return { before: before, after: c.nextDelivery };
  }

  /* ---------------- operations ---------------- */
  var Ops = {};

  /* Preview of a delivery bill (exchange / left at door) – used by screens before saving.
     Returns per-line maths plus the customer balances after the bill. */
  Ops.previewBill = function (input) {
    var c = Q.customer(input.custId);
    if (!c) return null;
    var res = { lines: [], amount: 0, owedAdd: 0, notes: [], after: { held: clone(c.held), toCollect: clone(c.toCollect), owed: clone(c.owed) } };
    (input.lines || []).forEach(function (l) {
      var bt = l.bt, F = int(l.filled), E = int(l.empties);
      if (!F && !E) return;
      var price = Q.price(c, bt, input.date || today());
      var r = { bt: bt, filled: F, empties: E, price: price, amount: F * (price || 0), owed: 0, fromCollect: 0, fromOwed: 0, toCollect: 0, excess: 0 };
      if (input.kind === 'leftdoor') {
        r.toCollect = F; // BR-04: not a shortage
        r.empties = 0;
      } else {
        var diff = F - E;
        if (diff > 0) r.owed = diff; // BR-07
        else if (diff < 0) {
          var ex = -diff;
          r.fromCollect = Math.min(ex, c.toCollect[bt] || 0); ex -= r.fromCollect;
          r.fromOwed = Math.min(ex, c.owed[bt] || 0); ex -= r.fromOwed;
          r.excess = ex;
        }
      }
      res.after.toCollect[bt] = (res.after.toCollect[bt] || 0) + r.toCollect - r.fromCollect;
      res.after.owed[bt] = (res.after.owed[bt] || 0) + r.owed - r.fromOwed;
      res.amount += r.amount;
      res.owedAdd += r.owed;
      res.lines.push(r);
    });
    return res;
  };

  /* FR-34/35/38/46/47/48 – scheduled exchange or "Left at door" from a paper bill. */
  Ops.postBill = function (input) {
    var cc = checkCustomer(input.custId); if (cc.err) return cc.err;
    var c = cc.c;
    var e = checkDate(input.date) || checkBill(paper(input), true); if (e) return e;
    var kind = input.kind === 'leftdoor' ? 'leftdoor' : 'exchange';
    var pv = Ops.previewBill({ custId: c.id, date: input.date, kind: kind, lines: input.lines });
    if (!pv.lines.length) return fail('Enter the filled bottles given and/or empties collected.');
    if (Q.sumBt(c.held) === 0 && kind === 'exchange') return fail(c.name + ' has no bottles yet. Record a First purchase instead.');
    for (var i = 0; i < pv.lines.length; i++) {
      var l = pv.lines[i];
      if (l.price == null) return fail('No water price set for ' + Q.btName(l.bt) + ' / ' + c.type + '.');
      if (l.excess > 0) return fail(c.name + ' returned ' + l.excess + ' more ' + Q.btShort(l.bt) + ' empties than they hold, owe or have waiting for collection. Check the bill.');
      if (l.filled > 0 && (c.held[l.bt] || 0) === 0) return fail(c.name + ' holds no ' + Q.btShort(l.bt) + ' bottles. Use Extra order (deposit needed) for new bottles.');
    }
    if (kind === 'leftdoor') {
      if (!input.phoneConfirmed) return fail('Confirm that the customer agreed by phone (BR-04).');
      if (!input.note || !input.note.trim()) return fail('Add a note for the "Left at door" delivery (BR-04).');
    } else if (pv.owedAdd > 0 && !ctx.sim && (!input.note || !input.note.trim())) {
      return fail('Fewer empties than filled bottles: ' + pv.owedAdd + ' bottle(s) will be recorded as owed. Add a note explaining the shortage (FR-38).', 'NOTE');
    }
    var paid = num(input.paid);
    if (paid < 0) return fail('Paid amount cannot be negative.');
    var g = creditGate(c, pv.amount, paid, input); if (g) return g;

    var fx = newFx(c.id);
    pv.lines.forEach(function (l) {
      fxS(fx, l.bt, 'filled', -l.filled);
      fxS(fx, l.bt, 'empty', l.empties);
      fxS(fx, l.bt, 'customers', l.filled - l.empties);
      fxC(fx, 'toCollect', l.bt, l.toCollect - l.fromCollect);
      fxC(fx, 'owed', l.bt, l.owed - l.fromOwed);
    });
    var txn = {
      id: seq('txn', 'T', 6), kind: kind, billNo: sysBill(), paperNo: paper(input), date: input.date, custId: c.id,
      lines: pv.lines, amount: pv.amount, paid: paid, payMode: paid ? 'Cash' : '', note: (input.note || '').trim(),
      phoneConfirmed: kind === 'leftdoor' ? true : undefined,
      split: { water: pv.amount, deposit: 0, product: 0 },
      override: input.approveOverLimit && Q.creditCheck(c, pv.amount - paid).exceeds ? currentUser().name : null
    };
    var r = commit(txn, fx, KINDS[kind] + ' for ' + c.name + ': ' + pv.lines.map(function (l) { return Q.btShort(l.bt) + ' filled ' + l.filled + ' / empties ' + l.empties; }).join(', ') + ', ' + money(pv.amount));
    if (r.ok) {
      r.schedule = advanceSchedule(c, input.date); // FR-46
      txn.nextDelivery = r.schedule;
      save();
    }
    return r;
  };

  /* FR-32/33 – first purchase: deposit + water; old American Water bottles waive the deposit. */
  Ops.previewFirst = function (input) {
    var c = Q.customer(input.custId);
    var bt = input.bt, qty = int(input.qty), old = int(input.oldBottles);
    var price = c ? Q.price(c, bt, input.date || today()) : Q.stdPrice(bt, 'Household', input.date);
    var dep = Q.deposit(bt, input.date || today());
    var deposits = Math.max(0, qty - old);
    return { qty: qty, old: old, deposits: deposits, price: price, deposit: dep, water: qty * (price || 0), depositAmt: deposits * (dep || 0), amount: qty * (price || 0) + deposits * (dep || 0) };
  };
  Ops.firstPurchase = function (input) {
    var cc = checkCustomer(input.custId); if (cc.err) return cc.err;
    var c = cc.c;
    var e = checkDate(input.date) || checkBill(paper(input)); if (e) return e;
    if ((c.held[input.bt] || 0) > 0) return fail(c.name + ' already holds ' + Q.btShort(input.bt) + ' bottles. Use Extra order for additional bottles.');
    var p = Ops.previewFirst(input);
    if (p.qty < 1) return fail('Enter the number of bottles.');
    if (p.old > p.qty) return fail('Old bottles handed in cannot be more than the bottles given.');
    if (p.old > 0) {
      var brand = db.oldBrands.filter(function (b) { return b.id === input.brandId && b.active; })[0];
      if (!brand) return fail('Select an accepted old-bottle brand.');
      if (brand.bottle !== input.bt) return fail(brand.name + ' bottles are accepted only for ' + Q.btName(brand.bottle) + '.');
    }
    if (p.price == null || p.deposit == null) return fail('Price or deposit not set for this bottle type.');
    var paid = num(input.paid);
    var g = creditGate(c, p.amount, paid, input); if (g) return g;
    var fx = newFx(c.id);
    fxS(fx, input.bt, 'filled', -p.qty);
    fxS(fx, input.bt, 'customers', p.qty);
    fxS(fx, input.bt, 'empty', p.old); // BR-02: joins the pool as an empty
    fxC(fx, 'held', input.bt, p.qty);
    var txn = {
      id: seq('txn', 'T', 6), kind: 'first', billNo: sysBill(), paperNo: paper(input), date: input.date, custId: c.id,
      lines: [{ bt: input.bt, filled: p.qty, empties: 0, price: p.price, deposit: p.deposit, deposits: p.deposits, old: p.old, brandId: input.brandId || null, amount: p.amount }],
      amount: p.amount, paid: paid, payMode: paid ? 'Cash' : '', note: (input.note || '').trim(),
      split: { water: p.water, deposit: p.depositAmt, product: 0 }, oldBottles: p.old,
      override: input.approveOverLimit && Q.creditCheck(c, p.amount - paid).exceeds ? currentUser().name : null
    };
    var r = commit(txn, fx, 'First purchase for ' + c.name + ': ' + p.qty + ' × ' + Q.btShort(input.bt) + ', deposits ' + p.deposits + ', old bottles ' + p.old + ', ' + money(p.amount));
    if (r.ok) { r.schedule = advanceSchedule(c, input.date); save(); }
    return r;
  };

  /* FR-39 – extra order: bottles beyond the current holding need a deposit (or old bottle). */
  Ops.previewExtra = function (input) {
    var c = Q.customer(input.custId);
    var F = int(input.filled), E = int(input.empties), old = int(input.oldBottles);
    var price = c ? Q.price(c, input.bt, input.date || today()) : null;
    var dep = Q.deposit(input.bt, input.date || today());
    var newB = Math.max(0, F - E);
    var deposits = Math.max(0, newB - old);
    return { filled: F, empties: E, old: old, newBottles: newB, deposits: deposits, price: price, deposit: dep, water: F * (price || 0), depositAmt: deposits * (dep || 0), amount: F * (price || 0) + deposits * (dep || 0) };
  };
  Ops.extraOrder = function (input) {
    var cc = checkCustomer(input.custId); if (cc.err) return cc.err;
    var c = cc.c;
    var e = checkDate(input.date) || checkBill(paper(input)); if (e) return e;
    var p = Ops.previewExtra(input);
    if (p.filled < 1) return fail('Enter the filled bottles ordered.');
    if (p.empties > p.filled) return fail('Empties returned are more than filled bottles. Use Bill entry or Collect empties for returns.');
    if (p.empties > (c.held[input.bt] || 0)) return fail('Empties returned cannot be more than the bottles the customer holds (' + (c.held[input.bt] || 0) + ').');
    if (p.old > p.newBottles) return fail('Old bottles handed in cannot be more than the new bottles (' + p.newBottles + ').');
    if (p.old > 0) {
      var brand = db.oldBrands.filter(function (b) { return b.id === input.brandId && b.active; })[0];
      if (!brand) return fail('Select an accepted old-bottle brand.');
      if (brand.bottle !== input.bt) return fail(brand.name + ' bottles are accepted only for ' + Q.btName(brand.bottle) + '.');
    }
    var paid = num(input.paid);
    var g = creditGate(c, p.amount, paid, input); if (g) return g;
    var fx = newFx(c.id);
    fxS(fx, input.bt, 'filled', -p.filled);
    fxS(fx, input.bt, 'empty', p.empties + p.old);
    fxS(fx, input.bt, 'customers', p.filled - p.empties);
    fxC(fx, 'held', input.bt, p.newBottles);
    var txn = {
      id: seq('txn', 'T', 6), kind: 'extra', billNo: sysBill(), paperNo: paper(input), date: input.date, custId: c.id,
      lines: [{ bt: input.bt, filled: p.filled, empties: p.empties, price: p.price, deposit: p.deposit, deposits: p.deposits, old: p.old, newBottles: p.newBottles, brandId: input.brandId || null, amount: p.amount }],
      amount: p.amount, paid: paid, payMode: paid ? 'Cash' : '', note: (input.note || '').trim(),
      split: { water: p.water, deposit: p.depositAmt, product: 0 }, oldBottles: p.old,
      override: input.approveOverLimit && Q.creditCheck(c, p.amount - paid).exceeds ? currentUser().name : null
    };
    return commit(txn, fx, 'Extra order for ' + c.name + ': ' + p.filled + ' × ' + Q.btShort(input.bt) + ', new bottles ' + p.newBottles + ', deposits ' + p.deposits + ', ' + money(p.amount));
  };

  /* FR-36 – empties left at the door collected on the next trip. */
  Ops.collectEmpties = function (input) {
    var cc = checkCustomer(input.custId); if (cc.err) return cc.err;
    var c = cc.c;
    var e = checkDate(input.date); if (e) return e;
    var qty = int(input.qty);
    if (qty < 1) return fail('Enter the number of empties collected.');
    if (qty > (c.toCollect[input.bt] || 0)) return fail('Only ' + (c.toCollect[input.bt] || 0) + ' ' + Q.btShort(input.bt) + ' empties are waiting to be collected from ' + c.name + '.');
    if (paper(input)) { var b = checkBill(paper(input)); if (b) return b; }
    var fx = newFx(c.id);
    fxS(fx, input.bt, 'empty', qty);
    fxS(fx, input.bt, 'customers', -qty);
    fxC(fx, 'toCollect', input.bt, -qty);
    var txn = { id: seq('txn', 'T', 6), kind: 'collect', billNo: sysBill(), paperNo: paper(input), date: input.date, custId: c.id, lines: [{ bt: input.bt, filled: 0, empties: qty }], amount: 0, paid: 0, note: (input.note || '').trim(), split: { water: 0, deposit: 0, product: 0 } };
    return commit(txn, fx, 'Collected ' + qty + ' × ' + Q.btShort(input.bt) + ' empties from ' + c.name);
  };

  /* FR-37 / BR-06 – customer-damaged bottle. */
  Ops.previewCustDamage = function (input) {
    var c = Q.customer(input.custId);
    var qty = int(input.qty);
    var dep = Q.deposit(input.bt, input.date || today());
    var price = c ? Q.price(c, input.bt, input.date || today()) : 0;
    if (input.mode === 'reduce') return { qty: qty, deposit: dep, price: price, depositAmt: 0, water: 0, amount: 0 };
    var water = input.chargeWater ? qty * (price || 0) : 0;
    return { qty: qty, deposit: dep, price: price, depositAmt: qty * (dep || 0), water: water, amount: qty * (dep || 0) + water };
  };
  Ops.customerDamage = function (input) {
    var cc = checkCustomer(input.custId); if (cc.err) return cc.err;
    var c = cc.c;
    var e = checkDate(input.date); if (e) return e;
    if (paper(input)) { var b = checkBill(paper(input)); if (b) return b; }
    var p = Ops.previewCustDamage(input);
    if (p.qty < 1) return fail('Enter the number of damaged bottles.');
    if (p.qty > (c.held[input.bt] || 0)) return fail(c.name + ' holds only ' + (c.held[input.bt] || 0) + ' ' + Q.btShort(input.bt) + ' bottles.');
    if (!input.reason) return fail('Enter how the bottle was damaged.');
    var paid = num(input.paid);
    var g = creditGate(c, p.amount, paid, input); if (g) return g;
    var fx = newFx(c.id);
    fxS(fx, input.bt, 'customers', -p.qty);
    fxS(fx, input.bt, 'writtenOff', p.qty);
    if (input.mode === 'reduce') {
      fxC(fx, 'held', input.bt, -p.qty);
    } else {
      fxS(fx, input.bt, 'filled', -p.qty); // replacement filled bottle given
      fxS(fx, input.bt, 'customers', p.qty);
    }
    var txn = {
      id: seq('txn', 'T', 6), kind: 'custdamage', billNo: sysBill(), paperNo: paper(input), date: input.date, custId: c.id,
      lines: [{ bt: input.bt, filled: input.mode === 'reduce' ? 0 : p.qty, empties: 0, damaged: p.qty, deposit: p.deposit, price: p.price, amount: p.amount }],
      amount: p.amount, paid: paid, payMode: paid ? 'Cash' : '', note: input.reason + (input.note ? ' – ' + input.note : ''), mode: input.mode,
      split: { water: p.water, deposit: p.depositAmt, product: 0 }
    };
    var r = commit(txn, fx, 'Customer damage – ' + c.name + ': ' + p.qty + ' × ' + Q.btShort(input.bt) + (input.mode === 'reduce' ? ', holding reduced' : ', new bottle charged ' + money(p.amount)));
    if (r.ok) {
      db.damages.push({ id: seq('dmg', 'D', 4), date: input.date, bt: input.bt, qty: p.qty, resp: 'Customer', location: 'At customer', reason: input.reason, note: input.note || '', custId: c.id, ref: txn.id, user: currentUser().name });
      save();
    }
    return r;
  };

  /* FR-38 / BR-07 – settle bottles owed by returning bottles or paying the deposit. */
  Ops.settleOwed = function (input) {
    var cc = checkCustomer(input.custId); if (cc.err) return cc.err;
    var c = cc.c;
    var e = checkDate(input.date); if (e) return e;
    var qty = int(input.qty);
    if (qty < 1) return fail('Enter the number of bottles.');
    if (qty > (c.owed[input.bt] || 0)) return fail(c.name + ' owes only ' + (c.owed[input.bt] || 0) + ' ' + Q.btShort(input.bt) + ' bottles.');
    var dep = Q.deposit(input.bt, input.date);
    var amount = input.method === 'deposit' ? qty * dep : 0;
    var paid = num(input.paid);
    var fx = newFx(c.id);
    fxC(fx, 'owed', input.bt, -qty);
    if (input.method === 'deposit') {
      fxC(fx, 'held', input.bt, qty); // customer keeps the bottle and now holds it
    } else {
      fxS(fx, input.bt, 'empty', qty);
      fxS(fx, input.bt, 'customers', -qty);
    }
    var txn = {
      id: seq('txn', 'T', 6), kind: 'owedsettle', billNo: sysBill(), paperNo: paper(input), date: input.date, custId: c.id,
      lines: [{ bt: input.bt, filled: 0, empties: input.method === 'deposit' ? 0 : qty, deposits: input.method === 'deposit' ? qty : 0, deposit: dep, amount: amount }],
      amount: amount, paid: paid, payMode: paid ? 'Cash' : '', method: input.method, note: (input.note || '').trim(),
      split: { water: 0, deposit: amount, product: 0 }
    };
    return commit(txn, fx, 'Bottles owed settled – ' + c.name + ': ' + qty + ' × ' + Q.btShort(input.bt) + (input.method === 'deposit' ? ' by deposit ' + money(amount) : ' returned'));
  };

  /* FR-40 – other product sale. */
  Ops.productSale = function (input) {
    var c = null;
    if (input.custId) { var cc = checkCustomer(input.custId); if (cc.err) return cc.err; c = cc.c; }
    else if (!input.walkIn) return fail('Select a customer or enter a walk-in name.');
    var e = checkDate(input.date) || checkBill(paper(input)); if (e) return e;
    var items = (input.items || []).filter(function (i) { return i.pid && int(i.qty) > 0; });
    if (!items.length) return fail('Add at least one product.');
    var fx = newFx(c ? c.id : null), amount = 0, cost = 0;
    items.forEach(function (i) {
      var p = Q.product(i.pid);
      i.qty = int(i.qty); i.price = num(i.price != null && i.price !== '' ? i.price : p.price);
      i.amount = i.qty * i.price; i.cost = i.qty * p.cost;
      amount += i.amount; cost += i.cost;
      fxP(fx, i.pid, -i.qty);
    });
    var paid = num(input.paid);
    if (!c && paid < amount) return fail('Walk-in sales must be paid in full.');
    if (c) { var g = creditGate(c, amount, paid, input); if (g) return g; }
    var txn = {
      id: seq('txn', 'T', 6), kind: 'product', billNo: sysBill(), paperNo: paper(input), date: input.date, custId: c ? c.id : null, walkIn: c ? null : input.walkIn,
      lines: items, amount: amount, paid: paid, payMode: paid ? (input.payMode || 'Cash') : '', note: (input.note || '').trim(),
      split: { water: 0, deposit: 0, product: amount }, cost: cost,
      override: c && input.approveOverLimit && Q.creditCheck(c, amount - paid).exceeds ? currentUser().name : null
    };
    return commit(txn, fx, 'Product sale ' + (c ? c.name : input.walkIn) + ': ' + items.map(function (i) { return i.qty + ' × ' + Q.product(i.pid).name; }).join(', ') + ', ' + money(amount));
  };

  /* BR-14 – corrections by reversal entry. */
  Ops.reverse = function (txnId, reason) {
    var t = Q.txn(txnId);
    if (!t) return fail('Transaction not found.');
    if (t.kind === 'reversal' || t.kind === 'opening') return fail('This entry cannot be reversed.');
    if (t.reversedBy) return fail('Already reversed by ' + t.reversedBy + '.');
    if (!reason || !reason.trim()) return fail('Enter the reason for the reversal.');
    var fx = negFx(t.fx);
    var errs = checkFx(fx, 1);
    if (errs.length) return fail('Cannot reverse: ' + errs.join(' '), 'STOCK');
    applyFx(fx, 1);
    var r = {
      id: seq('txn', 'T', 6), kind: 'reversal', billNo: sysBill(), date: bizDate(), custId: t.custId, walkIn: t.walkIn,
      lines: t.lines, amount: -t.amount, paid: -t.paid, note: 'Reversal of ' + (t.billNo ? 'bill ' + t.billNo : t.id) + ': ' + reason.trim(),
      reversalOf: t.id, origKind: t.kind, fx: fx, user: currentUser().name, created: nowTs(),
      split: { water: -t.split.water, deposit: -t.split.deposit, product: -t.split.product }, cost: t.cost ? -t.cost : 0
    };
    db.txns.push(r);
    t.reversedBy = r.id;
    logMovement(r.date, r.billNo || r.id, 'Reversal of ' + KINDS[t.kind], fx);
    audit('Reverse', KINDS[t.kind], t.billNo ? 'Bill ' + t.billNo : t.id, 'Reversal entry ' + r.id + ' – ' + reason.trim());
    if (t.kind === 'custdamage') {
      db.damages.forEach(function (d) { if (d.ref === t.id) d.reversed = true; });
    }
    save();
    return ok({ txn: r });
  };

  /* FR-47/FR-50 – payments received. */
  Ops.receivePayment = function (input) {
    var c = Q.customer(input.custId);
    if (!c) return fail('Select a customer.');
    var e = checkDate(input.date); if (e) return e;
    var amt = num(input.amount);
    if (amt <= 0) return fail('Enter the amount received.');
    if (PAY_MODES.indexOf(input.mode) < 0) return fail('Select how the money was received.');
    if (input.mode === 'Cheque' && !input.ref) return fail('Enter the cheque number.');
    if (input.mode === 'Bank transfer' && !input.ref) return fail('Enter the bank reference.');
    var bal = Q.balance(c.id);
    var p = { id: seq('pay', 'RC-', 5), date: input.date, custId: c.id, amount: amt, mode: input.mode, ref: input.ref || '', bank: input.bank || '', chequeDate: input.chequeDate || '', note: input.note || '', invoiceId: input.invoiceId || '', user: currentUser().name, created: nowTs(), balanceBefore: bal };
    db.payments.push(p);
    audit('Create', 'Payment', p.id, c.name + ': ' + money(amt) + ' by ' + input.mode + (input.ref ? ' (' + input.ref + ')' : '') + (amt < bal ? ' – part payment' : ''));
    save();
    return ok({ payment: p, balanceAfter: bal - amt });
  };

  /* FR-49 – monthly invoices. */
  Ops.invoicePreview = function (custId, ym) {
    var from = D.monthStart(ym), to = D.monthEnd(ym);
    var txns = db.txns.filter(function (t) { return t.custId === custId && t.date >= from && t.date <= to && t.kind !== 'opening'; });
    var pays = db.payments.filter(function (p) { return p.custId === custId && p.date >= from && p.date <= to; });
    var charges = 0, paidOnBills = 0, paySum = 0;
    txns.forEach(function (t) { charges += t.amount; paidOnBills += t.paid; });
    pays.forEach(function (p) { paySum += p.amount; });
    var opening = Q.balanceAt(custId, from, true);
    return { custId: custId, period: ym, from: from, to: to, txnIds: txns.map(function (t) { return t.id; }), deliveries: txns.length, charges: charges, paidOnBills: paidOnBills, opening: opening, payments: paySum, closing: opening + charges - paidOnBills - paySum };
  };
  Ops.generateInvoices = function (ym, custIds) {
    var created = [], skipped = [];
    if (D.monthEnd(ym) > today() && !ctx.sim) { /* allow mid-month draft but flag it */ }
    custIds.forEach(function (id) {
      var c = Q.customer(id);
      if (!c || c.payType !== 'Monthly bill') { skipped.push({ id: id, why: 'Not a Monthly bill customer' }); return; }
      if (db.invoices.some(function (i) { return i.custId === id && i.period === ym; })) { skipped.push({ id: id, why: 'Invoice already exists' }); return; }
      var pv = Ops.invoicePreview(id, ym);
      if (!pv.deliveries && pv.closing <= 0) { skipped.push({ id: id, why: 'No deliveries and nothing due' }); return; }
      var inv = {
        id: 'INV-' + ym.replace('-', '').slice(2) + '-' + seq('inv', '', 4), custId: id, period: ym, date: ctx.sim ? D.add(D.monthEnd(ym), 1) : today(),
        due: D.add(ctx.sim ? D.add(D.monthEnd(ym), 1) : today(), c.terms || 30), from: pv.from, to: pv.to, txnIds: pv.txnIds,
        charges: pv.charges, paidOnBills: pv.paidOnBills, opening: pv.opening, payments: pv.payments, amount: pv.charges - pv.paidOnBills, total: pv.closing,
        user: currentUser().name, created: nowTs()
      };
      db.invoices.push(inv);
      created.push(inv);
      audit('Create', 'Invoice', inv.id, c.name + ' – ' + D.monthLabel(ym) + ': ' + money(inv.amount) + ' (total due ' + money(inv.total) + ')');
    });
    save();
    return ok({ created: created, skipped: skipped });
  };

  /* ---------- master data ---------- */
  Ops.saveBottleType = function (b, isNew) {
    if (!b.name || !(num(b.litres) > 0)) return fail('Name and size in litres are required.');
    if (isNew) b.id = 'B' + String(num(b.litres)).replace('.', '_');
    var ex = Q.bt(b.id);
    if (isNew && ex) return fail('A ' + num(b.litres) + 'L bottle type already exists (' + ex.name + ').');
    if (isNew) {
      db.bottleTypes.push({ id: b.id, name: b.name, litres: num(b.litres), active: !!b.active });
      db.stock[b.id] = { empty: 0, factory: 0, filled: 0, customers: 0, writtenOff: 0 };
      audit('Create', 'Bottle type', b.id, b.name);
    } else {
      var before = ex.name + ', ' + ex.litres + 'L, ' + (ex.active ? 'Active' : 'Inactive');
      ex.name = b.name; ex.litres = num(b.litres); ex.active = !!b.active;
      audit('Update', 'Bottle type', b.id, before + ' → ' + ex.name + ', ' + ex.litres + 'L, ' + (ex.active ? 'Active' : 'Inactive'));
    }
    save();
    return ok();
  };
  Ops.saveProduct = function (p, isNew) {
    if (!p.name || !(num(p.price) > 0)) return fail('Name and selling price are required.');
    if (isNew) {
      var np = { id: seq('prod', 'P', 2), name: p.name, price: num(p.price), cost: num(p.cost), stock: int(p.stock), active: !!p.active };
      db.products.push(np);
      audit('Create', 'Product', np.id, np.name + ' – ' + money(np.price));
    } else {
      var ex = Q.product(p.id);
      var before = money(ex.price);
      ex.name = p.name; ex.price = num(p.price); ex.cost = num(p.cost); ex.active = !!p.active;
      audit('Update', 'Product', ex.id, ex.name + ': price ' + before + ' → ' + money(ex.price));
    }
    save();
    return ok();
  };
  Ops.saveCustomerType = function (t, isNew) {
    if (!t.id) return fail('Enter the customer type name.');
    if (isNew) {
      if (db.customerTypes.some(function (x) { return x.id.toLowerCase() === t.id.toLowerCase(); })) return fail('Customer type already exists.');
      db.customerTypes.push({ id: t.id, desc: t.desc || '', active: true });
      audit('Create', 'Customer type', t.id, t.desc || '');
    } else {
      var ex = db.customerTypes.filter(function (x) { return x.id === t.id; })[0];
      ex.desc = t.desc; ex.active = !!t.active;
      audit('Update', 'Customer type', t.id, (ex.active ? 'Active' : 'Inactive') + ' – ' + ex.desc);
    }
    save();
    return ok();
  };
  Ops.setPrice = function (key, price, effective, reason) {
    price = num(price);
    if (!(price > 0)) return fail('Enter a price greater than zero.');
    if (!effective) return fail('Enter the effective date (DD/MM/YYYY).');
    if (!reason) return fail('Enter the reason for the change.');
    var cur = Q.priceEntry(key, effective);
    db.priceHistory.push({ id: seq('price', 'PH', 4), key: key, price: price, effective: effective, reason: reason, by: currentUser().name, created: nowTs() });
    audit('Update', 'Price', key.replace(/\|/g, ' / '), (cur ? money(cur.price) : 'none') + ' → ' + money(price) + ' from ' + D.dmy(effective) + ' – ' + reason);
    save();
    return ok();
  };
  Ops.saveBrand = function (b, isNew) {
    if (!b.name) return fail('Enter the brand name.');
    if (isNew) {
      var nb = { id: seq('brand', 'OB', 1), name: b.name, bottle: b.bottle, active: true, added: today(), note: b.note || '' };
      db.oldBrands.push(nb);
      audit('Create', 'Accepted old-bottle brand', nb.name, 'For ' + Q.btName(nb.bottle));
    } else {
      var ex = db.oldBrands.filter(function (x) { return x.id === b.id; })[0];
      ex.active = !!b.active; ex.note = b.note; ex.bottle = b.bottle;
      audit('Update', 'Accepted old-bottle brand', ex.name, ex.active ? 'Active' : 'Inactive');
    }
    save();
    return ok();
  };

  /* ---------- customers ---------- */
  Ops.saveCustomer = function (f, isNew) {
    if (!f.name || !f.address || !f.phone || !f.type || !f.area) return fail('Name, address, phone, customer type and area are required (FR-08).');
    if (!/^0\d{2} ?\d{3} ?\d{4}$/.test(f.phone.trim())) return fail('Phone must be a Sri Lankan number, e.g. 077 123 4567 or 011 285 4471.');
    if (DAYS.indexOf(f.day) < 0 || !(int(f.cycle) >= 1)) return fail('Set the delivery day and cycle (FR-09).');
    if (!f.nextDelivery) return fail('Enter the next delivery date (DD/MM/YYYY).');
    if (DAYS[D.dow(f.nextDelivery)] !== f.day) return fail('Next delivery date ' + D.dmy(f.nextDelivery) + ' is a ' + D.dayName(f.nextDelivery) + ', but the delivery day is ' + f.day + '.');
    if (PAY_TYPES.indexOf(f.payType) < 0) return fail('Select the payment type (FR-10).');
    if (f.payType !== 'Cash' && !(num(f.limit) > 0)) return fail('Credit and Monthly bill customers need a credit limit (FR-10).');
    if (f.payType !== 'Cash' && !(int(f.terms) > 0)) return fail('Enter payment terms in days (FR-10).');
    var dupPhone = db.customers.filter(function (c) { return c.phone.replace(/\s/g, '') === f.phone.replace(/\s/g, '') && c.id !== f.id; })[0];
    if (dupPhone && !f.allowDupPhone) return fail('Phone number already used by ' + dupPhone.id + ' – ' + dupPhone.name + '.', 'DUP_PHONE');
    var usual = {};
    Object.keys(f.usual || {}).forEach(function (bt) { usual[bt] = int(f.usual[bt]); });
    var c;
    if (isNew) {
      c = {
        id: seq('cust', 'C', 4), name: f.name.trim(), address: f.address.trim(), area: f.area, phone: f.phone.trim(), type: f.type,
        day: f.day, cycle: int(f.cycle), nextDelivery: f.nextDelivery, usual: usual, payType: f.payType,
        limit: f.payType === 'Cash' ? 0 : num(f.limit), terms: f.payType === 'Cash' ? 0 : int(f.terms),
        since: f.since || today(), status: 'Active', held: {}, toCollect: {}, owed: {}, notes: f.notes || ''
      };
      Q.activeBottles().forEach(function (b) { c.held[b.id] = 0; c.toCollect[b.id] = 0; c.owed[b.id] = 0; });
      db.customers.push(c);
      audit('Create', 'Customer', c.id, c.name + ' – ' + c.type + ', ' + c.area + ', ' + c.payType);
    } else {
      c = Q.customer(f.id);
      var changes = [];
      ['name', 'address', 'area', 'phone', 'type', 'day', 'payType', 'notes'].forEach(function (k) { if ((c[k] || '') !== (f[k] || '')) changes.push(k + ': ' + (c[k] || '–') + ' → ' + (f[k] || '–')); });
      if (c.cycle !== int(f.cycle)) changes.push('cycle: ' + c.cycle + ' → ' + int(f.cycle) + ' wk');
      if (c.nextDelivery !== f.nextDelivery) changes.push('next delivery: ' + D.dmy(c.nextDelivery) + ' → ' + D.dmy(f.nextDelivery));
      if (c.limit !== num(f.limit) && f.payType !== 'Cash') changes.push('credit limit: ' + money(c.limit) + ' → ' + money(num(f.limit)));
      c.name = f.name.trim(); c.address = f.address.trim(); c.area = f.area; c.phone = f.phone.trim(); c.type = f.type;
      c.day = f.day; c.cycle = int(f.cycle); c.nextDelivery = f.nextDelivery; c.usual = usual; c.payType = f.payType;
      c.limit = f.payType === 'Cash' ? 0 : num(f.limit); c.terms = f.payType === 'Cash' ? 0 : int(f.terms);
      c.notes = f.notes || '';
      audit('Update', 'Customer', c.id, changes.join('; ') || 'No field changes');
    }
    save();
    return ok({ customer: c });
  };
  /* FR-05 – set an agreed water price for one customer and bottle type (Admin). from may be in the future
     (scheduled); until is optional (e.g. a tender period). The agreement in force before `from` is closed the day before. */
  Ops.setCustPrice = function (input) {
    var c = Q.customer(input.custId);
    if (!c) return fail('Customer not found.');
    if (!Q.bt(input.bt)) return fail('Select the bottle type.');
    var price = num(input.price);
    if (!(price > 0)) return fail('Enter the agreed price per bottle.');
    if (!input.from) return fail('Enter the date the agreed price starts (DD/MM/YYYY).');
    if (input.until && input.until < input.from) return fail('The end date cannot be before the start date.');
    if (!input.reason || !String(input.reason).trim()) return fail('Enter the reason for the agreed price (e.g. about 3,500 bottles a month).');
    var before = Q.price(c, input.bt, input.from), std = Q.stdPrice(input.bt, c.type, input.from);
    db.custPrices.forEach(function (e) {
      if (e.custId !== c.id || e.bt !== input.bt || e.cancelled) return;
      if (e.from >= input.from) { if (!input.until || e.from <= input.until) { e.cancelled = true; e.cancelReason = 'Replaced by a new agreed price from ' + D.dmy(input.from); } }
      else if (!e.until || e.until >= input.from) e.until = D.add(input.from, -1);
    });
    var rec = { id: seq('cprice', 'CP', 4), custId: c.id, bt: input.bt, price: price, std: std, from: input.from, until: input.until || '', reason: String(input.reason).trim(), quoteId: input.quoteId || '', by: currentUser().name, created: nowTs() };
    db.custPrices.push(rec);
    audit('Update', 'Customer price', c.id + ' ' + Q.btShort(input.bt), 'Agreed price ' + money(before) + ' → ' + money(price) + ' from ' + D.dmy(input.from) + (input.until ? ' until ' + D.dmy(input.until) : '') + ' (standard ' + money(std) + ') – ' + rec.reason);
    save();
    return ok({ entry: rec });
  };
  /* Go back to the standard price list from a date. */
  Ops.endCustPrice = function (custId, bt, from, reason) {
    var c = Q.customer(custId);
    if (!c) return fail('Customer not found.');
    if (!from) return fail('Enter the date the standard price starts again.');
    if (!reason) return fail('Enter the reason.');
    var n = 0;
    db.custPrices.forEach(function (e) {
      if (e.custId !== custId || e.bt !== bt || e.cancelled) return;
      if (e.from >= from) { e.cancelled = true; e.cancelReason = reason; n++; }
      else if (!e.until || e.until >= from) { e.until = D.add(from, -1); n++; }
    });
    if (!n) return fail(c.name + ' has no agreed ' + Q.btShort(bt) + ' price on or after ' + D.dmy(from) + '.');
    audit('Update', 'Customer price', c.id + ' ' + Q.btShort(bt), 'Back to the standard price from ' + D.dmy(from) + ' – ' + reason);
    save();
    return ok();
  };

  /* FR-13 – deactivate (reason required) or reactivate; history is kept. date = from when (default today). */
  Ops.setCustomerStatus = function (id, active, reason, date) {
    var c = Q.customer(id);
    if (!c) return fail('Customer not found.');
    if (!active && !reason) return fail('Enter the reason for deactivating.');
    date = date || today();
    if (date > today()) return fail('The status date cannot be in the future.');
    c.status = active ? 'Active' : 'Inactive';
    c.statusReason = active ? '' : reason;
    c.statusDate = date;
    // A reactivated customer gets the next delivery day from today, so they show on the delivery list again.
    if (active && c.nextDelivery < today()) c.nextDelivery = Q.nextAligned(c, D.add(today(), -1));
    audit('Update', 'Customer', c.id, (active ? 'Reactivated' : 'Deactivated') + ' from ' + D.dmy(date) + (reason ? ' – ' + reason : '') + '. History kept.');
    save();
    return ok();
  };

  /* ---------- purchasing ---------- */
  Ops.saveSupplier = function (s, isNew) {
    if (!s.name || !s.phone) return fail('Supplier name and phone are required.');
    if (!s.items || !s.items.length) return fail('Select at least one item the supplier supplies.');
    if (isNew) {
      var ns = { id: seq('sup', 'S', 2), name: s.name, contact: s.contact || '', phone: s.phone, email: s.email || '', address: s.address || '', items: s.items || [], terms: int(s.terms), active: true };
      db.suppliers.push(ns);
      audit('Create', 'Supplier', ns.id, ns.name);
    } else {
      var ex = Q.supplier(s.id);
      Object.keys(s).forEach(function (k) { if (k !== 'id') ex[k] = k === 'terms' ? int(s[k]) : s[k]; });
      audit('Update', 'Supplier', ex.id, ex.name);
    }
    save();
    return ok();
  };
  Ops.createQR = function (input) {
    var e = checkDate(input.date); if (e) return e;
    var items = (input.items || []).filter(function (i) { return i.item && int(i.qty) > 0; }).map(function (i) { return { item: i.item, qty: int(i.qty) }; });
    if (!items.length) return fail('Add at least one item with a quantity.');
    if (!input.suppliers || input.suppliers.length < 1) return fail('Select the suppliers asked to quote.');
    var qr = { id: seq('qr', 'QR-2026-', 3), date: input.date, requiredBy: input.requiredBy || '', items: items, suppliers: input.suppliers, note: input.note || '', status: 'Open', user: currentUser().name };
    db.qrs.push(qr);
    audit('Create', 'Quotation request', qr.id, items.map(function (i) { return i.qty + ' × ' + Q.itemName(i.item); }).join(', ') + ' – ' + input.suppliers.length + ' supplier(s)');
    save();
    return ok({ qr: qr });
  };
  Ops.recordQuote = function (input) {
    var qr = Q.qr(input.qrId);
    if (!qr) return fail('Select the quotation request.');
    if (qr.status === 'Selected' || qr.status === 'Closed') return fail('A quotation has already been chosen for ' + qr.id + '.');
    if (!input.supplierId) return fail('Select the supplier.');
    var e = checkDate(input.date); if (e) return e;
    if (!input.validUntil) return fail('Enter the validity date (DD/MM/YYYY).');
    if (db.quotes.some(function (q) { return q.qrId === qr.id && q.supplierId === input.supplierId; })) return fail(Q.supplier(input.supplierId).name + ' already has a quotation recorded for ' + qr.id + '.');
    var lines = (input.lines || []).map(function (l) { return { item: l.item, price: l.price === '' || l.price == null ? null : num(l.price) }; });
    if (!lines.some(function (l) { return l.price > 0; })) return fail('Enter at least one unit price.');
    var q = { id: seq('quote', 'SQ-', 4), qrId: qr.id, supplierId: input.supplierId, ref: input.ref || '', date: input.date, lines: lines, deliveryDays: int(input.deliveryDays), validUntil: input.validUntil, notes: input.notes || '', attachment: input.attachment || '', selected: false, user: currentUser().name };
    db.quotes.push(q);
    qr.status = 'Quotes received';
    audit('Create', 'Quotation', q.id, Q.supplier(q.supplierId).name + ' for ' + qr.id + ' – total ' + money(Q.quoteTotal(q)) + (q.attachment ? ' (attached ' + q.attachment + ')' : ''));
    save();
    return ok({ quote: q });
  };
  /* FR-17 / BR-10 – the owner chooses manually. */
  Ops.selectQuote = function (quoteId, reason) {
    var q = Q.quote(quoteId); var qr = Q.qr(q.qrId);
    if (qr.status === 'Selected' || qr.status === 'Closed') return fail('A quotation has already been chosen for this request.');
    if (!reason) return fail('Add the reason for choosing this quotation.');
    if (q.validUntil < bizDate()) return fail('This quotation expired on ' + D.dmy(q.validUntil) + '. Ask the supplier to re-quote.');
    q.selected = true; q.reason = reason; q.selectedBy = currentUser().name; q.selectedOn = bizDate();
    qr.status = 'Selected';
    audit('Approve', 'Quotation', q.id, 'Chosen for ' + qr.id + ': ' + Q.supplier(q.supplierId).name + ' – ' + reason);
    save();
    return ok();
  };
  Ops.createPO = function (quoteId) {
    var q = Q.quote(quoteId);
    if (!q || !q.selected) return fail('A purchase order can be created only from the chosen quotation (BR-10).');
    var exists = db.pos.filter(function (p) { return p.quoteId === q.id && p.status !== 'Cancelled'; })[0];
    if (exists) return fail('Purchase order ' + exists.id + ' already exists for this quotation.', 'EXISTS', { poId: exists.id });
    var qr = Q.qr(q.qrId);
    var lines = [];
    qr.items.forEach(function (it) {
      var ql = q.lines.filter(function (l) { return l.item === it.item; })[0];
      if (ql && ql.price > 0) lines.push({ item: it.item, qty: it.qty, price: ql.price, received: 0, damaged: 0 });
    });
    var po = { id: seq('po', 'PO-2026-', 4), qrId: qr.id, quoteId: q.id, supplierId: q.supplierId, date: bizDate(), expected: D.add(bizDate(), q.deliveryDays || 7), lines: lines, status: 'Draft', notes: '', user: currentUser().name, history: [{ ts: nowTs(), status: 'Draft', user: currentUser().name }] };
    db.pos.push(po);
    qr.status = 'Closed';
    audit('Create', 'Purchase order', po.id, 'From ' + q.id + ' (' + Q.supplier(q.supplierId).name + ') – ' + money(Q.poTotal(po)));
    save();
    return ok({ po: po });
  };
  Ops.updatePO = function (poId, lines, expected, notes) {
    var po = Q.po(poId);
    if (po.status !== 'Draft') return fail('Only Draft purchase orders can be edited.');
    if (!lines.some(function (l) { return int(l.qty) > 0; })) return fail('At least one line needs a quantity.');
    var before = money(Q.poTotal(po));
    po.lines = lines.filter(function (l) { return int(l.qty) > 0; }).map(function (l) { return { item: l.item, qty: int(l.qty), price: num(l.price), received: 0, damaged: 0 }; });
    po.expected = expected || po.expected; po.notes = notes || '';
    audit('Update', 'Purchase order', po.id, 'Edited before approval: ' + before + ' → ' + money(Q.poTotal(po)));
    save();
    return ok();
  };
  Ops.setPOStatus = function (poId, status, reason) {
    var po = Q.po(poId);
    var allowed = { Approved: ['Draft'], Sent: ['Approved'], Cancelled: ['Draft', 'Approved', 'Sent'] };
    if (!allowed[status] || allowed[status].indexOf(po.status) < 0) return fail('Cannot change ' + po.status + ' to ' + status + '.');
    if (status === 'Cancelled' && !reason) return fail('Enter the reason for cancelling.');
    po.status = status;
    if (status === 'Approved') { po.approvedBy = currentUser().name; po.approvedOn = bizDate(); }
    if (status === 'Cancelled') po.cancelReason = reason;
    po.history.push({ ts: nowTs(), status: status, user: currentUser().name, note: reason || '' });
    audit(status === 'Approved' ? 'Approve' : 'Update', 'Purchase order', po.id, 'Status → ' + status + (reason ? ' – ' + reason : ''));
    save();
    return ok();
  };
  /* FR-20 / BR-10 – goods receipt against an approved PO. */
  Ops.receiveGoods = function (input) {
    var po = Q.po(input.poId);
    if (!po) return fail('Select a purchase order.');
    if (['Approved', 'Sent', 'Partly received'].indexOf(po.status) < 0) return fail('Goods can be received only against an approved PO (BR-10). ' + po.id + ' is ' + po.status + '.');
    var e = checkDate(input.date); if (e) return e;
    var lines = [], any = false, err = null;
    po.lines.forEach(function (l, i) {
      var inL = input.lines[i] || {};
      var rec = int(inL.received), dmg = int(inL.damaged);
      if (dmg > rec) err = 'Damaged cannot be more than received for ' + Q.itemName(l.item) + '.';
      if (rec > l.qty - l.received) err = 'Received for ' + Q.itemName(l.item) + ' is more than the ' + (l.qty - l.received) + ' still due.';
      if (rec > 0) any = true;
      lines.push({ item: l.item, received: rec, damaged: dmg, price: l.price });
    });
    if (err) return fail(err);
    if (!any) return fail('Enter the quantities received.');
    var fx = newFx(null), value = 0;
    lines.forEach(function (l) {
      var good = l.received - l.damaged;
      value += good * l.price;
      if (Q.isBottle(l.item)) fxS(fx, l.item, 'empty', good); else fxP(fx, l.item, good);
    });
    applyFx(fx, 1);
    po.lines.forEach(function (l, i) { l.received += lines[i].received; l.damaged += lines[i].damaged; });
    var full = po.lines.every(function (l) { return l.received >= l.qty; });
    po.status = full ? 'Received' : 'Partly received';
    po.history.push({ ts: nowTs(), status: po.status, user: currentUser().name, note: 'GRN' });
    var g = { id: seq('grn', 'GRN-', 4), poId: po.id, supplierId: po.supplierId, date: input.date, deliveryNote: input.deliveryNote || '', lines: lines, value: value, note: input.note || '', user: currentUser().name };
    db.grns.push(g);
    logMovement(input.date, g.id, 'Goods received – ' + po.id, fx);
    audit('Create', 'Goods receipt', g.id, po.id + ': ' + lines.filter(function (l) { return l.received; }).map(function (l) { return l.received + ' × ' + Q.itemName(l.item) + (l.damaged ? ' (' + l.damaged + ' damaged)' : ''); }).join(', ') + ' – PO now ' + po.status);
    save();
    return ok({ grn: g });
  };
  Ops.supplierPayment = function (input) {
    var s = Q.supplier(input.supplierId);
    if (!s) return fail('Select a supplier.');
    var e = checkDate(input.date); if (e) return e;
    var amt = num(input.amount);
    if (amt <= 0) return fail('Enter the amount paid.');
    if (PAY_MODES.indexOf(input.mode) < 0) return fail('Select the payment method.');
    if (input.mode !== 'Cash' && !input.ref) return fail('Enter the cheque number or bank reference.');
    var p = { id: seq('spay', 'SP-', 4), supplierId: s.id, poId: input.poId || '', date: input.date, amount: amt, mode: input.mode, ref: input.ref || '', note: input.note || '', user: currentUser().name };
    db.supplierPayments.push(p);
    audit('Create', 'Supplier payment', p.id, s.name + ': ' + money(amt) + ' by ' + p.mode);
    save();
    return ok({ payment: p });
  };

  /* ---------- production ---------- */
  Ops.saveFactory = function (f, charges, isNew) {
    if (!f.name) return fail('Enter the factory name.');
    var fac;
    if (isNew) {
      fac = { id: seq('fac', 'F', 2), name: f.name, address: '', contact: '', phone: '', email: '', licence: '', terms: 30, charges: {}, active: true };
      db.factories.push(fac);
    } else fac = Q.factory(f.id);
    var before = JSON.stringify(fac.charges);
    Object.keys(f).forEach(function (k) { if (k !== 'id') fac[k] = k === 'terms' ? int(f[k]) : f[k]; });
    Object.keys(charges).forEach(function (bt) { if (num(charges[bt]) > 0) fac.charges[bt] = num(charges[bt]); });
    audit(isNew ? 'Create' : 'Update', 'Filling factory', fac.id + ' ' + fac.name, 'Charges per bottle ' + before + ' → ' + JSON.stringify(fac.charges));
    save();
    return ok();
  };
  /* FR-23 – send empties. One entry can send every bottle type at once (client request 08/10/2026):
     input.lines = [{bt, qty}] (or the single input.bt + input.qty). All lines are checked first, then one
     batch per bottle type is created under one dispatch number FD-xxxx. */
  Ops.dispatch = function (input) {
    var e = checkDate(input.date); if (e) return e;
    var fac = Q.factory(input.factoryId) || Q.activeFactories()[0];
    if (!fac || !fac.active) return fail('Select an active filling factory.');
    var lines = (input.lines || [{ bt: input.bt, qty: input.qty }]).map(function (l) { return { bt: l.bt, qty: int(l.qty) }; }).filter(function (l) { return l.qty > 0; });
    if (!lines.length) return fail('Enter the number of empty bottles sent for at least one bottle type.');
    var noCharge = lines.filter(function (l) { return !fac.charges[l.bt]; })[0];
    if (noCharge) return fail(fac.name + ' has no filling charge for ' + Q.btName(noCharge.bt) + '. Set it under Suppliers & Factories.');
    var fx = newFx(null);
    lines.forEach(function (l) { fxS(fx, l.bt, 'empty', -l.qty); fxS(fx, l.bt, 'factory', l.qty); });
    var errs = checkFx(fx, 1);
    if (errs.length) return fail(errs.join(' ') + ' Record a stock adjustment if the physical count differs (BR-12).', 'STOCK');
    applyFx(fx, 1);
    var no = seq('disp', 'FD-', 4), batches = [];
    lines.forEach(function (l) {
      var b = { id: seq('batch', 'FB-', 4), dispatchNo: no, date: input.date, bt: l.bt, qty: l.qty, vehicle: input.vehicle || '', note: input.note || '', returns: [], factoryId: fac.id, charge: fac.charges[l.bt], user: currentUser().name };
      db.batches.push(b);
      batches.push(b);
    });
    logMovement(input.date, no, 'Dispatched to factory', fx);
    audit('Create', 'Factory dispatch', no + ' (' + batches.map(function (b) { return b.id; }).join(', ') + ')', lines.map(function (l) { return l.qty + ' × ' + Q.btShort(l.bt); }).join(' + ') + ' empties sent to ' + fac.name);
    save();
    return ok({ dispatchNo: no, batches: batches, batch: batches[0] });
  };
  /* FR-24/26 / BR-11 – receive filled bottles; cost = bottles filled × charge. */
  Ops.receiveFilled = function (input) {
    var b = Q.batch(input.batchId);
    if (!b) return fail('Select the batch.');
    var e = checkDate(input.date); if (e) return e;
    if (input.date < b.date) return fail('Return date cannot be before the dispatch date.');
    var f = int(input.filled), r = int(input.rejected);
    var out = Q.batchReturned(b).outstanding;
    if (f + r < 1) return fail('Enter filled and/or rejected bottles received.');
    if (f + r > out) return fail('Only ' + out + ' bottles of this batch are still at the factory.');
    if (r > 0 && !input.rejectReason) return fail('Enter the reason for the rejected bottles.');
    var fx = newFx(null);
    fxS(fx, b.bt, 'factory', -(f + r));
    fxS(fx, b.bt, 'filled', f);
    fxS(fx, b.bt, 'writtenOff', r);
    applyFx(fx, 1);
    var cost = f * b.charge;
    var ret = { id: seq('ret', 'FR-', 4), date: input.date, filled: f, rejected: r, rejectReason: input.rejectReason || '', cost: cost, note: input.note || '', user: currentUser().name };
    b.returns.push(ret);
    if (r > 0) db.damages.push({ id: seq('dmg', 'D', 4), date: input.date, bt: b.bt, qty: r, resp: 'Company', location: 'At factory', reason: input.rejectReason, note: 'Rejected in batch ' + b.id, ref: b.id, user: currentUser().name });
    logMovement(input.date, b.id, 'Returned from factory' + (r ? ' (' + r + ' rejected)' : ''), fx);
    audit('Create', 'Factory return', b.id + ' / ' + ret.id, f + ' filled, ' + r + ' rejected; filling cost ' + money(cost) + ' (' + f + ' × ' + money(b.charge) + ')');
    save();
    return ok({ ret: ret, outstanding: out - f - r });
  };
  Ops.factoryPayment = function (input) {
    var e = checkDate(input.date); if (e) return e;
    var amt = num(input.amount);
    if (amt <= 0) return fail('Enter the amount paid.');
    if (PAY_MODES.indexOf(input.mode) < 0) return fail('Select the payment method.');
    if (input.mode !== 'Cash' && !input.ref) return fail('Enter the cheque number or bank reference.');
    if (!Q.factory(input.factoryId)) return fail('Select the factory.');
    var p = { id: seq('fpay', 'FP-', 4), factoryId: input.factoryId, date: input.date, amount: amt, mode: input.mode, ref: input.ref || '', period: input.period || '', note: input.note || '', user: currentUser().name };
    db.factoryPayments.push(p);
    audit('Create', 'Factory payment', p.id, Q.factory(p.factoryId).name + ': ' + money(amt) + ' by ' + p.mode + (p.period ? ' for ' + p.period : ''));
    save();
    return ok({ payment: p });
  };

  /* ---------- inventory ---------- */
  var DAMAGE_LOC = { 'Empty in store': 'empty', 'Filled in store': 'filled', 'At factory': 'factory', 'During delivery': 'filled' };
  /* FR-29 / BR-05 – company damage: disposed and written off. */
  Ops.companyDamage = function (input) {
    var e = checkDate(input.date); if (e) return e;
    var qty = int(input.qty);
    if (qty < 1) return fail('Enter the number of damaged bottles.');
    var bucket = DAMAGE_LOC[input.location];
    if (!bucket) return fail('Select where the damage happened.');
    if (!input.reason) return fail('Enter the reason.');
    var fx = newFx(null);
    fxS(fx, input.bt, bucket, -qty);
    fxS(fx, input.bt, 'writtenOff', qty);
    var errs = checkFx(fx, 1);
    if (errs.length) return fail(errs.join(' ') + ' Record a stock adjustment first (BR-12).', 'STOCK');
    applyFx(fx, 1);
    var d = { id: seq('dmg', 'D', 4), date: input.date, bt: input.bt, qty: qty, resp: 'Company', location: input.location, reason: input.reason, note: input.note || '', user: currentUser().name };
    db.damages.push(d);
    logMovement(input.date, d.id, 'Company damage – written off (' + input.location + ')', fx);
    audit('Create', 'Damage (company)', d.id, qty + ' × ' + Q.btShort(input.bt) + ' ' + input.location + ' – ' + input.reason + '. Written off at company cost.');
    save();
    return ok({ damage: d });
  };
  /* FR-30 – lost bottles and physical count adjustments. */
  Ops.adjustStock = function (input) {
    var e = checkDate(input.date); if (e) return e;
    if (!input.reason) return fail('Enter the reason for the adjustment.');
    var bucket = input.bucket;
    if (['empty', 'filled', 'factory'].indexOf(bucket) < 0 && input.item.charAt(0) === 'B') return fail('Select the stock status to adjust.');
    var fx = newFx(null), delta, desc;
    if (input.item.charAt(0) === 'P') {
      var p = Q.product(input.item);
      var counted = int(input.counted);
      if (input.counted === '' || counted < 0) return fail('Enter the counted quantity.');
      delta = counted - p.stock;
      if (!delta) return fail('Counted quantity matches the system. No adjustment needed.');
      fxP(fx, p.id, delta);
      desc = p.name + ': system ' + p.stock + ' → counted ' + counted;
    } else if (input.mode === 'lost') {
      var q = int(input.qty);
      if (q < 1) return fail('Enter the number of bottles lost.');
      fxS(fx, input.item, bucket, -q);
      fxS(fx, input.item, 'writtenOff', q);
      delta = -q;
      desc = q + ' × ' + Q.btShort(input.item) + ' lost from ' + BUCKET_LABEL[bucket] + ' – written off';
    } else {
      var cnt = int(input.counted);
      if (input.counted === '' || cnt < 0) return fail('Enter the counted quantity.');
      var sys = db.stock[input.item][bucket];
      delta = cnt - sys;
      if (!delta) return fail('Counted quantity matches the system. No adjustment needed.');
      fxS(fx, input.item, bucket, delta);
      desc = Q.btShort(input.item) + ' ' + BUCKET_LABEL[bucket] + ': system ' + sys + ' → counted ' + cnt;
    }
    var errs = checkFx(fx, 1);
    if (errs.length) return fail(errs.join(' '), 'STOCK');
    applyFx(fx, 1);
    var a = { id: seq('adj', 'ADJ-', 4), date: input.date, item: input.item, bucket: bucket || 'stock', mode: input.mode || 'count', delta: delta, desc: desc, reason: input.reason, user: currentUser().name, ts: nowTs() };
    db.adjustments.push(a);
    logMovement(input.date, a.id, 'Stock adjustment – ' + input.reason, fx);
    audit('Create', 'Stock adjustment', a.id, desc + ' – ' + input.reason);
    save();
    return ok({ adj: a });
  };
  Ops.setMinLevel = function (bt, n) {
    n = int(n);
    if (n < 0) return fail('Minimum level cannot be negative.');
    var before = db.minLevels[bt];
    db.minLevels[bt] = n;
    audit('Update', 'Minimum stock level', bt, 'Filled ' + Q.btShort(bt) + ': ' + (before == null ? '–' : before) + ' → ' + n);
    save();
    return ok();
  };

  /* ---------- running expenses (client request 08/10/2026) ---------- */
  Ops.saveExpense = function (x, isNew) {
    var e = checkDate(x.date); if (e) return e;
    if (!/^\d{4}-\d{2}$/.test(x.month || '')) return fail('Select the month the expense is for.');
    var cat = db.expenseCategories.filter(function (c) { return c.id === x.category; })[0];
    if (!cat) return fail('Select the expense type.');
    if (!(num(x.amount) > 0)) return fail('Enter the amount paid.');
    if (PAY_MODES.indexOf(x.mode) < 0) return fail('Select the payment method.');
    if (x.mode !== 'Cash' && !x.ref) return fail('Enter the cheque number or bank reference.');
    var rec;
    if (isNew) {
      rec = { id: seq('exp', 'EX-', 4), created: nowTs(), user: currentUser().name };
      db.expenses.push(rec);
    } else {
      rec = Q.expense(x.id);
      if (!rec) return fail('Expense not found.');
    }
    var before = isNew ? '' : rec.category + ' ' + money(rec.amount) + ' (' + D.monthLabel(rec.month) + ') → ';
    rec.date = x.date; rec.month = x.month; rec.category = cat.id; rec.description = (x.description || '').trim(); rec.paidTo = (x.paidTo || '').trim();
    rec.amount = num(x.amount); rec.mode = x.mode; rec.ref = x.ref || ''; rec.billNo = (x.billNo || '').trim();
    audit(isNew ? 'Create' : 'Update', 'Expense', rec.id, before + rec.category + ' ' + money(rec.amount) + ' for ' + D.monthLabel(rec.month) + (rec.description ? ' – ' + rec.description : ''));
    save();
    return ok({ expense: rec });
  };
  Ops.deleteExpense = function (id, reason) {
    var rec = Q.expense(id);
    if (!rec) return fail('Expense not found.');
    if (!reason) return fail('Enter the reason for deleting.');
    db.expenses.splice(db.expenses.indexOf(rec), 1);
    audit('Delete', 'Expense', rec.id, rec.category + ' ' + money(rec.amount) + ' for ' + D.monthLabel(rec.month) + ' – ' + reason);
    save();
    return ok();
  };
  Ops.saveExpenseCategory = function (c, isNew) {
    var name = (c.id || '').trim();
    if (!name) return fail('Enter the expense type name.');
    if (isNew) {
      if (db.expenseCategories.some(function (x) { return x.id.toLowerCase() === name.toLowerCase(); })) return fail('That expense type already exists.');
      db.expenseCategories.push({ id: name, desc: (c.desc || '').trim(), active: true });
      audit('Create', 'Expense type', name, c.desc || '');
    } else {
      var ex = db.expenseCategories.filter(function (x) { return x.id === name; })[0];
      if (!ex) return fail('Expense type not found.');
      ex.active = !!c.active;
      audit('Update', 'Expense type', name, ex.active ? 'Activated' : 'Deactivated');
    }
    save();
    return ok();
  };

  /* ---------- customer quotations (client request 08/10/2026) ---------- */
  /* To an existing customer (custId) or to a new organisation (government office, factory…). */
  Ops.saveCQuote = function (x, isNew) {
    var e = checkDate(x.date); if (e) return e;
    var cust = x.custId ? Q.customer(x.custId) : null;
    if (x.custId && !cust) return fail('Customer not found.');
    if (!cust && !(x.org || '').trim()) return fail('Enter the organisation or person the quotation is for.');
    if (!x.validUntil || x.validUntil < x.date) return fail('Valid until must be on or after the quotation date.');
    if (!x.custType) return fail('Select the customer type (used for the standard prices).');
    var lines = (x.lines || []).filter(function (l) { return l.item || (l.desc || '').trim(); }).map(function (l) { return { item: l.item || '', desc: (l.desc || '').trim(), qty: int(l.qty), unit: l.unit || '', price: num(l.price), std: l.std != null && l.std !== '' ? num(l.std) : null }; });
    if (!lines.length) return fail('Add at least one item to the quotation.');
    var bad = lines.filter(function (l) { return l.qty < 1 || !l.desc; })[0];
    if (bad) return fail('Every line needs a description and a quantity of 1 or more.');
    var q;
    if (isNew) {
      q = { id: seq('cq', 'QT-2026-', 3), status: 'Draft', history: [], created: nowTs(), user: currentUser().name };
      db.cquotes.push(q);
    } else {
      q = Q.cquote(x.id);
      if (!q) return fail('Quotation not found.');
      if (q.status !== 'Draft') return fail('Only Draft quotations can be edited. Make a copy to change a sent quotation.');
    }
    q.date = x.date; q.validUntil = x.validUntil; q.custId = cust ? cust.id : ''; q.custType = x.custType;
    q.org = cust ? cust.name : x.org.trim(); q.contact = (x.contact || '').trim(); q.designation = (x.designation || '').trim();
    q.address = cust ? cust.address + ', ' + cust.area : (x.address || '').trim(); q.area = cust ? cust.area : (x.area || '');
    q.phone = cust ? cust.phone : (x.phone || '').trim(); q.email = (x.email || '').trim();
    q.subject = (x.subject || '').trim(); q.delivery = (x.delivery || '').trim(); q.terms = int(x.terms); q.conditions = (x.conditions || '').trim();
    q.lines = lines;
    q.history.push({ ts: nowTs(), status: isNew ? 'Draft' : 'Edited', user: currentUser().name, note: isNew && x.copyOf ? 'Copy of ' + x.copyOf : '' });
    audit(isNew ? 'Create' : 'Update', 'Customer quotation', q.id, q.org + ' – ' + money(Q.cquoteTotal(q)) + (isNew && x.copyOf ? ' (copy of ' + x.copyOf + ')' : ''));
    save();
    return ok({ quote: q });
  };
  /* Draft → Sent → Accepted / Rejected. Only the next step is allowed. */
  Ops.setCQuoteStatus = function (id, status, info) {
    var q = Q.cquote(id);
    info = info || {};
    if (!q) return fail('Quotation not found.');
    var allowed = { Sent: ['Draft'], Accepted: ['Sent'], Rejected: ['Sent'] };
    if (!allowed[status] || allowed[status].indexOf(q.status) < 0) return fail('Cannot change ' + q.status + ' to ' + status + '.');
    if (status === 'Sent' && SENT_VIA.indexOf(info.via) < 0) return fail('Select how the quotation was sent.');
    if (status === 'Rejected' && !info.note) return fail('Enter the reason the quotation was not accepted.');
    q.status = status;
    if (status === 'Sent') { q.sentVia = info.via; q.sentOn = info.date || bizDate(); }
    if (status === 'Accepted') q.acceptedOn = info.date || bizDate();
    if (status === 'Rejected') q.rejectReason = info.note;
    q.history.push({ ts: nowTs(), status: status, user: currentUser().name, note: status === 'Sent' ? Q.viaText(info.via) : info.note || '' });
    audit('Update', 'Customer quotation', q.id, 'Status → ' + status + (status === 'Sent' ? ' (' + info.via + ')' : '') + (info.note ? ' – ' + info.note : ''));
    save();
    return ok({ quote: q });
  };
  /* An accepted quotation to a new organisation is linked to the customer registered from it. */
  Ops.linkCQuoteCustomer = function (id, custId) {
    var q = Q.cquote(id), c = Q.customer(custId);
    if (!q || !c) return fail('Quotation or customer not found.');
    q.custId = c.id;
    q.history.push({ ts: nowTs(), status: 'Customer registered', user: currentUser().name, note: c.id });
    audit('Update', 'Customer quotation', q.id, 'Registered as customer ' + c.id + ' – ' + c.name);
    save();
    return ok();
  };

  /* An accepted quotation's water prices become the customer's agreed prices (only lines that differ from the
     price the customer would otherwise pay). */
  Ops.applyCQuotePrices = function (id, from) {
    var q = Q.cquote(id), c = q && Q.customer(q.custId);
    if (!q || !c) return fail('Register or link the customer first.');
    if (q.status !== 'Accepted') return fail('Only an accepted quotation can set prices.');
    from = from || bizDate();
    var n = 0, errs = [];
    q.lines.forEach(function (l) {
      var m = /^water\|(.+)$/.exec(l.item || '');
      if (!m || !(l.price > 0)) return;
      if (Q.price(c, m[1], from) === l.price && !Q.nextCustPrice(c.id, m[1])) return;
      var r = l.price === Q.stdPrice(m[1], c.type, from) ? (Q.isAgreed(c, m[1], from) ? Ops.endCustPrice(c.id, m[1], from, 'Quotation ' + q.id + ' accepted at the standard price') : ok())
        : Ops.setCustPrice({ custId: c.id, bt: m[1], price: l.price, from: from, until: '', reason: 'Quotation ' + q.id + ' accepted' + (q.subject ? ' – ' + q.subject : ''), quoteId: q.id });
      if (r.ok) n++; else errs.push(r.error);
    });
    if (errs.length) return fail(errs.join(' '));
    q.pricesApplied = from;
    q.history.push({ ts: nowTs(), status: 'Prices applied', user: currentUser().name, note: n + ' price(s) for ' + c.id + ' from ' + D.dmy(from) });
    save();
    return ok({ count: n });
  };

  /* ---------- migration (FR-53/54/55) ---------- */
  Ops.importCustomers = function (rows, fileName) {
    var ids = [];
    rows.forEach(function (r) {
      var c = {
        id: seq('cust', 'C', 4), name: r.name, address: r.address, area: r.area, phone: r.phone, type: r.type,
        day: r.day, cycle: r.cycle, nextDelivery: r.nextDelivery, usual: { B20: r.usual20 || 0, B10: r.usual10 || 0 }, payType: r.payType,
        limit: r.limit || 0, terms: r.terms || 0, since: today(), status: 'Active',
        held: { B20: r.held20, B10: r.held10 }, toCollect: { B20: r.toCollect20, B10: 0 }, owed: { B20: r.owed20, B10: 0 }, notes: 'Imported from ' + fileName
      };
      db.customers.push(c);
      [['B20', r.price20], ['B10', r.price10]].forEach(function (x) { if (x[1] > 0 && x[1] !== Q.stdPrice(x[0], c.type)) db.custPrices.push({ id: seq('cprice', 'CP', 4), custId: c.id, bt: x[0], price: x[1], std: Q.stdPrice(x[0], c.type), from: today(), until: '', reason: 'Agreed price imported from ' + fileName, quoteId: '', by: currentUser().name, created: nowTs() }); });
      var bottles = r.held20 + r.toCollect20 + r.owed20 + r.held10;
      db.stock.B20.customers += r.held20 + r.toCollect20 + r.owed20;
      db.stock.B10.customers += r.held10;
      if (r.outstanding) {
        db.txns.push({ id: seq('txn', 'T', 6), kind: 'opening', billNo: '', date: today(), custId: c.id, lines: [], amount: r.outstanding, paid: 0, note: 'Opening balance imported from ' + fileName, split: { water: 0, deposit: 0, product: 0 }, fx: newFx(c.id), user: currentUser().name, created: nowTs() });
      }
      if (bottles) db.movements.push({ id: seq('mov', 'M', 6), date: today(), ref: 'IMPORT', desc: 'Opening customer bottles – ' + c.name, item: 'B20', d: { customers: r.held20 + r.toCollect20 + r.owed20 }, user: currentUser().name, ts: nowTs() });
      ids.push(c.id);
    });
    audit('Import', 'Customers', fileName, rows.length + ' customers imported (' + (ids[0] || '') + ' – ' + (ids[ids.length - 1] || '') + ')');
    save();
    return ok({ ids: ids });
  };
  Ops.importStock = function (rows, fileName) {
    rows.forEach(function (r) {
      var cur = db.stock[r.bt][r.bucket];
      var d = {}; d[r.bucket] = r.qty - cur;
      db.stock[r.bt][r.bucket] = r.qty;
      if (d[r.bucket]) db.movements.push({ id: seq('mov', 'M', 6), date: today(), ref: 'IMPORT', desc: 'Opening stock import – ' + BUCKET_LABEL[r.bucket], item: r.bt, d: d, user: currentUser().name, ts: nowTs() });
    });
    audit('Import', 'Opening stock', fileName, rows.map(function (r) { return Q.btShort(r.bt) + ' ' + BUCKET_LABEL[r.bucket] + ' = ' + r.qty; }).join('; '));
    save();
    return ok();
  };

  /* ---------- users ---------- */
  Ops.saveUser = function (u, isNew) {
    if (!u.name || !u.username || !u.role) return fail('Name, username and role are required.');
    if (!/^[a-z0-9._]{3,}$/.test(u.username)) return fail('Username: at least 3 lowercase letters, numbers, dots or underscores.');
    if (db.users.some(function (x) { return x.username === u.username && x.id !== u.id; })) return fail('Username already taken.');
    if (isNew) {
      if (!u.password || u.password.length < 8) return fail('Temporary password must be at least 8 characters.');
      var nu = { id: seq('user', 'U', 2), username: u.username, name: u.name, role: u.role, phone: u.phone || '', active: true, lastLogin: '' };
      db.users.push(nu);
      audit('Create', 'User', nu.username, nu.name + ' – ' + nu.role);
    } else {
      var ex = Q.user(u.id);
      var ch = ex.role !== u.role ? 'role ' + ex.role + ' → ' + u.role : 'details updated';
      ex.name = u.name; ex.username = u.username; ex.role = u.role; ex.phone = u.phone || '';
      audit('Update', 'User', ex.username, ch);
    }
    save();
    return ok();
  };
  Ops.setUserActive = function (id, active) {
    var u = Q.user(id);
    if (u.id === currentUser().id && !active) return fail('You cannot deactivate your own account.');
    u.active = active;
    audit('Update', 'User', u.username, active ? 'Activated' : 'Deactivated');
    save();
    return ok();
  };
  Ops.resetPassword = function (id) {
    var u = Q.user(id);
    audit('Update', 'User', u.username, 'Password reset – temporary password issued');
    save();
    return ok();
  };
  Ops.logEvent = function (action, entity, ref, details) { audit(action, entity, ref, details); save(); };

  /* ======================================================================
     Seed + history simulation
     ====================================================================== */
  function rngFactory(seed) {
    return function () {
      seed |= 0; seed = seed + 0x6D2B79F5 | 0;
      var t = Math.imul(seed ^ seed >>> 15, 1 | seed);
      t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
  }

  function buildSeed() {
    var S = DATA;
    db = {
      version: VERSION,
      settings: { today: S.today, company: clone(S.company) },
      seq: {},
      bottleTypes: clone(S.bottleTypes),
      products: clone(S.products),
      customerTypes: clone(S.customerTypes),
      areas: clone(S.areas),
      priceHistory: [],
      confirmedPrices: clone(S.confirmedPrices),
      oldBrands: clone(S.oldBrands),
      customers: [],
      suppliers: clone(S.suppliers),
      qrs: [], quotes: [], pos: [], grns: [], supplierPayments: [],
      factories: clone(S.factories), batches: [], factoryPayments: [],
      stock: {}, minLevels: clone(S.minLevels),
      movements: [], damages: [], adjustments: [],
      txns: [], payments: [], invoices: [],
      expenseCategories: clone(S.expenseCategories), expenses: [], cquotes: [], custPrices: [],
      users: clone(S.users), audit: []
    };
    db.seq.prod = db.products.length;
    db.seq.sup = db.suppliers.length;
    db.seq.user = db.users.length;
    db.seq.brand = db.oldBrands.length;
    db.suppliers.push({ id: 'S06', name: 'CoolTech Distributors', contact: 'Ms. Hasini Perera', phone: '011 252 6603', email: 'sales@cooltech.lk', address: '88, Nawala Road, Nugegoda', items: ['P01', 'P02', 'P05'], terms: 30, active: true });
    db.seq.sup = 6;
    db.seq.fac = db.factories.length;

    S.priceHistory.forEach(function (p, i) {
      db.priceHistory.push({ id: 'PH' + String(i + 1).padStart(4, '0'), key: p[0], price: p[1], effective: p[2], reason: p[3] || 'Initial price list', by: 'Nimal Perera', created: (p[2] > '2026-06-15' ? '2026-06-15' : '2025-12-20') + ' 10:15:00' });
    });
    db.seq.price = db.priceHistory.length;

    S.bottleTypes.forEach(function (b) { var o = S.stockOpening[b.id]; db.stock[b.id] = { empty: o.empty, factory: o.factory, filled: o.filled, customers: 0, writtenOff: o.writtenOff }; });

    var start = S.historyStart;
    S.customers.forEach(function (r) {
      var c = {
        id: r[0], name: r[1], address: r[2], area: r[3], phone: r[4], type: r[5], day: r[6], cycle: r[7],
        usual: { B20: r[9], B10: r[10] }, payType: r[11], limit: r[12], terms: r[13],
        since: r[15], status: r[16], held: { B20: 0, B10: 0 }, toCollect: { B20: 0, B10: 0 }, owed: { B20: 0, B10: 0 }, notes: ''
      };
      // first scheduled date on/after max(start, since) on the delivery day, plus phase
      var d0 = c.since > start ? c.since : start;
      while (DAYS[D.dow(d0)] !== c.day) d0 = D.add(d0, 1);
      c.nextDelivery = D.add(d0, 7 * r[8]);
      if (c.since <= start) {
        var oh = S.openingHeld[c.id] || {};
        c.held.B20 = oh.B20 != null ? oh.B20 : c.usual.B20;
        c.held.B10 = oh.B10 != null ? oh.B10 : c.usual.B10;
        db.stock.B20.customers += c.held.B20;
        db.stock.B10.customers += c.held.B10;
      }
      if (c.status === 'Inactive') { c.statusReason = 'Moved to Kandy – account closed by customer'; c.statusDate = '2026-06-30'; }
      db.customers.push(c);
      if (r[17]) {
        db.txns.push({ id: seq('txn', 'T', 6), kind: 'opening', billNo: '', date: r[18], custId: c.id, lines: [], amount: r[17], paid: 0, note: 'Opening balance migrated from the old ledger', split: { water: 0, deposit: 0, product: 0 }, fx: newFx(c.id), user: 'Nimal Perera', created: '2026-07-31 16:30:00' });
      }
    });
    db.seq.cust = 40;
    // Agreed customer prices (FR-05, client feedback 08/10/2026) – set before the history so past bills use them.
    S.custPrices.forEach(function (x, i) {
      var c = Q.customer(x[0]);
      db.custPrices.push({ id: 'CP' + String(i + 1).padStart(4, '0'), custId: x[0], bt: x[1], price: x[2], std: Q.stdPrice(x[1], c.type, x[3]), from: x[3], until: x[4] || '', reason: x[5], quoteId: '', by: 'Nimal Perera', created: (x[3] > '2026-07-31' ? x[3] : '2026-07-31') + ' 11:00:00', cancelled: !!x[6] });
    });
    db.seq.cprice = db.custPrices.length;
    var openMov = {};
    ['B20', 'B10'].forEach(function (bt) {
      openMov[bt] = { empty: db.stock[bt].empty, filled: db.stock[bt].filled, customers: db.stock[bt].customers };
      db.movements.push({ id: seq('mov', 'M', 6), date: '2026-07-31', ref: 'IMPORT', desc: 'Opening stock (migrated)', item: bt, d: openMov[bt], user: 'Nimal Perera', ts: '2026-07-31 16:20:00' });
    });
    ctx.time = '2026-07-31 16:20:00';
    ctx.user = { id: 'U01', name: 'Nimal Perera', role: 'Admin' };
    audit('Import', 'Customers', 'customers_2026-07-31.xlsx', '36 customers imported from the old ledger');
    audit('Import', 'Opening stock', 'stock_2026-07-31.xlsx', 'Opening stock by bottle type and status');

    simulate();

    ctx.sim = false; ctx.user = null; ctx.time = null;
    cache = {};
    return db;
  }

  function simulate() {
    var rnd = rngFactory(20261002);
    var NIMAL = { id: 'U01', name: 'Nimal Perera', role: 'Admin' };
    var DILINI = { id: 'U02', name: 'Dilini Wickramasinghe', role: 'Admin' };
    var SHANIKA = { id: 'U03', name: 'Shanika Fernando', role: 'Accountant' };
    var bill = 41000;
    function as(user, date, fn) {
      ctx.user = user;
      var h = 8 + Math.floor(rnd() * 9), m = Math.floor(rnd() * 60);
      ctx.time = date + ' ' + pad(h) + ':' + pad(m) + ':' + pad(Math.floor(rnd() * 60));
      var r = fn();
      if (r && r.ok === false && r.code !== 'CREDIT_LIMIT' && typeof console !== 'undefined') console.warn('[seed] ' + date + ': ' + r.error);
      return r;
    }
    ctx.sim = true;
    db.seq.qr = 9; db.seq.po = 38;

    /* ---- purchasing history ---- */
    as(NIMAL, '2026-06-08', function () { return Ops.createQR({ date: '2026-06-08', items: [{ item: 'P03', qty: 20 }], suppliers: ['S05'], note: 'Stands for new office customers' }); });
    as(NIMAL, '2026-06-10', function () { return Ops.recordQuote({ qrId: 'QR-2026-010', supplierId: 'S05', ref: 'PSW/Q/118', date: '2026-06-10', lines: [{ item: 'P03', price: 3100 }], deliveryDays: 21, validUntil: '2026-07-10', notes: '' }); });
    as(NIMAL, '2026-06-11', function () { return Ops.selectQuote(db.quotes[0].id, 'Only supplier for steel stands'); });
    as(NIMAL, '2026-06-11', function () { var r = Ops.createPO(db.quotes[0].id); return r; });
    as(NIMAL, '2026-06-11', function () { return Ops.setPOStatus('PO-2026-0039', 'Approved'); });
    as(NIMAL, '2026-07-02', function () { return Ops.setPOStatus('PO-2026-0039', 'Cancelled', 'Supplier could not deliver before July – re-order later'); });

    as(NIMAL, '2026-07-20', function () { return Ops.createQR({ date: '2026-07-20', requiredBy: '2026-08-05', items: [{ item: 'B20', qty: 120 }], suppliers: ['S01', 'S02', 'S03'], note: 'Pool top-up before school term' }); });
    var qr11 = db.qrs[1].id;
    as(NIMAL, '2026-07-22', function () { return Ops.recordQuote({ qrId: qr11, supplierId: 'S01', ref: 'LPC-Q-2207', date: '2026-07-22', lines: [{ item: 'B20', price: 620 }], deliveryDays: 10, validUntil: '2026-08-21', notes: 'Free delivery to Nugegoda', attachment: 'LPC-Q-2207.pdf' }); });
    as(NIMAL, '2026-07-22', function () { return Ops.recordQuote({ qrId: qr11, supplierId: 'S02', ref: 'CPI/2026/311', date: '2026-07-22', lines: [{ item: 'B20', price: 655 }], deliveryDays: 7, validUntil: '2026-08-15', notes: 'Transport Rs. 4,500 extra', attachment: 'CPI-2026-311.pdf' }); });
    as(NIMAL, '2026-07-23', function () { return Ops.recordQuote({ qrId: qr11, supplierId: 'S03', ref: 'KP-0723', date: '2026-07-23', lines: [{ item: 'B20', price: 640 }], deliveryDays: 14, validUntil: '2026-08-07', notes: '', attachment: 'KP-0723.jpg' }); });
    as(NIMAL, '2026-07-24', function () { return Ops.selectQuote(db.quotes[1].id, 'Lowest price with free delivery; good quality on last order'); });
    as(NIMAL, '2026-07-24', function () { return Ops.createPO(db.quotes[1].id); });
    var po41 = db.pos[1]; po41.expected = '2026-08-04';
    as(NIMAL, '2026-07-24', function () { return Ops.setPOStatus(po41.id, 'Approved'); });
    as(NIMAL, '2026-07-25', function () { return Ops.setPOStatus(po41.id, 'Sent'); });

    var qr12, po42;

    /* ---- daily loop ---- */
    var end = S_today();
    var skip = { 'C0017|2026-09-25': true };
    var forceShort = { 'C0005|2026-09-29': true, 'C0023|2026-09-26': true, 'C0003|2026-09-28': true };
    var todayOnly = { C0020: true, C0021: true };
    for (var d = '2026-08-01'; d <= end; d = D.add(d, 1)) {
      var dow = D.dow(d);
      var isToday = d === end;

      // Purchasing events
      if (d === '2026-08-04') as(NIMAL, d, function () { return Ops.receiveGoods({ poId: po41.id, date: d, deliveryNote: 'LPC-DN-5531', lines: [{ received: 120, damaged: 2 }], note: '2 bottles cracked on arrival – supplier informed' }); });
      if (d === '2026-08-18') {
        as(NIMAL, d, function () { return Ops.createQR({ date: d, requiredBy: '2026-09-05', items: [{ item: 'P01', qty: 6 }, { item: 'P02', qty: 8 }, { item: 'P05', qty: 12 }], suppliers: ['S04', 'S06'], note: 'Dispensers for office customers' }); });
        qr12 = db.qrs[db.qrs.length - 1].id;
      }
      if (d === '2026-08-20') {
        as(NIMAL, d, function () { return Ops.recordQuote({ qrId: qr12, supplierId: 'S04', ref: 'HC/TQ/0820', date: d, lines: [{ item: 'P01', price: 30500 }, { item: 'P02', price: 11200 }, { item: 'P05', price: 2650 }], deliveryDays: 14, validUntil: '2026-09-20', notes: '1-year warranty on dispensers', attachment: 'HomeCool-TQ-0820.pdf' }); });
        as(NIMAL, d, function () { return Ops.recordQuote({ qrId: qr12, supplierId: 'S06', ref: 'CT-Q-4471', date: d, lines: [{ item: 'P01', price: 29800 }, { item: 'P02', price: 11900 }, { item: 'P05', price: 2800 }], deliveryDays: 30, validUntil: '2026-09-05', notes: '6-month warranty only', attachment: '' }); });
      }
      if (d === '2026-08-24') {
        as(NIMAL, d, function () { return Ops.selectQuote(Q.quotesFor(qr12)[0].id, 'Better warranty and faster delivery; total close to CoolTech'); });
        as(NIMAL, d, function () { return Ops.createPO(Q.quotesFor(qr12)[0].id); });
        po42 = db.pos[db.pos.length - 1]; po42.expected = '2026-09-07';
        as(NIMAL, d, function () { return Ops.setPOStatus(po42.id, 'Approved'); });
      }
      if (d === '2026-08-25') as(NIMAL, d, function () { return Ops.setPOStatus(po42.id, 'Sent'); });
      if (d === '2026-08-28') as(SHANIKA, d, function () { return Ops.supplierPayment({ supplierId: 'S01', poId: po41.id, date: d, amount: 118 * 620, mode: 'Bank transfer', ref: 'BOC-TRF-882140', note: 'Full settlement PO ' + po41.id }); });
      if (d === '2026-09-10') as(NIMAL, d, function () { return Ops.receiveGoods({ poId: po42.id, date: d, deliveryNote: 'HC-DN-20931', lines: [{ received: 6, damaged: 0 }, { received: 4, damaged: 0 }, { received: 12, damaged: 1 }], note: 'Balance 4 table-top dispensers to follow' }); });
      if (d === '2026-09-15') as(SHANIKA, d, function () { return Ops.supplierPayment({ supplierId: 'S04', poId: po42.id, date: d, amount: 150000, mode: 'Cheque', ref: 'Cheque 004512 (Sampath Bank)', note: 'Part payment' }); });
      if (d === '2026-09-22') {
        as(NIMAL, d, function () { return Ops.createQR({ date: d, requiredBy: '2026-10-15', items: [{ item: 'B20', qty: 600 }, { item: 'B10', qty: 200 }], suppliers: ['S01', 'S02', 'S03'], note: 'Bottles for the new Kottawa route and replacement of written-off bottles' }); });
      }
      if (d === '2026-09-24') as(NIMAL, d, function () { return Ops.recordQuote({ qrId: db.qrs[3].id, supplierId: 'S02', ref: 'CPI/2026/402', date: d, lines: [{ item: 'B20', price: 640 }, { item: 'B10', price: 410 }], deliveryDays: 7, validUntil: '2026-10-24', notes: 'Price includes transport. 2% discount if paid within 7 days.', attachment: 'CPI-2026-402.pdf' }); });
      if (d === '2026-09-25') as(NIMAL, d, function () { return Ops.recordQuote({ qrId: db.qrs[3].id, supplierId: 'S01', ref: 'LPC-Q-2509', date: d, lines: [{ item: 'B20', price: 615 }, { item: 'B10', price: 430 }], deliveryDays: 12, validUntil: '2026-10-25', notes: 'Free delivery. 10L stock limited – 2 shipments.', attachment: 'LPC-Q-2509.pdf' }); });
      if (d === '2026-09-28') as(NIMAL, d, function () { return Ops.recordQuote({ qrId: db.qrs[3].id, supplierId: 'S03', ref: 'KP-0928', date: d, lines: [{ item: 'B20', price: 605 }, { item: 'B10', price: null }], deliveryDays: 21, validUntil: '2026-10-12', notes: 'Cannot supply 10L bottles. Advance 30% required.', attachment: 'KP-0928.jpg' }); });
      if (d === '2026-09-29') {
        as(NIMAL, d, function () { return Ops.createQR({ date: d, requiredBy: '2026-10-20', items: [{ item: 'P03', qty: 20 }, { item: 'P04', qty: 50 }], suppliers: ['S04', 'S05'], note: 'Stands and pumps for Sinhala & Tamil New Year promotion stock' }); });
      }
      if (d === '2026-10-01') as(NIMAL, d, function () { return Ops.recordQuote({ qrId: db.qrs[4].id, supplierId: 'S05', ref: 'PSW/Q/162', date: d, lines: [{ item: 'P03', price: 3200 }, { item: 'P04', price: null }], deliveryDays: 21, validUntil: '2026-10-31', notes: 'Stands only', attachment: '' }); });

      // Factory: dispatch empties on Saturday, filled bottles return on Sunday.
      if (dow === 6 && d < '2026-10-01') {
        ['B20', 'B10'].forEach(function (bt) {
          var q = db.stock[bt].empty;
          if (q > 0) as(NIMAL, d, function () { return Ops.dispatch({ date: d, bt: bt, factoryId: bt === 'B10' ? 'F02' : 'F01', qty: q, vehicle: 'LH-4521', note: 'Weekly batch' }); });
        });
      }
      if (dow === 0) {
        db.batches.forEach(function (b) {
          if (b.date !== D.add(d, -1)) return;
          var out = Q.batchReturned(b).outstanding;
          var rej = Math.floor(b.qty / 75);
          var short = b.date === '2026-09-26' && b.bt === 'B20' ? 6 : 0;
          as(NIMAL, d, function () { return Ops.receiveFilled({ batchId: b.id, date: d, filled: out - rej - short, rejected: rej, rejectReason: rej ? 'Cracked neck / failed seal test' : '', note: short ? 'Factory short-shipped 6 – to come with next batch' : '' }); });
        });
      }
      if (d === '2026-10-01') {
        // One entry sending both bottle types to the same factory.
        as(NIMAL, d, function () { return Ops.dispatch({ date: d, factoryId: 'F01', lines: [{ bt: 'B20', qty: Math.min(db.stock.B20.empty, 60) }, { bt: 'B10', qty: Math.min(db.stock.B10.empty, 8) }], vehicle: 'LH-4521', note: 'Mid-week top-up batch (low filled stock)' }); });
      }
      if (d === '2026-09-10') as(SHANIKA, d, function () {
        var aug = 0; db.batches.forEach(function (b) { if (b.factoryId === 'F01') b.returns.forEach(function (r) { if (r.date.slice(0, 7) === '2026-08') aug += r.cost; }); });
        return Ops.factoryPayment({ factoryId: 'F01', date: d, amount: aug, mode: 'Bank transfer', ref: 'COM-TRF-551902', period: 'August 2026', note: 'August filling charges' });
      });
      // New customers' first purchases
      if (d === '2026-08-19') as(DILINI, d, function () { return Ops.firstPurchase({ custId: 'C0038', date: d, paperNo: String(++bill), bt: 'B20', qty: 3, oldBottles: 1, brandId: 'OB1', paid: 0, note: 'Handed in 1 American Water bottle' }); });
      if (d === '2026-09-04') as(DILINI, d, function () { return Ops.firstPurchase({ custId: 'C0037', date: d, paperNo: String(++bill), bt: 'B20', qty: 2, oldBottles: 0, paid: 2700 }); });
      if (d === '2026-09-14') as(DILINI, d, function () { return Ops.firstPurchase({ custId: 'C0039', date: d, paperNo: String(++bill), bt: 'B20', qty: 2, oldBottles: 2, brandId: 'OB1', paid: 700, note: 'Two American Water bottles handed in – no deposit' }); });

      // Scheduled deliveries
      if (dow !== 0) {
        db.customers.forEach(function (c) {
          if (!Q.isDue(c, d)) return;
          if (isToday && !todayOnly[c.id]) return;
          if (c.since === d) return; // first purchase handled above
          if (skip[c.id + '|' + d]) return;
          if (Q.sumBt(c.held) === 0) return;
          var r = rnd();
          var lines = [], kind = 'exchange', note = '';
          var forced = forceShort[c.id + '|' + d];
          var left = !forced && r < 0.06, short = forced || (!left && r > 0.965);
          ['B20', 'B10'].forEach(function (bt) {
            if (!c.held[bt]) return;
            var F = c.usual[bt] || 0;
            if (bt === 'B20' && rnd() < 0.1) F += 1;
            if (!F) return;
            var E = F;
            if (left) E = 0;
            else {
              E += (c.toCollect[bt] || 0);
              if (c.owed[bt] && rnd() < 0.6) E += c.owed[bt];
              if (short && bt === 'B20') E -= 1;
            }
            lines.push({ bt: bt, filled: F, empties: E });
          });
          if (!lines.length) return;
          if (left) { kind = 'leftdoor'; note = ['Customer at work – phoned, agreed to leave at the gate', 'Not home – phoned, left with security guard', 'Shop closed – owner agreed by phone, left at side door'][Math.floor(rnd() * 3)]; }
          if (short) note = ['One bottle taken to relative\'s house – will return next trip', 'Bottle in use upstairs – customer to return next week', 'One empty missing – customer checking'][Math.floor(rnd() * 3)];
          var pv = Ops.previewBill({ custId: c.id, date: d, kind: kind, lines: lines });
          var paid = 0;
          if (c.payType === 'Cash' && kind === 'exchange') paid = rnd() < 0.06 ? Math.floor(pv.amount / 2 / 100) * 100 : pv.amount;
          var input = { custId: c.id, date: d, paperNo: String(++bill), kind: kind, lines: lines, paid: paid, note: note, phoneConfirmed: true };
          var res = as(DILINI, d, function () { return Ops.postBill(input); });
          if (res && res.code === 'CREDIT_LIMIT') { input.approveOverLimit = true; as(NIMAL, d, function () { return Ops.postBill(input); }); }
        });
      }

      // Extra orders, product sales, damage
      if (d === '2026-08-15') as(DILINI, d, function () { return Ops.productSale({ custId: 'C0009', date: d, paperNo: String(++bill), items: [{ pid: 'P02', qty: 1 }], paid: 14900, payMode: 'Cash' }); });
      if (d === '2026-08-22') as(DILINI, d, function () { return Ops.extraOrder({ custId: 'C0023', date: d, paperNo: String(++bill), bt: 'B20', filled: 2, empties: 0, oldBottles: 0, paid: 2700, note: 'Extra for weekend orders' }); });
      if (d === '2026-08-12') as(NIMAL, d, function () { return Ops.companyDamage({ date: d, bt: 'B20', qty: 2, location: 'Filled in store', reason: 'Dropped while loading the lorry', note: '' }); });
      if (d === '2026-09-03') as(NIMAL, d, function () { return Ops.companyDamage({ date: d, bt: 'B20', qty: 1, location: 'During delivery', reason: 'Cracked in transport – pothole on Kesbewa Road', note: '' }); });
      if (d === '2026-09-05') as(DILINI, d, function () { return Ops.productSale({ custId: 'C0021', date: d, paperNo: String(++bill), items: [{ pid: 'P03', qty: 1 }, { pid: 'P04', qty: 1 }], paid: 0 }); });
      if (d === '2026-09-12') as(DILINI, d, function () { return Ops.extraOrder({ custId: 'C0024', date: d, paperNo: String(++bill), bt: 'B20', filled: 1, empties: 0, oldBottles: 1, brandId: 'OB1', paid: 350, note: 'Handed in American Water bottle' }); });
      if (d === '2026-09-18') as(DILINI, d, function () { return Ops.customerDamage({ custId: 'C0013', date: d, paperNo: String(++bill), bt: 'B20', qty: 1, mode: 'replace', chargeWater: true, reason: 'Bottle cracked – dropped by customer', paid: 1350 }); });
      if (d === '2026-09-20') as(DILINI, d, function () { return Ops.productSale({ walkIn: 'Walk-in: K. Perera', date: d, paperNo: String(++bill), items: [{ pid: 'P04', qty: 2 }], paid: 2500, payMode: 'Cash' }); });
      if (d === '2026-09-26') as(DILINI, d, function () { return Ops.extraOrder({ custId: 'C0016', date: d, paperNo: String(++bill), bt: 'B20', filled: 3, empties: 0, oldBottles: 0, paid: 0, note: 'Extra 3 bottles for shop – deposit and water to be paid on next delivery' }); });
      if (d === '2026-09-30') as(NIMAL, d, function () { return Ops.adjustStock({ date: d, item: 'B20', bucket: 'empty', mode: 'lost', qty: 3, reason: 'Month-end physical count – 3 empties missing from store' }); });

      // Customer payments
      if (d === '2026-08-08') {
        ['C0004', 'C0010', 'C0018', 'C0025', 'C0030'].forEach(function (id, i) {
          var open = db.txns.filter(function (t) { return t.custId === id && t.kind === 'opening'; })[0];
          if (open) as(SHANIKA, d, function () { return Ops.receivePayment({ custId: id, date: d, amount: open.amount, mode: i % 2 ? 'Cheque' : 'Bank transfer', ref: i % 2 ? 'Cheque 1' + (20330 + i) : 'TRF-' + (771200 + i * 13), note: 'July invoice' }); });
        });
        as(SHANIKA, d, function () { return Ops.receivePayment({ custId: 'C0008', date: d, amount: 6000, mode: 'Cheque', ref: 'Cheque 330981', note: 'Part payment – July' }); });
      }
      if (d === '2026-09-01') {
        as(SHANIKA, d, function () { return Ops.generateInvoices('2026-08', db.customers.filter(function (c) { return c.payType === 'Monthly bill'; }).map(function (c) { return c.id; })); });
      }
      if (d === '2026-09-12' || d === '2026-09-18' || d === '2026-09-25') {
        var plan = { '2026-09-12': ['C0004', 'C0010', 'C0030'], '2026-09-18': ['C0025'], '2026-09-25': ['C0018'] }[d];
        plan.forEach(function (id, i) {
          var inv = db.invoices.filter(function (x) { return x.custId === id && x.period === '2026-08'; })[0];
          if (!inv) return;
          var amt = id === 'C0018' ? Math.round(inv.amount * 0.5 / 100) * 100 : inv.amount;
          as(SHANIKA, d, function () { return Ops.receivePayment({ custId: id, date: d, amount: amt, mode: i % 2 ? 'Cheque' : 'Bank transfer', ref: i % 2 ? 'Cheque 5' + (11870 + i) : 'TRF-' + (902100 + i * 7), invoiceId: inv.id, note: id === 'C0018' ? 'Part payment – balance next week' : 'August invoice' }); });
        });
      }
      if (d.slice(8) === '10' || d.slice(8) === '25') {
        db.customers.forEach(function (c, i) {
          if (c.payType !== 'Credit') return;
          var bal = Q.balance(c.id);
          if (bal <= 0) return;
          var amt;
          if (c.id === 'C0012') { if (d !== '2026-09-10') return; amt = 2000; }
          else if (c.id === 'C0021') amt = Math.floor(bal * 0.5 / 500) * 500;
          else amt = Math.floor(bal * 0.8 / 100) * 100;
          if (amt <= 0) return;
          var mode = PAY_MODES[i % 3];
          as(SHANIKA, d, function () { return Ops.receivePayment({ custId: c.id, date: d, amount: amt, mode: mode, ref: mode === 'Cash' ? '' : mode === 'Cheque' ? 'Cheque ' + (440000 + i * 37) : 'TRF-' + (650000 + i * 91), note: amt < bal ? 'Part payment' : '' }); });
        });
      }
    }

    // Running expenses (client request 08/10/2026)
    DATA.expenses.forEach(function (x) {
      as(x[2] === 'Rent' || x[2] === 'Salaries' ? NIMAL : SHANIKA, x[0], function () { return Ops.saveExpense({ date: x[0], month: x[1], category: x[2], description: x[3], paidTo: x[4], amount: x[5], mode: x[6], ref: x[7], billNo: x[8] }, true); });
    });

    // Customer quotations (client request 08/10/2026): government office, factory, hotel, hospital, existing customer.
    var cq = function (date, f) { var r = as(NIMAL, date, function () { return Ops.saveCQuote(f, true); }); return r.ok ? r.quote.id : null; };
    var cqs = function (id, date, status, info) { as(NIMAL, date, function () { return Ops.setCQuoteStatus(id, status, info); }); };
    var W = function (bt, qty, price, std) { return { item: 'water|' + bt, desc: Q.btName(bt) + ' – drinking water (refill, delivered)', qty: qty, unit: 'per month', price: price, std: std }; };
    var DEP = function (bt, qty) { return { item: 'deposit|' + bt, desc: Q.btName(bt) + ' – bottle deposit (one time)', qty: qty, unit: 'bottles', price: Q.deposit(bt, '2026-09-01'), std: Q.deposit(bt, '2026-09-01') }; };
    var PR = function (pid, qty, price) { var p = Q.product(pid); return { item: pid, desc: p.name, qty: qty, unit: 'units', price: price || p.price, std: p.price }; };
    var q1 = cq('2026-09-08', { org: 'Divisional Secretariat – Maharagama', contact: 'Mrs. W.A. Dilrukshi', designation: 'Administrative Officer', address: 'Divisional Secretariat, High Level Road, Maharagama', area: 'Maharagama', phone: '011 285 0153', email: 'dsmaharagama@gov.lk', custType: 'Office', date: '2026-09-08', validUntil: '2026-10-08', subject: 'Supply of drinking water – 20L bottles, monthly', delivery: 'Every Monday, about 10 bottles a week', terms: 30, conditions: DATA.quoteConditions, lines: [W('B20', 40, 300, 320), DEP('B20', 12), PR('P01', 2, 36500)] });
    cqs(q1, '2026-09-09', 'Sent', { via: 'Email' });
    cqs(q1, '2026-09-24', 'Accepted', {});
    var q2 = cq('2026-09-18', { org: 'Lanka Tiles (Pvt) Ltd – Piliyandala factory', contact: 'Mr. Ranjith Fonseka', designation: 'HR & Admin Manager', address: 'No. 215, Horana Road, Piliyandala', area: 'Piliyandala', phone: '011 261 4420', email: 'admin.piliyandala@lankatiles.lk', custType: 'Factory', date: '2026-09-18', validUntil: '2026-10-18', subject: 'Drinking water for factory staff', delivery: 'Twice a week (Tuesday and Friday) – about 30 bottles a week', terms: 30, conditions: DATA.quoteConditions, lines: [W('B20', 120, 300, 300), DEP('B20', 30), PR('P03', 6)] });
    cqs(q2, '2026-09-18', 'Sent', { via: 'By hand' });
    var q3 = cq('2026-08-20', { org: 'Royal Seaside Hotel', contact: 'Mr. Imran Cassim', designation: 'Purchasing Officer', address: '41, De Saram Road, Mount Lavinia', area: 'Mount Lavinia', phone: '011 273 6650', email: 'purchasing@royalseaside.lk', custType: 'Shop', date: '2026-08-20', validUntil: '2026-09-19', subject: 'Drinking water for staff areas', delivery: 'Every Wednesday', terms: 14, conditions: DATA.quoteConditions, lines: [W('B20', 24, 330, 330), DEP('B20', 6)] });
    cqs(q3, '2026-08-21', 'Sent', { via: 'Email' });
    var q4 = cq('2026-09-02', { org: 'Base Hospital – Homagama', contact: 'Dr. N. Wijeratne', designation: 'Medical Superintendent', address: 'Base Hospital, Homagama', area: 'Kottawa', phone: '011 285 5321', email: '', custType: 'Office', date: '2026-09-02', validUntil: '2026-09-30', subject: 'Quotation for drinking water supply – tender ref. BHH/PRO/2026/14', delivery: 'Daily, Monday to Saturday', terms: 45, conditions: DATA.quoteConditions, lines: [W('B20', 200, 290, 320), DEP('B20', 40)] });
    cqs(q4, '2026-09-03', 'Sent', { via: 'Post' });
    cqs(q4, '2026-09-29', 'Rejected', { note: 'Tender given to the lowest bidder (Rs. 270 per bottle)' });
    cq('2026-10-01', { custId: 'C0004', custType: 'Office', contact: 'Ms. Hiruni Peris', designation: 'Office Manager', email: 'admin@vertexsoftware.lk', date: '2026-10-01', validUntil: '2026-10-31', subject: 'Dispensers for the new 4th floor', delivery: 'With the Monday delivery', terms: 30, conditions: DATA.quoteConditions, lines: [PR('P01', 2, 36900), PR('P02', 2), W('B20', 16, 300, 320)] });

    // A login trail for the audit log
    ctx.user = DILINI; ctx.time = end + ' 08:02:11'; audit('Login', 'Session', 'dilini.admin', 'Signed in');
    ctx.user = NIMAL; ctx.time = end + ' 08:15:40'; audit('Login', 'Session', 'nimal.admin', 'Signed in');
    db.audit.sort(function (a, b) { return a.ts < b.ts ? -1 : a.ts > b.ts ? 1 : 0; });
  }
  function S_today() { return DATA.today; }

  load();

  global.TW = {
    get db() { return db; },
    D: D, Q: Q, Ops: Ops,
    BUCKETS: BUCKETS, BUCKET_LABEL: BUCKET_LABEL, KINDS: KINDS, PAY_TYPES: PAY_TYPES, PAY_MODES: PAY_MODES, TERMS: TERMS, PO_STATUS: PO_STATUS, CQ_STATUS: CQ_STATUS, SENT_VIA: SENT_VIA, DAMAGE_LOC: Object.keys(DAMAGE_LOC),
    today: today, save: save, reset: reset, reload: load,
    storageOK: function () { return storageOK; },
    getSession: getSession, setSession: setSession, currentUser: currentUser,
    money: money
  };
})(typeof window !== 'undefined' ? window : globalThis);
