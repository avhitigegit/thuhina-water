/* Thuhina Water prototype – shared layout: sidebar, top bar, role switcher and
   role-based access (BRD section 5, FR-56). NAV is also used by the sitemap (index.html). */
(function (global) {
  'use strict';
  var TW = global.TW, UI = global.UI;

  // Access codes: A = Admin, C = Accountant, D = Delivery Staff; "v" suffix = view only.
  var NAV = [
    { module: 'Dashboard', icon: 'home', items: [
      { id: 'dashboard/dashboard', title: 'Dashboard', brd: 'Sec 8 Dashboard, FR-31', acc: 'A C', desc: 'Today at a glance' }
    ] },
    { module: 'Master Data', icon: 'db', items: [
      { id: 'master-data/bottles-products', title: 'Bottles & Products', brd: 'FR-01, FR-02, FR-04, FR-05, FR-06, FR-07, BR-01, BR-02, BR-08, BR-18', acc: 'A', desc: 'Bottle types, other products, standard prices, deposits, customer agreed prices and accepted old bottles' },
      { id: 'master-data/customers', title: 'Customers', brd: 'FR-03, FR-05, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-32, FR-33, FR-53, BR-08, BR-09, BR-16, BR-18', acc: 'A Cv', desc: 'Register customers (with the bottles given that day and any agreed price), edit and find them; click a customer for details and prices' },
      { id: 'master-data/suppliers', title: 'Suppliers & Factories', brd: 'FR-14, FR-22, BR-11', acc: 'A Cv', desc: 'Bottle and product suppliers, and the filling factories' }
    ] },
    { module: 'Purchasing', icon: 'cart', items: [
      { id: 'purchasing/purchasing', title: 'Purchasing', brd: 'FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-28, BR-10', acc: 'A Cv', desc: 'Quotations, purchase orders, goods receipt and supplier payments' }
    ] },
    { module: 'Production', icon: 'factory', items: [
      { id: 'production/production', title: 'Filling Factory', brd: 'FR-23, FR-24, FR-25, FR-26, FR-28, FR-29, BR-11, BR-12', acc: 'A Cv', desc: 'Send empties, receive filled bottles, factory payments' }
    ] },
    { module: 'Inventory', icon: 'box', items: [
      { id: 'inventory/inventory', title: 'Stock', brd: 'FR-27, FR-28, FR-29, FR-30, FR-31, BR-05, BR-06, BR-12, BR-15', acc: 'A', desc: 'Bottle and product stock, damage, adjustments and low-stock alerts' }
    ] },
    { module: 'Sales & Deliveries', icon: 'truck', items: [
      { id: 'sales/enter-bills', title: 'Enter Bills', brd: 'FR-34, FR-35, FR-36, FR-38, FR-41, FR-46, FR-47, FR-48, BR-03, BR-04, BR-07, BR-09, BR-13, NFR-04', acc: 'A', desc: 'Type in the day\'s paper bills quickly with the keyboard' },
      { id: 'sales/sales-bills', title: 'Sales & Bills', brd: 'FR-32, FR-33, FR-36, FR-37, FR-38, FR-39, FR-40, FR-41, FR-59, BR-01, BR-02, BR-06, BR-07, BR-13, BR-14', acc: 'A', desc: 'All bills, plus first purchase, extra order, product sale and other entries' },
      { id: 'sales/quotations', title: 'Customer Quotations', brd: 'FR-64, FR-65, FR-66, BR-20', acc: 'A Cv', desc: 'Price quotations for customers and new organisations (government offices, factories): print or email, then mark accepted or not' }
    ] },
    { module: 'Delivery Schedule', icon: 'calendar', items: [
      { id: 'delivery/delivery-planning', title: 'Delivery Planning', brd: 'FR-58, FR-45', acc: 'A', desc: 'Pick a date (normally tomorrow) to see who is due by route, the bottles needed and whether they are ready' },
      { id: 'delivery/delivery-schedule', title: 'Daily Delivery List', brd: 'FR-42, FR-43, FR-44, FR-45, FR-46', acc: 'A D', desc: 'Who to deliver to each day, and bottles needed for coming days' }
    ] },
    { module: 'Billing & Payments', icon: 'money', items: [
      { id: 'billing/billing', title: 'Billing & Payments', brd: 'FR-47, FR-48, FR-49, FR-50, FR-51, FR-52, BR-09', acc: 'A C', desc: 'Customer balances, payments, statements and monthly invoices' }
    ] },
    { module: 'Expenses', icon: 'receipt', items: [
      { id: 'expenses/expenses', title: 'Expenses', brd: 'FR-62, FR-63, BR-19', acc: 'A C', desc: 'Running expenses by month – electricity, water, salaries, rent, vehicle fuel and spare parts, and more' }
    ] },
    // Hidden for now at the client's request – set hidden: false to show it again (page kept: migration/data-migration.html).
    { module: 'Data Migration', icon: 'upload', hidden: true, items: [
      { id: 'migration/data-migration', title: 'Data Migration', brd: 'FR-53, FR-54, FR-55', acc: 'A', desc: 'Import existing customers and opening stock' }
    ] },
    { module: 'Reports', icon: 'chart', items: [
      { id: 'reports/reports', title: 'Reports', brd: 'Sec 8 – Sales, Purchasing, Production, Stock, Bottle movement, Damage and write-off, Outstanding and aging, Profit, Delivery', acc: 'A C', desc: 'Choose a report and a date range' }
    ] },
    { module: 'Administration', icon: 'shield', items: [
      { id: 'admin/administration', title: 'Administration', brd: 'FR-56, FR-57, BR-14, BR-17, FR-60, NFR-05, Sec 5', acc: 'A', desc: 'Users, roles and audit log' }
    ] }
  ];
  var ROLE_CODE = { Admin: 'A', Accountant: 'C', 'Delivery Staff': 'D' };
  var ROLES = ['Admin', 'Accountant', 'Delivery Staff'];
  var HOME = { Admin: 'dashboard/dashboard', Accountant: 'dashboard/dashboard', 'Delivery Staff': 'delivery/delivery-schedule' };

  var ICONS = {
    home: '<path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
    db: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
    users: '<circle cx="9" cy="8" r="4"/><path d="M1 21c0-4 3.6-6 8-6s8 2 8 6"/><path d="M17 11a3 3 0 1 0 0-6M23 21c0-3-2-5-5-5.5"/>',
    cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h3l3 12h11l2-8H6"/>',
    factory: '<path d="M2 21V10l6 4V10l6 4V4h4l2 17z"/>',
    box: '<path d="M21 8l-9-5-9 5v8l9 5 9-5z"/><path d="M3 8l9 5 9-5M12 13v8"/>',
    truck: '<path d="M1 4h14v12H1zM15 9h4l3 4v3h-7"/><circle cx="5.5" cy="18.5" r="2"/><circle cx="18.5" cy="18.5" r="2"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
    money: '<rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="12" cy="12" r="3"/>',
    upload: '<path d="M12 16V4M7 9l5-5 5 5M4 20h16"/>',
    receipt: '<path d="M5 2h14v20l-3-2-2 2-2-2-2 2-2-2-3 2z"/><path d="M9 7h6M9 11h6M9 15h4"/>',
    chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    shield: '<path d="M12 2l8 4v6c0 5-3.5 9-8 10-4.5-1-8-5-8-10V6z"/>',
    chev: '<path d="M6 9l6 6 6-6"/>'
  };
  function icon(name, cls) {
    return '<svg class="' + (cls || '') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + ICONS[name] + '</svg>';
  }

  /* Root folder of the prototype, derived from this script's URL. */
  var script = document.currentScript;
  var ROOT = script ? script.src.replace(/assets\/js\/layout\.js.*$/, '') : '';

  function access(item, role) {
    var code = ROLE_CODE[role];
    var parts = item.acc.split(' ');
    for (var i = 0; i < parts.length; i++) {
      if (parts[i].charAt(0) === code) return parts[i].length > 1 ? 'view' : 'full';
    }
    return null;
  }
  function findItem(id) {
    for (var i = 0; i < NAV.length; i++) for (var j = 0; j < NAV[i].items.length; j++) if (NAV[i].items[j].id === id) return { item: NAV[i].items[j], module: NAV[i] };
    return null;
  }
  function url(id, params) {
    var q = params ? '?' + new URLSearchParams(params).toString() : '';
    return ROOT + id + '.html' + q;
  }
  function lsGet(k) { try { return global.localStorage.getItem(k); } catch (e) { return null; } }
  function lsSet(k, v) { try { global.localStorage.setItem(k, v); } catch (e) { /* ignore */ } }

  var App = {
    NAV: NAV, ROLES: ROLES, HOME: HOME, ROOT: ROOT,
    url: url, access: access, findItem: findItem, icon: icon,
    go: function (id, params) { location.href = url(id, params); },
    /* Can the current role open page `id`? Returns 'full', 'view' or null. */
    can: function (id) { var f = findItem(id); return f && App.role ? access(f.item, App.role) : null; },
    /* Link to a page only if the current role can open it; otherwise plain text. */
    link: function (id, params, text, cls) {
      return App.can(id) ? '<a' + (cls ? ' class="' + cls + '"' : '') + ' href="' + url(id, params) + '">' + text + '</a>' : text;
    },
    role: null, access_: null, allowed: false
  };

  function setRole(role) {
    var u = TW.db.users.filter(function (x) { return x.role === role && x.active; })[0];
    TW.setSession({ userId: u.id, role: role });
    TW.Ops.logEvent('Login', 'Session', u.username, 'Switched role to ' + role + ' (prototype role switcher)');
  }

  function buildShell() {
    var pageId = document.body.getAttribute('data-page');
    var session = TW.getSession();
    if (!session) {
      location.replace(ROOT + 'login.html?next=' + encodeURIComponent(pageId));
      return;
    }
    var user = TW.currentUser();
    App.role = user.role;
    var found = findItem(pageId);
    var acc = found ? access(found.item, user.role) : null;
    App.access_ = acc;
    App.allowed = !!acc;
    App.viewOnly = acc === 'view';
    if (App.viewOnly) document.body.classList.add('view-only');

    var appEl = document.getElementById('app');
    var actions = appEl.querySelector(':scope > .page-actions');
    if (actions) actions.remove();

    // Sidebar
    var collapsed = {};
    try { collapsed = JSON.parse(lsGet('thuhina.nav.collapsed') || '{}'); } catch (e) { collapsed = {}; }
    var side = '<div class="brandbar"><div class="logo">T</div><div><b>Thuhina Water</b><span>Inventory &amp; Sales</span></div></div>';
    NAV.forEach(function (g) {
      if (g.hidden) return; // hidden modules are not shown in the menu
      var items = g.items.filter(function (it) { return access(it, user.role); });
      if (!items.length) return;
      var isCur = found && found.module === g;
      var tag = function (it) { return access(it, user.role) === 'view' ? '<span class="view-tag">view</span>' : ''; };
      if (g.items.length === 1) {
        var it1 = items[0];
        side += '<a class="nav-single' + (found && found.item === it1 ? ' active' : '') + '" href="' + url(it1.id) + '">' + icon(g.icon) + '<span>' + UI.esc(g.module) + '</span>' + tag(it1) + '</a>';
        return;
      }
      side += '<div class="nav-group' + (collapsed[g.module] && !isCur ? ' collapsed' : '') + '" data-mod="' + UI.esc(g.module) + '"><button class="nav-head" type="button">' + icon(g.icon) + '<span>' + UI.esc(g.module) + '</span>' + icon('chev', 'chev') + '</button><ul>';
      items.forEach(function (it) {
        side += '<li><a href="' + url(it.id) + '"' + (found && found.item === it ? ' class="active"' : '') + '>' + UI.esc(it.title) + tag(it) + '</a></li>';
      });
      side += '</ul></div>';
    });

    var roleSel = ROLES.map(function (r) { return '<option' + (r === user.role ? ' selected' : '') + '>' + r + '</option>'; }).join('');
    var top = '<span class="crumbs">' + (found ? UI.esc(found.module.module) + (found.module.items.length > 1 ? ' › ' + UI.esc(found.item.title) : '') : '') + '</span><span class="spacer"></span>' +
      (!TW.storageOK() ? '<span class="badge bad" title="localStorage is blocked; changes will not carry between pages">Storage unavailable</span>' : '') +
      '<span class="biz-date" title="Business date used by the prototype">Today: ' + TW.D.dayName(TW.today()).slice(0, 3) + ' ' + TW.D.dmy(TW.today()) + '</span>' +
      '<label>Role <select id="role-switch">' + roleSel + '</select></label>' +
      '<div class="who"><b>' + UI.esc(user.name) + '</b><span class="muted">' + UI.esc(user.role) + '</span></div>' +
      '<button class="btn sm" id="btn-reset" title="Restore the original demo data">Reset demo data</button>' +
      '<a class="btn sm" href="' + ROOT + 'index.html" title="All screens with BRD IDs">Sitemap</a>' +
      '<button class="btn sm" id="btn-logout">Log out</button>';

    var head = '';
    if (found) {
      head = '<div class="page-head"><div><h1>' + UI.esc(found.item.title) + '</h1><p class="sub">' + UI.esc(found.item.desc) + '</p></div><div class="page-actions-slot"></div></div>';
    }

    var shell = document.createElement('div');
    shell.className = 'shell';
    shell.innerHTML = '<aside class="sidebar no-print">' + side + '</aside><div class="main"><header class="topbar no-print">' + top + '</header><div class="content">' + head + '</div></div>';
    var content = shell.querySelector('.content');
    if (App.allowed) {
      if (App.viewOnly) content.insertAdjacentHTML('beforeend', '<div class="banner view">View-only access for the ' + UI.esc(user.role) + ' role. Entry and edit actions are hidden.</div>');
      if (actions) shell.querySelector('.page-actions-slot').appendChild(actions);
      while (appEl.firstChild) content.appendChild(appEl.firstChild);
    } else {
      content.innerHTML = '<div class="card no-access"><h2>No access</h2><p>The <b>' + UI.esc(user.role) + '</b> role cannot open <b>' + UI.esc(found ? found.item.title : pageId) + '</b> (BRD section 5).</p><p><a class="btn primary" href="' + url(HOME[user.role]) + '">Go to my home page</a></p></div>';
    }
    appEl.replaceWith(shell);

    // View-only: disable entry forms
    if (App.viewOnly) UI.$$('.write-form input, .write-form select, .write-form textarea, .write-form button', shell).forEach(function (el) { el.disabled = true; });

    // Events
    UI.$$('.nav-head', shell).forEach(function (b) {
      b.addEventListener('click', function () {
        var g = b.parentNode;
        g.classList.toggle('collapsed');
        collapsed[g.getAttribute('data-mod')] = g.classList.contains('collapsed');
        lsSet('thuhina.nav.collapsed', JSON.stringify(collapsed));
      });
    });
    var activeLink = shell.querySelector('.nav-group a.active');
    if (activeLink && activeLink.scrollIntoView) activeLink.scrollIntoView({ block: 'center' });
    shell.querySelector('#role-switch').addEventListener('change', function (e) {
      setRole(e.target.value);
      var next = found && access(found.item, e.target.value) ? location.href : url(HOME[e.target.value]);
      location.href = next;
    });
    shell.querySelector('#btn-logout').addEventListener('click', function () {
      TW.Ops.logEvent('Logout', 'Session', '', 'Signed out');
      TW.setSession(null);
      location.href = ROOT + 'login.html';
    });
    shell.querySelector('#btn-reset').addEventListener('click', function () {
      UI.confirm('Reset demo data?', '<p>This restores the original placeholder data and history. All changes made in the prototype (bills, payments, customers, POs…) are removed.</p><p class="muted small">Your role and login stay the same.</p>', 'Reset demo data', 'danger').then(function (yes) {
        if (!yes) return;
        TW.reset();
        UI.toast('Demo data restored', 'ok');
        setTimeout(function () { location.reload(); }, 400);
      });
    });
    document.title = (found ? found.item.title : 'Thuhina Water') + ' · Thuhina Water';
  }

  /* Pages call App.page(fn) – fn runs only when the role may open the page. */
  App.page = function (fn) {
    if (!App.allowed) return;
    try { fn(); } catch (e) {
      console.error(e);
      UI.toast('Prototype error: ' + e.message, 'bad');
    }
  };

  global.App = App;
  var pid = document.body.getAttribute('data-page');
  if (pid && pid !== 'index' && pid !== 'login') buildShell();
})(window);
