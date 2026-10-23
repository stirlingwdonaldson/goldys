import { describe, it, expect } from "vitest";
import { explorerPage, explorerUrl } from "./url-state";

describe("explorer URLs", () => {
  it("round-trips the applied scope and resets pagination in one update", () => {
    const url = explorerUrl(new URLSearchParams("layer=raw&page=8&size=25&fetcher=old"), { source: "CTB", fetcher: null, page: "0" });
    expect(new URLSearchParams(url).get("source")).toBe("CTB");
    expect(new URLSearchParams(url).has("fetcher")).toBe(false);
    expect(explorerPage(new URLSearchParams(url))).toEqual({ page: 0, pageSize: 25 });
  });
  it("bounds malformed and excessive pagination", () => {
    for (const page of ["-1", "NaN", "1e10", "99999999999999999"]) expect(explorerPage(new URLSearchParams({ page, size: "99999" }))).toEqual({ page: 0, pageSize: 10 });
  });
});
