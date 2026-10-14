import { describe, it, expect } from "vitest";
import { demoApi } from "./demo";

describe("demoApi relational query", () => {
  it("lists the three approved datasets", async () => {
    const datasets = await demoApi.listQueryDatasets();
    expect(datasets.map((d) => d.id)).toEqual([
      "resolved_daily_sales",
      "invoice",
      "invoice_line",
    ]);
  });

  it("returns typed fields for a dataset", async () => {
    const fields = await demoApi.listQueryFields("invoice");
    expect(fields.map((f) => f.name)).toContain("invoice_number");
    const amount = fields.find((f) => f.name === "total_amount");
    expect(amount?.type).toBe("DECIMAL");
    expect(amount?.unit).toBe("AUD");
  });

  it("returns the invoice->line relationship with fanout risk", async () => {
    const relationships = await demoApi.listQueryRelationships();
    expect(relationships).toHaveLength(1);
    expect(relationships[0].fanoutRisk).toBe(true);
    expect(relationships[0].cardinality).toBe("ONE_TO_MANY");
  });

  it("runs a plan and returns columns plus rows", async () => {
    const result = await demoApi.runQuery({
      version: 1,
      dataset: "invoice",
      select: [{ field: "invoice_number" }, { field: "total_amount" }],
      limit: 10,
      offset: 0,
    });
    expect(result.columns).toHaveLength(2);
    expect(result.rows.length).toBeGreaterThan(0);
    expect(result.semantics.readMode).toBe("CANONICAL_DIRECT");
  });

  it("round-trips a saved query through create, list and run", async () => {
    const created = await demoApi.createSavedQuery({
      title: "demo invoices",
      plan: {
        version: 1,
        dataset: "invoice",
        select: [{ field: "invoice_number" }],
        limit: 10,
        offset: 0,
      },
    });
    const listed = await demoApi.listSavedQueries();
    expect(listed.map((s) => s.title)).toContain("demo invoices");
    const run = await demoApi.runSavedQuery(created.id);
    expect(run.columns).toHaveLength(1);
  });
});
