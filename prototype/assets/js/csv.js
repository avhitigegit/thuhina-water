/* Thuhina Water prototype – tiny CSV parser and file reader for the Data Migration screens (FR-53/54/55). */
(function (global) {
  'use strict';
  function parse(text) {
    var rows = [], row = [], f = '', q = false;
    text = text.replace(/^﻿/, '');
    for (var i = 0; i < text.length; i++) {
      var ch = text[i];
      if (q) {
        if (ch === '"') { if (text[i + 1] === '"') { f += '"'; i++; } else q = false; }
        else f += ch;
      } else if (ch === '"') q = true;
      else if (ch === ',') { row.push(f); f = ''; }
      else if (ch === '\n' || ch === '\r') {
        if (ch === '\r' && text[i + 1] === '\n') i++;
        row.push(f); f = '';
        if (row.some(function (x) { return x.trim() !== ''; })) rows.push(row);
        row = [];
      } else f += ch;
    }
    row.push(f);
    if (row.some(function (x) { return x.trim() !== ''; })) rows.push(row);
    return rows;
  }
  /* Read a picked file. Excel files cannot be parsed without a library, so the prototype asks for CSV. */
  function readFile(file, cb) {
    if (!file) return;
    if (/\.xlsx?$/i.test(file.name)) { cb(null, 'Excel files: in the prototype please use File → Save As → CSV. The real system will read .xlsx directly.'); return; }
    var r = new FileReader();
    r.onload = function () { cb(parse(String(r.result)), null); };
    r.onerror = function () { cb(null, 'Could not read the file.'); };
    r.readAsText(file);
  }
  global.CSV = { parse: parse, readFile: readFile };
})(window);
