/*
 * CSV download that Excel opens directly (prototype UI.tableToCSV): UTF-8 with a byte-order mark so "–" and "Rs." show
 * correctly, every cell quoted.
 */

/** Rows of cells → CSV text (CRLF line ends, quotes doubled). */
export function toCsv(rows: (string | number)[][]): string {
  return rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(",")).join("\r\n");
}

export function downloadCsv(filename: string, rows: (string | number)[][]) {
  const blob = new Blob(["﻿" + toCsv(rows)], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
