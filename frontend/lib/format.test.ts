import { describe, it, expect } from "vitest";
import {
  formatCurrency,
  formatDay,
  formatHours,
  formatNumber,
  formatPercent,
  formatRecentTime,
  formatShortDay,
  humanizeKey,
  toNumber,
} from "./format";

describe("toNumber", () => {
  it("accepts numbers and numeric strings", () => {
    expect(toNumber(12)).toBe(12);
    expect(toNumber("12.5")).toBe(12.5);
  });
  it("is null for missing or non-numeric input, never 0", () => {
    expect(toNumber(null)).toBeNull();
    expect(toNumber(undefined)).toBeNull();
    expect(toNumber("")).toBeNull();
    expect(toNumber("abc")).toBeNull();
  });
});

describe("number formatters", () => {
  it("formats en-AU currency, with an em dash for missing values", () => {
    expect(formatCurrency(10865.72)).toBe("$10,865.72");
    expect(formatCurrency("212")).toBe("$212.00");
    expect(formatCurrency(10865.72, { cents: false })).toBe("$10,866");
    expect(formatCurrency(null)).toBe("—");
  });
  it("formats counts, ratios and hours", () => {
    expect(formatNumber(1284)).toBe("1,284");
    expect(formatNumber(2.456, 2)).toBe("2.46");
    expect(formatPercent(0.141)).toBe("14.1%");
    expect(formatHours(1231.5)).toBe("1,231.5 h");
    expect(formatPercent(null)).toBe("—");
  });
});

describe("date formatters", () => {
  it("formats ISO dates as local calendar days and passes other text through", () => {
    expect(formatDay("2026-10-04")).toBe("Sun, 4 Oct");
    expect(formatShortDay("2026-10-04")).toBe("4 Oct");
    expect(formatDay("sale-4821")).toBe("sale-4821");
  });
  it("shows only the time for today, and the date otherwise", () => {
    const now = new Date(2026, 9, 9, 12, 0);
    expect(formatRecentTime(new Date(2026, 9, 9, 6, 15).toISOString(), now)).toMatch(/^6:15\sam$/);
    expect(formatRecentTime(new Date(2026, 8, 20, 19, 5).toISOString(), now)).toMatch(/^20 Sept?, 7:05\spm$/);
  });
});

describe("humanizeKey", () => {
  it("turns API keys into sentence-case labels", () => {
    expect(humanizeKey("gross_sales")).toBe("Gross sales");
    expect(humanizeKey("quantity-sold")).toBe("Quantity sold");
  });
});
