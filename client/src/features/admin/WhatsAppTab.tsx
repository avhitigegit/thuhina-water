"use client";

/*
 * WhatsApp tab (M13 C1, C2 – design 9.2, FR-61): master switch, the eight customer messages (each with its own switch), the
 * daily reminder settings (days before / after the due date, time), Meta credentials (write-only: only "saved" is shown,
 * never the value), Verify connection, Test send, "send now" actions, the templates to create in Meta and the message log
 * with this month's counts (cost control). Without a phone number ID and access token the server runs in test mode:
 * messages are only written to the log.
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
  useWhatsAppTemplates,
  useWhatsAppTest,
  useWhatsAppVerify,
} from "./whatsappApi";

/** The customer messages: settings switch, log filter value, label and when it is sent. */
const MESSAGES = [
  {
    key: "welcome",
    type: "WELCOME",
    label: "Welcome",
    when: "when a customer is registered – code, delivery day",
  },
  {
    key: "quotation",
    type: "QUOTATION",
    label: "Quotation",
    when: 'with the quotation PDF, when the office presses "Send on WhatsApp"',
  },
  {
    key: "deliveryBill",
    type: "DELIVERY_BILL",
    label: "Delivery bill",
    when: "with the bill PDF, every bill saved (phone or office) – bottles, money, balance, next delivery",
  },
  {
    key: "billCancelled",
    type: "BILL_CANCELLED",
    label: "Bill cancelled",
    when: "when a bill that went on WhatsApp is reversed",
  },
  {
    key: "paymentReceipt",
    type: "PAYMENT_RECEIPT",
    label: "Payment receipt",
    when: "money paid apart from a bill – amount, receipt number, balance",
  },
  {
    key: "monthlyInvoice",
    type: "MONTHLY_INVOICE",
    label: "Monthly invoice",
    when: "with the invoice PDF, when invoices are generated",
  },
  {
    key: "dueSoon",
    type: "PAYMENT_DUE_SOON",
    label: "Payment due soon",
    when: "Credit / Monthly bill customers, the days set below before the due date",
  },
  {
    key: "overdue",
    type: "PAYMENT_OVERDUE",
    label: "Payment overdue",
    when: "the days set below after the due date if still unpaid (at most once a week)",
  },
] as const;
type MessageKey = (typeof MESSAGES)[number]["key"];

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

type Form = Record<MessageKey, boolean> & {
  enabled: boolean;
  daysBefore: number;
  daysAfter: number;
  reminderHour: number;
  phoneNumberId: string;
  businessAccountId: string;
  language: string;
};

function toForm(s: WhatsAppSettings): Form {
  return {
    enabled: s.enabled,
    welcome: s.welcome,
    quotation: s.quotation,
    deliveryBill: s.deliveryBill,
    billCancelled: s.billCancelled,
    paymentReceipt: s.paymentReceipt,
    monthlyInvoice: s.monthlyInvoice,
    dueSoon: s.dueSoon,
    overdue: s.overdue,
    daysBefore: s.daysBefore,
    daysAfter: s.daysAfter,
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
      <TemplatesCard />
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
          <span className="small muted">
            Messages from the business number – only to customers who agreed to WhatsApp messages and have a
            mobile number
          </span>
          {MESSAGES.map((m) => (
            <label key={m.key}>
              <input type="checkbox" checked={form[m.key]} onChange={(e) => set(m.key, e.target.checked)} />{" "}
              <b>{m.label}</b> – {m.when}
            </label>
          ))}
        </div>
        <div className="field">
          <label htmlFor="wa-before">Due soon: days before the due date</label>
          <input
            id="wa-before"
            type="number"
            min={1}
            max={30}
            value={form.daysBefore}
            onChange={(e) => set("daysBefore", Number(e.target.value))}
            className={errors.daysBefore ? "invalid" : undefined}
          />
          {err("daysBefore")}
        </div>
        <div className="field">
          <label htmlFor="wa-after">Overdue: days after the due date</label>
          <input
            id="wa-after"
            type="number"
            min={1}
            max={30}
            value={form.daysAfter}
            onChange={(e) => set("daysAfter", Number(e.target.value))}
            className={errors.daysAfter ? "invalid" : undefined}
          />
          {err("daysAfter")}
        </div>
        <div className="field">
          <label htmlFor="wa-hour">Reminder time (every day)</label>
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
        token above (subscribe to &quot;messages&quot;). The templates to create in Meta are listed below.
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
        themselves within a minute; payment due soon / overdue messages are checked every day at{" "}
        {hourText(settings.reminderHour)}.
      </p>
    </div>
  );
}

/** The Meta templates to create in WhatsApp Manager (category Utility); Meta must approve each one. */
function TemplatesCard() {
  const q = useWhatsAppTemplates();
  return (
    <div className="card">
      <h2>Templates for Meta</h2>
      <p className="small muted">
        Create each one in Meta WhatsApp Manager → Message templates: category <b>Utility</b>, the name and
        the text exactly as below, language as set above. A message is sent only after Meta approves its
        template.
      </p>
      {q.isError && <div className="banner bad">{q.error.message}</div>}
      {q.data?.map((t) => (
        <div key={t.name} className="wa-template" data-testid={`wa-template-${t.name}`}>
          <div>
            <b>{t.label}</b> – <code>{t.name}</code> · {t.category}
            {t.document && " · header: Document (PDF)"}{" "}
            {t.switchedOn ? <Badge kind="ok">On</Badge> : <Badge>Off</Badge>}
          </div>
          <pre className="small" style={{ whiteSpace: "pre-wrap", margin: "4px 0" }}>
            {t.body}
          </pre>
          <div className="small muted">{t.params.map((p, i) => `{{${i + 1}}} ${p}`).join(" · ")}</div>
        </div>
      ))}
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
            {MESSAGES.map((m) => (
              <option key={m.type} value={m.type}>
                {m.label}
              </option>
            ))}
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
