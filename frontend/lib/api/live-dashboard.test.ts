// @vitest-environment node
import { expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { liveApi } from "./live";

setupApiServer();
it("preserves ranking qualifications even without rows and sends the requested scope", async () => {
  const result = { items: [], notices: ["Incomplete subtotal", "Ranked by known sales; full-period ranking unverified."],
    coverage: {from: "2026-10-01", to: "2026-10-02", observedDates: [], unresolvedDates: [], absentDates: ["2026-10-01", "2026-10-02"], expectedTradingPopulationKnown: false} };
  server.use(http.get("http://localhost:3000/api/dashboard/top-sellers", ({request}) => {
    const params = new URL(request.url).searchParams;
    expect(Object.fromEntries(params)).toEqual({from: "2026-10-01", to: "2026-10-02", limit: "3"});
    return HttpResponse.json(result);
  }));
  expect(await liveApi.getTopSellers("2026-10-01", "2026-10-02", 3)).toEqual(result);
});

it("preserves sales trend provenance and zero versus unknown values", async () => {
  const result = { points: [{date: "2026-10-01", total: 0}, {date: "2026-10-02", total: null}], notices: ["Incomplete subtotal"],
    provenance: {metric: "sales.net", range: {from: "2026-10-01", to: "2026-10-02", calendar: "CALENDAR"}} };
  server.use(http.get("http://localhost:3000/api/dashboard/sales-trend", ({request}) => {
    expect(new URL(request.url).searchParams.get("from")).toBe("2026-10-01");
    expect(new URL(request.url).searchParams.get("to")).toBe("2026-10-02");
    return HttpResponse.json(result);
  }));
  expect(await liveApi.getSalesTrend("2026-10-01", "2026-10-02")).toEqual(result);
});
