"use client";

/*
 * Date field (NFR-07) – the prototype's calendar picker (assets/js/ui.js):
 *  - shows DD/MM/YYYY; the value given to / taken from the form is ISO (2026-10-05) or null
 *  - typing allowed, slashes are added automatically; a date that does not exist is marked invalid
 *  - calendar: Monday first, today outlined, selected day filled, ‹ › months, Today and Clear
 *  - opens below the field, or above it when there is no room; follows the field while the page or a
 *    pop-up scrolls; Escape / Tab / Enter close it
 */
import {
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
} from "react";
import { createPortal } from "react-dom";
import { autoSlashDate, dayOfWeek, daysInMonth, formatDate, MONTHS, parseDmy, todayIso } from "@/lib/format";
import { cn } from "@/lib/utils";

const POPUP_WIDTH = 250;
const POPUP_HEIGHT = 290;

export interface DateFieldProps {
  value: string | null | undefined;
  onChange: (iso: string | null) => void;
  id?: string;
  name?: string;
  disabled?: boolean;
  readOnly?: boolean;
  /** Mark the field red (e.g. a form error); invalid typed dates are marked automatically. */
  invalid?: boolean;
  onBlur?: () => void;
  className?: string;
  "aria-label"?: string;
  /** Business "today" (ISO) – for tests; defaults to today in Asia/Colombo. */
  today?: string;
}

const pad = (n: number) => (n < 10 ? "0" : "") + n;

function shiftMonth(ym: string, delta: number): string {
  let [y, m] = ym.split("-").map(Number);
  m += delta;
  if (m > 12) {
    m = 1;
    y += 1;
  } else if (m < 1) {
    m = 12;
    y -= 1;
  }
  return `${y}-${pad(m)}`;
}

export function DateField({
  value,
  onChange,
  id,
  name,
  disabled,
  readOnly,
  invalid,
  onBlur,
  className,
  today,
  ...aria
}: DateFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const inputRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState(formatDate(value));
  const [badText, setBadText] = useState(false);
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(() => (value || today || todayIso()).slice(0, 7));
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);

  // A new value from the form (reset, load) replaces the text, unless the text already means that date.
  const lastValue = useRef(value);
  useEffect(() => {
    if (lastValue.current === value) return;
    lastValue.current = value;
    if ((parseDmy(text) ?? null) !== (value ?? null)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setText(formatDate(value));
      setBadText(false);
    }
  }, [value, text]);

  const canOpen = !disabled && !readOnly;
  const todayValue = today ?? todayIso();

  function emit(iso: string | null) {
    lastValue.current = iso;
    onChange(iso);
  }

  function openPicker() {
    if (!canOpen || open) return;
    setMonth((parseDmy(text) ?? todayValue).slice(0, 7));
    setOpen(true);
  }

  function close() {
    setOpen(false);
  }

  function choose(iso: string | null) {
    setText(formatDate(iso));
    setBadText(false);
    emit(iso);
    close();
  }

  function onInput(e: ChangeEvent<HTMLInputElement>) {
    const native = e.nativeEvent as InputEvent;
    const next = native.inputType?.startsWith("delete") ? e.target.value : autoSlashDate(e.target.value);
    setText(next);
    const iso = parseDmy(next);
    if (iso) {
      setBadText(false);
      setMonth(iso.slice(0, 7));
      emit(iso);
    } else {
      emit(null);
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (open && (e.key === "Escape" || e.key === "Tab" || e.key === "Enter")) close();
    if (!open && e.key === "ArrowDown") openPicker();
  }

  function onInputBlur() {
    setBadText(text.trim() !== "" && parseDmy(text) === null);
    close();
    onBlur?.();
  }

  // Keep the calendar next to its field (also while the page or a pop-up scrolls).
  useLayoutEffect(() => {
    if (!open) return;
    function place() {
      const el = inputRef.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      if (r.bottom < 0 || r.top > window.innerHeight) {
        setOpen(false);
        return;
      }
      let top = r.bottom + 4;
      let left = r.left;
      if (top + POPUP_HEIGHT > window.innerHeight) top = Math.max(4, r.top - POPUP_HEIGHT - 4);
      if (left + POPUP_WIDTH > window.innerWidth) left = Math.max(4, window.innerWidth - POPUP_WIDTH - 4);
      setPos({ top, left });
    }
    place();
    const onResize = () => setOpen(false);
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", onResize);
    };
  }, [open]);

  const selected = parseDmy(text);

  return (
    <>
      <input
        ref={inputRef}
        id={inputId}
        name={name}
        type="text"
        role="combobox"
        className={cn("date", (invalid || badText) && "invalid", className)}
        value={text}
        placeholder="DD/MM/YYYY"
        maxLength={10}
        inputMode="numeric"
        autoComplete="off"
        disabled={disabled}
        readOnly={readOnly}
        aria-invalid={invalid || badText || undefined}
        aria-haspopup="dialog"
        aria-controls={inputId + "-calendar"}
        aria-expanded={open}
        onChange={onInput}
        onFocus={openPicker}
        onClick={openPicker}
        onKeyDown={onKeyDown}
        onBlur={onInputBlur}
        {...aria}
      />
      {open &&
        typeof document !== "undefined" &&
        createPortal(
          <Calendar
            id={inputId + "-calendar"}
            month={month}
            selected={selected}
            today={todayValue}
            style={pos ? { top: pos.top, left: pos.left } : { visibility: "hidden" }}
            onMonth={(d) => setMonth((m) => shiftMonth(m, d))}
            onPick={choose}
          />,
          document.body,
        )}
    </>
  );
}

function Calendar({
  id,
  month,
  selected,
  today,
  style,
  onMonth,
  onPick,
}: {
  id: string;
  month: string;
  selected: string | null;
  today: string;
  style: React.CSSProperties;
  onMonth: (delta: number) => void;
  onPick: (iso: string | null) => void;
}) {
  const [y, m] = month.split("-").map(Number);
  const startBlank = (dayOfWeek(`${month}-01`) + 6) % 7; // Monday first
  const days = daysInMonth(y, m);
  return (
    <div
      id={id}
      className="dp"
      role="dialog"
      aria-label="Choose a date"
      style={style}
      // Keep the focus in the field while clicking the calendar.
      onMouseDown={(e) => e.preventDefault()}
    >
      <div className="dp-head">
        <button type="button" aria-label="Previous month" onClick={() => onMonth(-1)}>
          ‹
        </button>
        <b>
          {MONTHS[m - 1]} {y}
        </b>
        <button type="button" aria-label="Next month" onClick={() => onMonth(1)}>
          ›
        </button>
      </div>
      <div className="dp-grid">
        {["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"].map((d) => (
          <span key={d} className="dp-dow">
            {d}
          </span>
        ))}
        {Array.from({ length: startBlank }, (_, i) => (
          <span key={"b" + i} />
        ))}
        {Array.from({ length: days }, (_, i) => {
          const iso = `${month}-${pad(i + 1)}`;
          return (
            <button
              key={iso}
              type="button"
              data-day={iso}
              aria-label={formatDate(iso)}
              aria-pressed={iso === selected}
              className={cn(iso === selected && "sel", iso === today && "today")}
              onClick={() => onPick(iso)}
            >
              {i + 1}
            </button>
          );
        })}
      </div>
      <div className="dp-foot">
        <button type="button" onClick={() => onPick(today)}>
          Today
        </button>
        <button type="button" onClick={() => onPick(null)}>
          Clear
        </button>
      </div>
    </div>
  );
}
