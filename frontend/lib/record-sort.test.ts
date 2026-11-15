import { describe, expect, it } from "vitest";
import { readSort, sortRecords, SORT_FIELDS } from "./record-sort";

describe("record ordering", () => {
  it("sorts the whole matching population with zeros, missing values and stable ties before paging", () => {
    const rows = Array.from({ length: 80 }, (_, id) => ({ id: String(id).padStart(3, "0"), amount: id === 79 ? 10000 : id === 78 ? null : Math.floor(id / 2), source: id % 2 ? "A" : "B" }));
    const matches = rows.filter(row => row.source === "A");
    for (const direction of ["asc", "desc"] as const) {
      const sorted = sortRecords(matches, { field: "amount", direction }, row => row.amount, row => row.id);
      const pages = [0, 1, 2, 3].flatMap(page => sorted.slice(page * 10, (page + 1) * 10));
      expect(pages).toHaveLength(40);
      expect(new Set(pages.map(row => row.id)).size).toBe(40);
      expect(pages[direction === "desc" ? 0 : 39].amount).toBe(10000);
    }
    const sorted = sortRecords(rows, { field: "amount", direction: "desc" }, row => row.amount, row => row.id);
    expect(sorted.at(-1)?.amount).toBeNull();
    expect(sorted.filter(row => row.amount === 0).map(row => row.id)).toEqual(["000", "001"]);
  });
  it("validates URL fields and directions", () => {
    expect(readSort(new URLSearchParams("sort=amount;drop&direction=bad"), SORT_FIELDS.invoices, "invoiceDate")).toEqual({ field: "invoiceDate", direction: "desc" });
  });
});

it("demo sales sort before paging with the same source filter and validated fields", async () => {
  const { demoApi } = await import("./api/demo");
  for (const direction of ["asc", "desc"] as const) {
    const pages = await Promise.all([0, 1, 2].map(page => demoApi.listDailySales({ source: "Cooking the Books" }, page, 1, { sort: { field: "net", direction } })));
    const values = pages.flatMap(page => page.items.map(row => Number(row.net)));
    expect(values).toHaveLength(3);
    expect(values).toEqual([...values].sort((a, b) => direction === "asc" ? a - b : b - a));
  }
  await expect(demoApi.listInvoices({}, 0, 1, { sort: { field: "total;drop", direction: "asc" } })).rejects.toMatchObject({ code: "VALIDATION_FAILED" });
});

it("accepts the explorer's absent sort and rejects unknown explicit columns", async () => {
  const { demoApi } = await import("./api/demo");
  const entities = await demoApi.listCanonicalEntities();
  const entity = entities[0].id;
  const defaultRows = await demoApi.listCanonicalRows(entity, 0, 10);
  expect(await demoApi.listCanonicalRows(entity, 0, 10, { sort: { field: "", direction: "asc" } })).toEqual(defaultRows);
  await expect(demoApi.listCanonicalRows(entity, 0, 10, { sort: { field: "unknown_column", direction: "asc" } })).rejects.toMatchObject({ code: "VALIDATION_FAILED" });
});
