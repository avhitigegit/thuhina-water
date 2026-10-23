"use client";

/*
 * Customers (M05, prototype master-data/customers.html): search and filters (area, delivery day, type, payment,
 * price, status), paged list with balances, row click → side panel, Edit / Deactivate per row, New customer and
 * Customer types (Admin). ?id=C0012 opens a customer's panel, ?new=1 the New customer pop-up, ?fromQuote=<id> the New
 * customer pop-up pre-filled from an accepted customer quotation (M16 Register as customer).
 */
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { MoneyText } from "@/components/shared/MoneyText";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Can, useCan } from "@/features/auth/access";
import { useAreas, useCustomer, useCustomerList, useCustomerTypes } from "@/features/customers/api";
import { CustomerDialog, type CustomerPrefill } from "@/features/customers/CustomerDialog";
import { CustomerPanel } from "@/features/customers/CustomerPanel";
import { cycleShort, DAYS, dayName, payLabel, PAY_TYPES } from "@/features/customers/labels";
import { StatusDialog } from "@/features/customers/StatusDialog";
import { TypesAreasDialog } from "@/features/customers/TypesAreasDialog";
import { usualFromQuote } from "@/features/quotations/usualFromQuote";
import { api } from "@/lib/api/client";
import type { CqView, CustomerListRow, CustomerLookupRow } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import { P } from "@/lib/permissions";

const PAGE_SIZE = 50;

function StatusFromList({ id, onClose }: { id: number; onClose: () => void }) {
  const c = useCustomer(id);
  return c.data ? <StatusDialog customer={c.data} onClose={onClose} /> : null;
}

function CustomersPage() {
  const params = useSearchParams();
  const canEdit = useCan(P.CUSTOMERS_EDIT);
  const areas = useAreas();
  const types = useCustomerTypes();
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [filters, setFilters] = useState({
    area: "",
    day: "",
    type: "",
    pay: "",
    price: "",
    status: "ACTIVE",
  });
  const filterKey = JSON.stringify([q, filters]);
  const [paging, setPaging] = useState({ key: filterKey, page: 0 });
  const page = paging.key === filterKey ? paging.page : 0;
  const list = useCustomerList({ q, ...filters, page, size: PAGE_SIZE });

  // Panel / pop-ups. editing: undefined = closed, null = new customer, number = edit that customer.
  const [panelId, setPanelId] = useState<number | null>(null);
  const [editing, setEditing] = useState<number | null | undefined>(undefined);
  const [statusId, setStatusId] = useState<number | null>(null);
  const [typesOpen, setTypesOpen] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setQ(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  // ?id=C0012 opens that customer's panel; ?new=1 the New customer pop-up (links from other screens).
  const idParam = params.get("id");
  const newParam = params.get("new");
  useEffect(() => {
    if (!idParam) return;
    let cancelled = false;
    api<CustomerLookupRow[]>("/customers/lookup", { query: { q: idParam } })
      .then((rows) => {
        if (!cancelled && rows[0]) setPanelId(rows[0].id);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [idParam]);
  // ?fromQuote=12: Register as customer from an accepted quotation – the New customer pop-up pre-filled.
  const [prefill, setPrefill] = useState<CustomerPrefill | undefined>(undefined);
  const fromQuote = params.get("fromQuote");
  useEffect(() => {
    if (!fromQuote || !canEdit) return;
    let cancelled = false;
    api<CqView>(`/customer-quotations/${fromQuote}`)
      .then((q) => {
        if (cancelled || !q.canRegister) return;
        setPrefill({
          quotationId: q.id,
          name: q.orgName,
          phone: q.phone ?? "",
          address: q.address ?? "",
          areaId: q.areaId ?? null,
          customerTypeId: q.customerTypeId,
          notes: q.registerNotes,
          agreedPrices: Object.fromEntries(q.agreedPrices.map((a) => [a.bottleTypeCode, a.price])),
          agreedReason: `Quotation ${q.cqNo} accepted`,
          usualQty: usualFromQuote(q.lines),
        });
        setEditing(null);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [fromQuote, canEdit]);
  const [newHandled, setNewHandled] = useState(false);
  if (newParam && canEdit && !newHandled) {
    setNewHandled(true);
    setEditing(null);
  }

  const set = (k: keyof typeof filters) => (e: React.ChangeEvent<HTMLSelectElement>) =>
    setFilters({ ...filters, [k]: e.target.value });

  const columns: Column<CustomerListRow>[] = [
    {
      key: "customer",
      header: "Customer",
      cell: (c) => (
        <>
          <b>{c.name}</b>
          <div className="small muted">
            {c.code} · {c.address}
          </div>
        </>
      ),
    },
    { key: "phone", header: "Phone", cell: (c) => <span className="nowrap">{c.phone}</span> },
    { key: "area", header: "Area", cell: (c) => c.areaName },
    {
      key: "type",
      header: "Type",
      cell: (c) => (
        <>
          {c.typeName}
          {c.agreed && (
            <div>
              <span className="badge info" title={c.agreed}>
                agreed price
              </span>
            </div>
          )}
        </>
      ),
    },
    {
      key: "delivery",
      header: "Delivery",
      cell: (c) => (
        <>
          {dayName(c.deliveryDay).slice(0, 3)}, {cycleShort(c.cycleWeeks)}
          <div className="small muted">next {formatDate(c.nextDeliveryDate)}</div>
        </>
      ),
    },
    { key: "pay", header: "Payment", cell: (c) => <StatusBadge status={payLabel(c.payType)} /> },
    {
      key: "bottles",
      header: "Bottles",
      align: "num",
      cell: (c) => (
        <>
          {c.bottles
            .filter((b) => b.held)
            .map((b) => `${b.held}×${b.label}`)
            .join(" ") || "0"}
          {c.toCollect > 0 && (
            <>
              {" "}
              <Badge kind="warn">{c.toCollect} collect</Badge>
            </>
          )}
          {c.owed > 0 && (
            <>
              {" "}
              <Badge kind="bad">{c.owed} owed</Badge>
            </>
          )}
        </>
      ),
    },
    {
      key: "balance",
      header: "Balance",
      align: "num",
      cell: (c) =>
        c.balance ? (
          c.overLimit ? (
            <b style={{ color: "var(--bad)" }}>
              <MoneyText value={c.balance} />
            </b>
          ) : (
            <MoneyText value={c.balance} />
          )
        ) : null,
    },
    {
      key: "status",
      header: "Status",
      cell: (c) => (
        <>
          <StatusBadge status={c.status === "ACTIVE" ? "Active" : "Inactive"} />
          {c.status !== "ACTIVE" && c.statusDate && (
            <div className="small muted" title={c.statusReason ?? ""}>
              since {formatDate(c.statusDate)}
            </div>
          )}
        </>
      ),
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (c) =>
        canEdit ? (
          <>
            <button
              className="btn sm"
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setEditing(c.id);
              }}
            >
              Edit
            </button>
            <button
              className={c.status === "ACTIVE" ? "btn sm danger" : "btn sm"}
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setStatusId(c.id);
              }}
            >
              {c.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
            </button>
          </>
        ) : null,
    },
  ];

  const data = list.data;
  return (
    <>
      <Can permission={P.CUSTOMERS_EDIT}>
        <PageActions>
          <button className="btn" type="button" onClick={() => setTypesOpen(true)}>
            Customer types
          </button>
          <button className="btn primary" type="button" onClick={() => setEditing(null)}>
            + New customer
          </button>
        </PageActions>
      </Can>
      <div className="card">
        <div className="toolbar">
          <div className="field grow">
            <label htmlFor="c-q">Search</label>
            <input
              id="c-q"
              value={search}
              autoFocus
              placeholder="Name, phone, code or address"
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="c-area">Area</label>
            <select id="c-area" value={filters.area} onChange={set("area")}>
              <option value="">All</option>
              {areas.data?.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="c-day">Delivery day</label>
            <select id="c-day" value={filters.day} onChange={set("day")}>
              <option value="">All</option>
              {DAYS.map((d, i) => (
                <option key={d} value={i + 1}>
                  {d}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="c-type">Type</label>
            <select id="c-type" value={filters.type} onChange={set("type")}>
              <option value="">All</option>
              {types.data?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="c-pay">Payment</label>
            <select id="c-pay" value={filters.pay} onChange={set("pay")}>
              <option value="">All</option>
              {PAY_TYPES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="c-price">Price</label>
            <select id="c-price" value={filters.price} onChange={set("price")}>
              <option value="">All</option>
              <option value="STANDARD">Standard</option>
              <option value="AGREED">Agreed</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="c-status">Status</label>
            <select id="c-status" value={filters.status} onChange={set("status")}>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
              <option value="ALL">All</option>
            </select>
          </div>
        </div>
        <div className="small muted" style={{ marginBottom: 6 }}>
          {data
            ? `${data.total} of ${data.all} customers (${data.active} active, ${data.inactive} inactive) · click a row to see details`
            : " "}
        </div>
        {list.isError ? (
          <div className="banner bad">{list.error.message}</div>
        ) : (
          <DataTable
            columns={columns}
            page={data}
            loading={list.isPending}
            state={{
              page,
              size: PAGE_SIZE,
              sort: null,
              setPage: (p) => setPaging({ key: filterKey, page: p }),
              setSort: () => undefined,
              params: { page, size: PAGE_SIZE },
            }}
            rowKey={(c) => c.id}
            rowClassName={(c) => (c.status === "ACTIVE" ? undefined : "row-muted")}
            onRowClick={(c) => setPanelId(c.id)}
            empty="No customers match."
          />
        )}
      </div>

      <CustomerPanel
        customerId={panelId}
        onClose={() => setPanelId(null)}
        onEdit={(c) => {
          setPanelId(null);
          setEditing(c.id);
        }}
      />
      {editing !== undefined && (
        <CustomerDialog
          key={editing ?? (prefill ? "quote" : "new")}
          customerId={editing}
          prefill={editing === null ? prefill : undefined}
          onClose={() => {
            setEditing(undefined);
            setPrefill(undefined);
          }}
          onSaved={(c) => setPanelId(c.id)}
        />
      )}
      {statusId !== null && <StatusFromList id={statusId} onClose={() => setStatusId(null)} />}
      {typesOpen && <TypesAreasDialog onClose={() => setTypesOpen(false)} />}
    </>
  );
}

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <CustomersPage />
    </Suspense>
  );
}
