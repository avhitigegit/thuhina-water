import { describe, expect, it } from "vitest";
import {
  autoSlashDate,
  formatDate,
  formatDateTime,
  formatDayDate,
  formatMoney,
  parseDmy,
  todayIso,
} from "./format";

describe("dates", () => {
  it("formats ISO as DD/MM/YYYY", () => {
    expect(formatDate("2026-10-05")).toBe("05/10/2026");
    expect(formatDate("2026-10-05T14:30:00Z")).toBe("05/10/2026");
    expect(formatDate(null)).toBe("");
  });

  it("parses DD/MM/YYYY and rejects dates that do not exist", () => {
    expect(parseDmy("05/10/2026")).toBe("2026-10-05");
    expect(parseDmy("5/1/2026")).toBe("2026-01-05");
    expect(parseDmy("29/02/2028")).toBe("2028-02-29");
    expect(parseDmy("29/02/2026")).toBeNull();
    expect(parseDmy("31/04/2026")).toBeNull();
    expect(parseDmy("12/13/2026")).toBeNull();
    expect(parseDmy("2026-10-05")).toBeNull();
    expect(parseDmy("")).toBeNull();
  });

  it("adds slashes while typing", () => {
    expect(autoSlashDate("05")).toBe("05");
    expect(autoSlashDate("051")).toBe("05/1");
    expect(autoSlashDate("05102026")).toBe("05/10/2026");
    expect(autoSlashDate("05/10/20261")).toBe("05/10/2026");
  });

  it("uses the Sri Lanka date for today", () => {
    // 20:00 UTC on 5 Oct is 01:30 on 6 Oct in Colombo.
    expect(todayIso(new Date("2026-10-05T20:00:00Z"))).toBe("2026-10-06");
    expect(todayIso(new Date("2026-10-05T10:00:00Z"))).toBe("2026-10-05");
  });

  it("shows the day name and time", () => {
    expect(formatDayDate("2026-10-09")).toBe("Fri 09/10/2026");
    expect(formatDateTime("2026-10-05T09:02:00Z")).toBe("05/10/2026 14:32");
  });
});

describe("money", () => {
  it("formats as Rs. with two decimals", () => {
    expect(formatMoney(1350)).toBe("Rs. 1,350.00");
    expect(formatMoney("3050.5")).toBe("Rs. 3,050.50");
    expect(formatMoney(0)).toBe("Rs. 0.00");
    expect(formatMoney(null)).toBe("Rs. 0.00");
    expect(formatMoney(-750)).toBe("-Rs. 750.00");
    expect(formatMoney(1234567.891)).toBe("Rs. 1,234,567.89");
  });
});
