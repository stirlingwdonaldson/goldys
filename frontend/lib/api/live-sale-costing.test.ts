// @vitest-environment node
import { expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server, setupApiServer } from "@/tests/api-server";
import { liveApi } from "./live";
import { demoApi } from "./demo";
setupApiServer();
it("sends the exact sale number and date scope and preserves null margin", async () => {
  server.use(http.get("http://localhost:3000/api/sale-items/costing", ({request}) => {
    const q = new URL(request.url).searchParams;
    expect(q.get("saleNumber")).toBe("SP-100 1021082623");
    expect(q.get("from")).toBe("2026-10-21"); expect(q.get("to")).toBe("2026-10-21");
    return HttpResponse.json({lines:[],complete:false,recipeCost:null,grossProfit:null,margin:null,qualification:"INCOMPLETE_COSTING"});
  }));
  expect((await liveApi.getSaleCosting("SP-100 1021082623","2026-10-21","2026-10-21")).margin).toBeNull();
});
it("demo costing preserves unmatched and dated recipe observations",async () => {
  const result = await demoApi.getSaleCosting("SALE-1002","2026-10-01","2026-10-31");
  expect(result.lines.map((l) => l.status)).toEqual(["STALE_COST","UNMATCHED"]);
  expect(result.margin).toBeNull(); expect(result.recipeCost).toBeNull();
  expect((await demoApi.getSaleCosting("missing","2026-10-01","2026-10-31")).lines).toEqual([]);
});
