"use client";

/*
 * List table with server paging and sorting (design 7.1: ?page=0&size=50&sort=name,asc → { items, page, size, total }).
 * Prototype look (UI.table): empty state text, clickable rows, optional footer row.
 *
 *   const table = useTableState({ sort: { key: "name", dir: "asc" } });
 *   const q = useQuery({ queryKey: ["customers", table.params], queryFn: () => api("/customers", { query: table.params }) });
 *   <DataTable columns={cols} page={q.data} loading={q.isPending} state={table} onRowClick={open} />
 */
import { useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface PageData<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type SortDir = "asc" | "desc";
export interface SortState {
  key: string;
  dir: SortDir;
}

export interface Column<T> {
  /** Column id; also the sort field sent to the server when `sortable`. */
  key: string;
  header: ReactNode;
  cell: (row: T, index: number) => ReactNode;
  sortable?: boolean;
  /** "num" right-aligns numbers and money. */
  align?: "num" | "center";
  className?: string;
}

export interface TableState {
  page: number;
  size: number;
  sort: SortState | null;
  setPage: (page: number) => void;
  setSort: (sort: SortState) => void;
  /** Query parameters for the API call. */
  params: { page: number; size: number; sort?: string };
}

export function useTableState(initial?: { size?: number; sort?: SortState }): TableState {
  const [page, setPage] = useState(0);
  const [size] = useState(initial?.size ?? 50);
  const [sort, setSortState] = useState<SortState | null>(initial?.sort ?? null);
  return {
    page,
    size,
    sort,
    setPage,
    setSort: (s) => {
      setSortState(s);
      setPage(0);
    },
    params: { page, size, ...(sort ? { sort: `${sort.key},${sort.dir}` } : {}) },
  };
}

export function DataTable<T>({
  columns,
  page,
  rows,
  state,
  loading,
  empty = "No records found.",
  onRowClick,
  rowKey,
  rowClassName,
  footer,
}: {
  columns: Column<T>[];
  /** Server page (paged lists) … */
  page?: PageData<T>;
  /** … or plain rows (short lists without paging). */
  rows?: T[];
  state?: TableState;
  loading?: boolean;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
  rowKey?: (row: T, index: number) => string | number;
  rowClassName?: (row: T) => string | undefined;
  /** Footer cells by column index (totals). */
  footer?: ReactNode[];
}) {
  const items = page?.items ?? rows ?? [];
  const sort = state?.sort ?? null;

  function onHeaderClick(c: Column<T>) {
    if (!c.sortable || !state) return;
    const dir: SortDir = sort?.key === c.key && sort.dir === "asc" ? "desc" : "asc";
    state.setSort({ key: c.key, dir });
  }

  return (
    <div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              {columns.map((c) => {
                const sorted = sort?.key === c.key ? sort.dir : null;
                return (
                  <th
                    key={c.key}
                    className={cn(c.align, c.sortable && state && "sortable", c.className)}
                    aria-sort={sorted === "asc" ? "ascending" : sorted === "desc" ? "descending" : undefined}
                    onClick={() => onHeaderClick(c)}
                  >
                    {c.header}
                    {sorted && <span aria-hidden="true">{sorted === "asc" ? " ▲" : " ▼"}</span>}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={columns.length} className="empty-state">
                  Loading…
                </td>
              </tr>
            ) : items.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className="empty-state">
                  {empty}
                </td>
              </tr>
            ) : (
              items.map((row, i) => (
                <tr
                  key={rowKey ? rowKey(row, i) : i}
                  className={cn(onRowClick && "clickable", rowClassName?.(row))}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {columns.map((c) => (
                    <td key={c.key} className={cn(c.align, c.className)}>
                      {c.cell(row, i)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
          {footer && !loading && items.length > 0 && (
            <tfoot>
              <tr>
                {columns.map((c, i) => (
                  <td key={c.key} className={cn(c.align, c.className)}>
                    {footer[i]}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
      {page && state && <Pager page={page} onPage={state.setPage} />}
    </div>
  );
}

function Pager<T>({ page, onPage }: { page: PageData<T>; onPage: (p: number) => void }) {
  const { total, size } = page;
  if (total <= size && page.page === 0) {
    return total > 0 ? <div className="pager">{total.toLocaleString("en-US")} records</div> : null;
  }
  const from = page.page * size + 1;
  const to = Math.min(total, from + page.items.length - 1);
  const last = Math.max(0, Math.ceil(total / size) - 1);
  return (
    <div className="pager">
      <span>
        {from.toLocaleString("en-US")}–{to.toLocaleString("en-US")} of {total.toLocaleString("en-US")}
      </span>
      <button
        type="button"
        className="btn sm"
        disabled={page.page <= 0}
        onClick={() => onPage(page.page - 1)}
      >
        ‹ Previous
      </button>
      <button
        type="button"
        className="btn sm"
        disabled={page.page >= last}
        onClick={() => onPage(page.page + 1)}
      >
        Next ›
      </button>
    </div>
  );
}
