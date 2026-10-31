"use client";

/*
 * WhatsApp tab (M13 C1, C2 – design 9.2): master switch, message types, weekly reminder time, Meta credentials (write-only:
 * only "saved" is shown, never the value), Verify connection, Test send, "send now" actions and the message log with this
 * month's counts (cost control). Without a phone number ID and access token the server runs in test mode: messages are only
 * written to the log.
 */
import { useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { Badge, StatusBadge, type BadgeKind } from "@/components/shared/StatusBadge";
import { ApiError } from "@/lib/api/client";
import type {
  WhatsAppCheckResult,
  WhatsAppMessageRow,
  WhatsAppSettings,
  WhatsAppSettingsRequest,
} from "@/lib/api/types";
import { formatDateTime, todayIso } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { toast } from "@/lib/toast";
import {
  useRunReminders,
  useSaveWhatsAppSettings,
  useSendQueuedNow,
  useWhatsAppMessages,
  useWhatsAppSettings,
  useWhatsAppTest,
  useWhatsAppVerify,
} from "./whatsappApi";

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const HOURS = Array.from({ length: 24 }, (_, h) => h);
const SECRETS = [
  { key: "accessToken", saved: "accessTokenSaved", label: "Access token (permanent, system user)" },
  { key: "appSecret", saved: "appSecretSaved", label: "App secret (checks the webhook)" },
  { key: "verifyToken", saved: "verifyTokenSaved", label: "Webhook verify token" },
] as const;
type SecretKey = (typeof SECRETS)[number]["key"];

const STATUS_KIND: Record<WhatsAppMessageRow["status"], BadgeKind> = {
  QUEUED: "info",
  SENT: "info",
  DELIVERED: "ok",
  READ: "ok",
  FAILED: "bad",
  SKIPPED: "warn",
};

interface Form {
  enabled: boolean;
  leftAtDoor: boolean;
  paymentReminder: boolean;
  monthlyInvoice: boolean;
  reminderDay: number;
  reminderHour: number;
  phoneNumberId: string;
  businessAccountId: string;
  language: string;
}

function toForm(s: WhatsAppSettings): Form {
  return {
    enabled: s.enabled,
    leftAtDoor: s.leftAtDoor,
    paymentReminder: s.paymentReminder,
    monthlyInvoice: s.monthlyInvoice,
    reminderDay: s.reminderDay,
    reminderHour: s.reminderHour,
    phoneNumberId: s.phoneNumberId ?? "",
    businessAccountId: s.businessAccountId ?? "",
    language: s.language,
  };
}

const hourText = (h: number) => String(h).padStart(2, "0") + ":00";

export function WhatsAppTab() {
  const settings = useWhatsAppSettings();
  return (
    <>
      {settings.isError && <div className="banner bad">{settings.error.message}</div>}
      {!settings.data && !settings.isError && <div className="card muted">Loading…</div>}
      {/* Remounts with fresh fields whenever the saved settings change. */}
      {settings.data && <SettingsCard key={JSON.stringify(settings.data)} settings={settings.data} />}
      {settings.data && <ActionsCard settings={settings.data} />}
      <MessageLog />
    </>
  );
}

function SettingsCard({ settings }: { settings: WhatsAppSettings }) {
  const save = useSaveWhatsAppSettings();
  const [form, setForm] = useState<Form>(() => toForm(settings));
  const [secrets, setSecrets] = useState<Record<SecretKey, string>>({
    accessToken: "",
    appSecret: "",
    verifyToken: "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  const initial = toForm(settings);
  const changes: WhatsAppSettingsRequest = {};
  (Object.keys(form) as (keyof Form)[]).forEach((k) => {
    if (form[k] !== initial[k]) (changes as Record<string, unknown>)[k] = form[k];
  });
  SECRETS.forEach(({ key }) => {
    if (secrets[key].trim()) changes[key] = secrets[key].trim();
  });
  const dirty = Object.keys(changes).length > 0;

  function send(body: WhatsAppSettingsRequest, done: string) {
    setErrors({});
    save.mutate(body, {
      onSuccess: () => {
        setSecrets({ accessToken: "", appSecret: "", verifyToken: "" });
        toast(done, "ok");
      },
      onError: (err) => {
        const fields = err instanceof ApiError ? err.fieldErrors : {};
        setErrors(fields);
        if (Object.keys(fields).length === 0) toast(errorText(err), "bad");
      },
    });
  }

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }));
  const err = (k: string) => errors[k] && <span className="error">{errors[k]}</span>;

  return (
    <div className="card">
      <h2>WhatsApp Business</h2>
      <div className={settings.mode === "LIVE" ? "banner ok" : "banner info"} data-testid="wa-mode">
        <b>{settings.mode === "LIVE" ? "Live" : "Test mode"}.</b>{" "}
        {settings.modeText.replace(/^(Live|Test mode) – /, "")}
      </div>
      {!settings.encryptionReady && (
        <div className="banner warn">
          The server has no encryption key (APP_ENCRYPTION_KEY), so the access token and secrets cannot be
          saved.
        </div>
      )}

      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field full">
          <label>
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => set("enabled", e.target.checked)}
            />{" "}
            <b>Send WhatsApp messages</b> (master switch)
          </label>
        </div>
        <div className="field full">
          <span className="small muted">Messages (only to customers who agreed to WhatsApp messages)</span>
          <label>
            <input
              type="checkbox"
              checked={form.leftAtDoor}
              onChange={(e) => set("leftAtDoor", e.target.checked)}
            />{" "}
            Left at door – when a bill is saved as &quot;Left at door&quot;
          </label>
          <label>
            <input
              type="checkbox"
              checked={form.monthlyInvoice}
              onChange={(e) => set("monthlyInvoice", e.target.checked)}
            />{" "}
            Monthly invoice – with the invoice PDF, when invoices are generated
          </label>
          <label>
            <input
              type="checkbox"
              checked={form.paymentReminder}
              onChange={(e) => set("paymentReminder", e.target.checked)}
            />{" "}
            Payment reminder – weekly, to Credit / Monthly bill customers past their terms (at most once a
            week)
          </label>
        </div>
        <div className="field">
          <label htmlFor="wa-day">Reminder day</label>
          <select
            id="wa-day"
            value={form.reminderDay}
            onChange={(e) => set("reminderDay", Number(e.target.value))}
          >
            {DAYS.map((d, i) => (
              <option key={d} value={i + 1}>
                {d}
              </option>
            ))}
          </select>
          {err("reminderDay")}
        </div>
        <div className="field">
          <label htmlFor="wa-hour">Reminder time</label>
          <select
            id="wa-hour"
            value={form.reminderHour}
            onChange={(e) => set("reminderHour", Number(e.target.value))}
          >
            {HOURS.map((h) => (
              <option key={h} value={h}>
                {hourText(h)}
              </option>
            ))}
          </select>
          {err("reminderHour")}
        </div>
      </div>

      <div className="section-title">Meta connection (from WhatsApp Manager)</div>
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field">
          <label htmlFor="wa-phone-id">Phone number ID</label>
          <input
            id="wa-phone-id"
            value={form.phoneNumberId}
            onChange={(e) => set("phoneNumberId", e.target.value)}
            className={errors.phoneNumberId ? "invalid" : undefined}
          />
          {err("phoneNumberId")}
        </div>
        <div className="field">
          <label htmlFor="wa-waba-id">WhatsApp Business account ID</label>
          <input
            id="wa-waba-id"
            value={form.businessAccountId}
            onChange={(e) => set("businessAccountId", e.target.value)}
            className={errors.businessAccountId ? "invalid" : undefined}
          />
          {err("businessAccountId")}
        </div>
        {SECRETS.map(({ key, saved, label }) => (
          <div className="field" key={key}>
            <label htmlFor={`wa-${key}`}>
              {label} {settings[saved] ? <Badge kind="ok">Saved</Badge> : <Badge>Not saved</Badge>}
            </label>
            <input
              id={`wa-${key}`}
              type="password"
              autoComplete="new-password"
              value={secrets[key]}
              placeholder={settings[saved] ? "Saved – type a new value to replace it" : ""}
              onChange={(e) => setSecrets((s) => ({ ...s, [key]: e.target.value }))}
              className={errors[key] ? "invalid" : undefined}
            />
            {err(key)}
            {settings[saved] && (
              <button
                className="btn small"
                type="button"
                disabled={save.isPending}
                onClick={() => send({ [key]: "" }, "Removed")}
              >
                Remove
              </button>
            )}
          </div>
        ))}
        <div className="field">
          <label htmlFor="wa-language">Template language</label>
          <input id="wa-language" value={form.language} onChange={(e) => set("language", e.target.value)} />
          {err("language")}
        </div>
      </div>
      <p className="small muted">
        Webhook address for Meta: <code>https://&lt;this site&gt;{settings.webhookPath}</code> with the verify
        token above (subscribe to &quot;messages&quot;). Templates to approve in Meta:{" "}
        <code>left_at_door_v1</code>, <code>payment_reminder_v1</code>, <code>monthly_invoice_v1</code>{" "}
        (document header).
      </p>
      <div className="form-actions">
        <button
          className="btn"
          type="button"
          disabled={!dirty || save.isPending}
          onClick={() => {
            setForm(toForm(settings));
            setSecrets({ accessToken: "", appSecret: "", verifyToken: "" });
            setErrors({});
          }}
        >
          Undo changes
        </button>
        <button
          className="btn primary"
          type="button"
          disabled={!dirty || save.isPending}
          onClick={() => send(changes, "WhatsApp settings saved")}
        >
          {save.isPending ? "Saving…" : "Save"}
        </button>
      </div>
    </div>
  );
}

function ActionsCard({ settings }: { settings: WhatsAppSettings }) {
  const verify = useWhatsAppVerify();
  const test = useWhatsAppTest();
  const reminders = useRunReminders();
  const sendNow = useSendQueuedNow();
  const [to, setTo] = useState("");
  const [result, setResult] = useState<WhatsAppCheckResult | null>(null);

  const show = (r: WhatsAppCheckResult) => setResult(r);
  const fail = (e: unknown) => setResult({ ok: false, mode: settings.mode, message: errorText(e) });

  return (
    <div className="card">
      <h2>Check &amp; send</h2>
      {result && (
        <div className={result.ok ? "banner ok" : "banner bad"} data-testid="wa-result">
          {result.message}
        </div>
      )}
      <div className="toolbar">
        <button
          className="btn"
          type="button"
          disabled={verify.isPending}
          onClick={() => verify.mutate(undefined, { onSuccess: show, onError: fail })}
        >
          {verify.isPending ? "Checking…" : "Verify connection"}
        </button>
        <div className="field">
          <label htmlFor="wa-test-to">Test number</label>
          <input
            id="wa-test-to"
            value={to}
            placeholder="077 123 4567"
            onChange={(e) => setTo(e.target.value)}
          />
        </div>
        <button
          className="btn"
          type="button"
          disabled={test.isPending || !to.trim()}
          onClick={() => test.mutate(to, { onSuccess: show, onError: fail })}
        >
          {test.isPending ? "Sending…" : "Test send"}
        </button>
        <button
          className="btn"
          type="button"
          disabled={reminders.isPending}
          onClick={() =>
            reminders.mutate(undefined, {
              onSuccess: (r) => show({ ok: true, mode: settings.mode, message: r.message }),
              onError: fail,
            })
          }
        >
          Send reminders now
        </button>
        <button
          className="btn"
          type="button"
          disabled={sendNow.isPending}
          onClick={() =>
            sendNow.mutate(undefined, {
              onSuccess: (r) => show({ ok: true, mode: settings.mode, message: r.message }),
              onError: fail,
            })
          }
        >
          Send queued now
        </button>
      </div>
      <p className="small muted">
        Test send uses Meta&apos;s ready-made &quot;hello_world&quot; message. Queued messages also go out by
        themselves within a minute; reminders run every {DAYS[settings.reminderDay - 1]} at{" "}
        {hourText(settings.reminderHour)}.
      </p>
    </div>
  );
}

function MessageLog() {
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [from, setFrom] = useState<string | null>(null);
  const [to, setTo] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const q = useWhatsAppMessages({ type, status, from, to, page, size: 50 });
  const m = q.data?.thisMonth;

  const columns: Column<WhatsAppMessageRow>[] = [
    { key: "time", header: "Time", cell: (r) => formatDateTime(r.createdAt) },
    {
      key: "customer",
      header: "Customer",
      cell: (r) =>
        r.customerCode ? `${r.customerCode} – ${r.customerName}` : <span className="muted">–</span>,
    },
    { key: "type", header: "Message", cell: (r) => r.typeLabel },
    { key: "to", header: "Number", cell: (r) => r.toNumber },
    {
      key: "status",
      header: "Status",
      cell: (r) => (
        <>
          <StatusBadge status={r.statusLabel} kind={STATUS_KIND[r.status]} />
          {r.testMode && (
            <>
              {" "}
              <Badge>test</Badge>
            </>
          )}
        </>
      ),
    },
    {
      key: "details",
      header: "Details",
      cell: (r) =>
        r.error ? (
          <span className="error">{r.error}</span>
        ) : (
          <span className="small">{r.params.join(" · ")}</span>
        ),
    },
  ];

  return (
    <div className="card">
      <h2>Message log</h2>
      {m && (
        <p className="small" data-testid="wa-month">
          This month: <b>{m.sent}</b> sent (charged by Meta) · <b>{m.failed}</b> failed · <b>{m.skipped}</b>{" "}
          skipped · <b>{m.test}</b> in test mode (free)
        </p>
      )}
      <div className="toolbar">
        <div className="field">
          <label htmlFor="wa-f-type">Message</label>
          <select
            id="wa-f-type"
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All</option>
            <option value="LEFT_AT_DOOR">Left at door</option>
            <option value="MONTHLY_INVOICE">Monthly invoice</option>
            <option value="PAYMENT_REMINDER">Payment reminder</option>
            <option value="TEST">Test message</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="wa-f-status">Status</label>
          <select
            id="wa-f-status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All</option>
            {Object.keys(STATUS_KIND).map((s) => (
              <option key={s} value={s}>
                {s.charAt(0) + s.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="wa-f-from">From</label>
          <DateField id="wa-f-from" value={from} onChange={setFrom} today={todayIso()} />
        </div>
        <div className="field">
          <label htmlFor="wa-f-to">To</label>
          <DateField id="wa-f-to" value={to} onChange={setTo} today={todayIso()} />
        </div>
      </div>
      {q.isError && <div className="banner bad">{q.error.message}</div>}
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isPending}
        state={{ page, size: 50, sort: null, setPage, setSort: () => {}, params: { page, size: 50 } }}
        rowKey={(r) => r.id}
        empty="No WhatsApp messages yet."
      />
    </div>
  );
}
