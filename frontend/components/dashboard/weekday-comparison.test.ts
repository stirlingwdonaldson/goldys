import { describe, expect, it } from "vitest";
import { weekOnWeek } from "./business-kpi-grid";

describe("date-only same-weekday comparisons", () => {
  // Distinct adjacent-day revenue catches the Melbourne midnight-to-UTC regression.
  for (const [latest, prior, adjacent] of [
    ["2026-11-15", "2026-11-08", "2026-11-07"],
    ["2026-07-12", "2026-07-05", "2026-07-04"],
    ["2026-10-04", "2026-09-27", "2026-09-26"],
    ["2026-04-05", "2026-03-29", "2026-03-28"],
  ]) it(`compares ${latest} with ${prior}`, () => {
    const result = weekOnWeek({ date: latest, total: 150 }, [{ date: prior, total: 100 }, { date: adjacent, total: 500 }]);
    expect(result?.label).toMatch(/^50% vs/);
    expect(result?.direction).toBe("up");
  });
  it("keeps unavailable operands unavailable and genuine zero meaningful", () => {
    expect(weekOnWeek({ date: "2026-11-15", total: null }, [{ date: "2026-11-08", total: 100 }])).toBeUndefined();
    expect(weekOnWeek({ date: "2026-11-15", total: 100 }, [{ date: "2026-11-08", total: null }])).toBeUndefined();
    expect(weekOnWeek({ date: "2026-11-15", total: 0 }, [{ date: "2026-11-08", total: 100 }])?.label).toMatch(/^100% vs/);
    expect(weekOnWeek({ date: "2026-11-15", total: 100 }, [{ date: "2026-11-08", total: 0 }])).toBeUndefined();
  });
});
