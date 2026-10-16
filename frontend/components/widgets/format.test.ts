import { describe, expect, it } from "vitest";
import { formatValue } from "./format";

describe("catalogue percentage rendering", () => {
  it("renders the same ratio as the summary formatter, rounded to whole percentages", () => {
    expect(formatValue(0.141, "percent")).toBe("14%");
    expect(formatValue(0, "percent")).toBe("0%");
    expect(formatValue(null, "percent")).toBe("—");
  });
});
