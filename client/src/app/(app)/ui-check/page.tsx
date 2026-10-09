"use client";

/*
 * Shared-components check page (M00 acceptance: "Calendar picker works on a test page").
 * Admin only; not in the sidebar – open /ui-check. Uses sample values only, nothing is saved.
 */
import { useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { DataTable, useTableState, type Column, type PageData } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { MoneyText } from "@/components/shared/MoneyText";
import { ReasonDialog } from "@/components/shared/ReasonDialog";
import { SidePanel } from "@/components/shared/SidePanel";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { StepBar } from "@/components/shared/StepBar";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { formatDate, todayIso } from "@/lib/format";
import { toast } from "@/lib/toast";

interface SampleRow {
  code: string;
  name: string;
  area: string;
  balance: number;
  status: string;
}

const AREAS = ["Kandy", "Peradeniya", "Katugastota", "Gampola", "Kundasale"];
const SAMPLE: SampleRow[] = Array.from({ length: 23 }, (_, i) => ({
  code: "C" + String(i + 1).padStart(4, "0"),
  name: `Sample customer ${i + 1}`,
  area: AREAS[i % AREAS.length],
  balance: ((i * 7919) % 9000) + (i % 3 === 0 ? 0.5 : 0),
  status: i % 6 === 5 ? "Inactive" : "Active",
}));

const COLUMNS: Column<SampleRow>[] = [
  { key: "code", header: "Code", cell: (r) => <span className="mono">{r.code}</span>, sortable: true },
  { key: "name", header: "Name", cell: (r) => r.name, sortable: true },
  { key: "area", header: "Area", cell: (r) => r.area },
  {
    key: "balance",
    header: "Balance",
    cell: (r) => <MoneyText value={r.balance} />,
    align: "num",
    sortable: true,
  },
  { key: "status", header: "Status", cell: (r) => <StatusBadge status={r.status} /> },
];

/** Paging and sorting done here the way the server will do it. */
function samplePage(
  page: number,
  size: number,
  sort: { key: string; dir: "asc" | "desc" } | null,
): PageData<SampleRow> {
  const rows = [...SAMPLE];
  if (sort) {
    const k = sort.key as keyof SampleRow;
    rows.sort((a, b) => (a[k] < b[k] ? -1 : a[k] > b[k] ? 1 : 0) * (sort.dir === "asc" ? 1 : -1));
  }
  return { items: rows.slice(page * size, page * size + size), page, size, total: rows.length };
}

const TABS = ["dates", "dialogs", "table", "badges"] as const;

export default function UiCheckPage() {
  const [tab, setTab] = useHashTab(TABS);
  const [date, setDate] = useState<string | null>(todayIso());
  const [emptyDate, setEmptyDate] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reasonOpen, setReasonOpen] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);
  const [formDate, setFormDate] = useState<string | null>(null);
  const [selected, setSelected] = useState<SampleRow | null>(null);
  const table = useTableState({ size: 10, sort: { key: "code", dir: "asc" } });

  return (
    <>
      <PageActions>
        <button className="btn" type="button" onClick={() => toast("Saved", "ok")}>
          Toast
        </button>
        <button className="btn primary" type="button" onClick={() => setFormOpen(true)}>
          + New
        </button>
      </PageActions>

      <Tabs
        tabs={[
          { id: "dates", label: "Dates & money" },
          { id: "dialogs", label: "Pop-ups" },
          { id: "table", label: "Table" },
          { id: "badges", label: "Badges, KPIs, steps" },
        ]}
        value={tab}
        onChange={setTab}
      />

      {tab === "dates" && (
        <div className="grid c2">
          <div className="card">
            <h3>Calendar picker</h3>
            <div className="form-grid">
              <div className="field">
                <label htmlFor="d1">Date</label>
                <DateField id="d1" value={date} onChange={setDate} />
                <span className="help">
                  Value sent to the API: <span className="mono">{date ?? "(empty)"}</span>
                </span>
              </div>
              <div className="field">
                <label htmlFor="d2">Empty date</label>
                <DateField id="d2" value={emptyDate} onChange={setEmptyDate} />
                <span className="help">Type 05102026 – the slashes are added.</span>
              </div>
              <div className="field">
                <label htmlFor="d3">Disabled</label>
                <DateField id="d3" value={todayIso()} onChange={() => undefined} disabled />
              </div>
            </div>
            <p className="small muted" style={{ marginTop: 12 }}>
              Check: pick a day, type a date, Today, Clear, ‹ › months, Monday first. Scroll the page with the
              calendar open – it follows the field; near the bottom of the window it opens above.
            </p>
          </div>
          <div className="card">
            <h3>Money and dates</h3>
            <div className="summary">
              <span>1350</span>
              <MoneyText value={1350} />
              <span>12500.5</span>
              <MoneyText value={12500.5} />
              <span>-750</span>
              <MoneyText value={-750} />
              <span>Today</span>
              <b>{formatDate(todayIso())}</b>
            </div>
            <h3 style={{ marginTop: 16 }}>Number input</h3>
            <input type="number" defaultValue={12} style={{ width: 90 }} aria-label="Quantity" />
          </div>
        </div>
      )}

      {tab === "dialogs" && (
        <div className="card">
          <div className="toolbar">
            <button className="btn primary" type="button" onClick={() => setFormOpen(true)}>
              Form pop-up
            </button>
            <button className="btn" type="button" onClick={() => setConfirmOpen(true)}>
              Confirm
            </button>
            <button className="btn danger" type="button" onClick={() => setReasonOpen(true)}>
              Reverse (reason)
            </button>
            <button className="btn" type="button" onClick={() => setPanelOpen(true)}>
              Side panel
            </button>
            <button className="btn" type="button" onClick={() => toast("Something went wrong", "bad")}>
              Error toast
            </button>
          </div>
          <p className="small muted" style={{ margin: 0 }}>
            In the form pop-up, Enter moves to the next field and does not save; Ctrl+Enter or Save saves.
          </p>
        </div>
      )}

      {tab === "table" && (
        <div className="card">
          <DataTable
            columns={COLUMNS}
            page={samplePage(table.page, table.size, table.sort)}
            state={table}
            rowKey={(r) => r.code}
            rowClassName={(r) => (r.status === "Inactive" ? "row-muted" : undefined)}
            onRowClick={(r) => setSelected(r)}
          />
        </div>
      )}

      {tab === "badges" && (
        <>
          <KpiGrid>
            <KpiCard label="Sales today" value={<MoneyText value={48250} />} hint="31 bills" />
            <KpiCard
              label="Outstanding"
              value={<MoneyText value={182300} />}
              hint="12 customers overdue"
              alert
            />
            <KpiCard label="Filled 20L in stock" value="214" hint="Minimum 150" />
          </KpiGrid>
          <div className="card">
            <h3>Status badges</h3>
            <p style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {[
                "Active",
                "Inactive",
                "Draft",
                "Approved",
                "Partly received",
                "Cancelled",
                "Paid",
                "Overdue",
                "Agreed",
              ].map((s) => (
                <StatusBadge key={s} status={s} />
              ))}
            </p>
            <h3>Step bar</h3>
            <StepBar steps={["Draft", "Approved", "Sent", "Received"]} current="Sent" />
          </div>
        </>
      )}

      <FormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        title="New sample"
        submitLabel="Save"
        onSubmit={() => {
          setFormOpen(false);
          toast("Sample saved (nothing is stored)", "ok");
        }}
      >
        <div className="form-grid">
          <div className="field req">
            <label htmlFor="f-name">Name</label>
            <input id="f-name" autoFocus />
          </div>
          <div className="field">
            <label htmlFor="f-phone">Phone</label>
            <input id="f-phone" />
          </div>
          <div className="field">
            <label htmlFor="f-date">Start date</label>
            <DateField id="f-date" value={formDate} onChange={setFormDate} />
          </div>
          <div className="field">
            <label htmlFor="f-qty">Bottles</label>
            <input id="f-qty" type="number" defaultValue={0} />
          </div>
        </div>
      </FormDialog>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Approve purchase order?"
        confirmLabel="Approve"
        onConfirm={() => {
          setConfirmOpen(false);
          toast("Approved", "ok");
        }}
      >
        <p>The purchase order can then be sent to the supplier.</p>
      </ConfirmDialog>

      <ReasonDialog
        open={reasonOpen}
        onOpenChange={setReasonOpen}
        title="Reverse bill"
        label="Reason for the reversal"
        confirmLabel="Reverse"
        danger
        onConfirm={(reason) => {
          setReasonOpen(false);
          toast(`Reversed – ${reason}`, "ok");
        }}
      >
        <p className="small muted">A reversal entry is added; the original bill is kept.</p>
      </ReasonDialog>

      <SidePanel
        open={panelOpen || selected !== null}
        onOpenChange={(o) => {
          if (!o) {
            setPanelOpen(false);
            setSelected(null);
          }
        }}
        title={selected ? `${selected.code} – ${selected.name}` : "Side panel"}
      >
        <div className="card">
          {selected ? (
            <div className="summary">
              <span>Area</span>
              <b>{selected.area}</b>
              <span>Balance</span>
              <MoneyText value={selected.balance} />
              <span>Status</span>
              <StatusBadge status={selected.status} />
            </div>
          ) : (
            <p style={{ margin: 0 }}>Details open here, e.g. a customer with balances and history.</p>
          )}
        </div>
      </SidePanel>
    </>
  );
}
