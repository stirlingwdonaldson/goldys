import { describe, it, expect } from "vitest";
import {
  buildLineageGraph,
  lineageDomainForTab,
  type LineageCounts,
  type LineageDomain,
} from "./lineage-graph";

const counts = (overrides: Partial<LineageCounts> = {}): LineageCounts => ({
  raw: 100,
  canonical: 80,
  resolved: 75,
  ...overrides,
});

describe("buildLineageGraph", () => {
  it("lays the five stages out left-to-right with a single node per column", () => {
    const g = buildLineageGraph("payment", counts());
    expect(g.columns.map((c) => c.map((n) => n.id))).toEqual([
      ["source"],
      ["raw"],
      ["canonical"],
      ["resolved"],
      ["metric"],
    ]);
    expect(g.edges.map((e) => `${e.source}->${e.target}`)).toEqual([
      "source->raw",
      "raw->canonical",
      "canonical->resolved",
      "resolved->metric",
    ]);
  });

  it("stamps the metric node with the tab's drill id and info tone", () => {
    const domains: [LineageDomain, string][] = [
      ["payment", "payments"],
      ["deleted_sale", "deleted-orders"],
      ["sale_item", "sale-items"],
    ];
    for (const [domain, tabId] of domains) {
      const metric = buildLineageGraph(domain, counts()).columns[4][0];
      expect(metric.data.drill).toBe(tabId);
      expect(metric.data.tone).toBe("info");
    }
  });

  it("links each stage to the right explorer href", () => {
    const g = buildLineageGraph("sale_item", counts());
    expect(g.columns[0][0].data.href).toBe("/logs");
    expect(g.columns[1][0].data.href).toBe("/data?layer=raw");
    expect(g.columns[2][0].data.href).toBe("/data?layer=canonical&entity=sale_item");
    expect(g.columns[3][0].data.href).toBe(
      "/data?layer=resolved&entity=resolved_sale_item_day",
    );
    expect(g.columns[4][0].data.href).toBeUndefined();
  });

  it("uses the venue's webhook names, entity titles, and metric ids", () => {
    const payment = buildLineageGraph("payment", counts());
    expect(payment.columns[0][0].data.title).toBe("Lightspeed all-payments");
    expect(payment.columns[2][0].data.title).toBe("Payments");
    expect(payment.columns[3][0].data.title).toBe("Payment day");
    expect(payment.columns[4][0].data.title).toBe("payments.amount");

    const deleted = buildLineageGraph("deleted_sale", counts());
    expect(deleted.columns[0][0].data.title).toBe("Lightspeed all-deleted-orders");
    expect(deleted.columns[2][0].data.title).toBe("Deleted orders");
    expect(deleted.columns[3][0].data.title).toBe("Deleted-sale day");
    expect(deleted.columns[4][0].data.title).toBe("deleted_sales.amount");

    const item = buildLineageGraph("sale_item", counts());
    expect(item.columns[0][0].data.title).toBe("Lightspeed sales-details");
    expect(item.columns[2][0].data.title).toBe("Sale items");
    expect(item.columns[3][0].data.title).toBe("Sale-item day");
    expect(item.columns[4][0].data.title).toBe("sale_items.amount");
  });

  it("marks an empty raw ledger as missing and a populated one as neutral", () => {
    const empty = buildLineageGraph("payment", counts({ raw: 0 }));
    expect(empty.columns[1][0].data.tone).toBe("missing");
    expect(empty.columns[1][0].data.subtitle).toBe("0 records");

    const populated = buildLineageGraph("payment", counts({ raw: 100 }));
    expect(populated.columns[1][0].data.tone).toBe("neutral");
    expect(populated.columns[1][0].data.subtitle).toBe("100 records");
  });
});

describe("lineageDomainForTab", () => {
  it("maps tab ids to domains", () => {
    expect(lineageDomainForTab("payments")).toBe("payment");
    expect(lineageDomainForTab("deleted-orders")).toBe("deleted_sale");
    expect(lineageDomainForTab("sale-items")).toBe("sale_item");
  });
});
