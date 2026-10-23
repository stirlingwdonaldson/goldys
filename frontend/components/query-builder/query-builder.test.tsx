import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import type {
  QueryDataset,
  QueryField,
  QueryPlan,
  QueryResult,
} from "@/lib/api/types";
import { ApiError } from "@/lib/api";
import { QueryBuilder, defaultQuery } from "./query-builder";
import { simplePredicates } from "./query-controls";

vi.mock("@/lib/demo-mode", () => ({ useApi: () => api }));
const fields: QueryField[] = [
  {
    name: "note",
    column: "note",
    type: "TEXT",
    nullable: true,
    kind: "DIMENSION",
    unit: null,
    permission: null,
  },
  {
    name: "trading_date",
    column: "trading_date",
    type: "DATE",
    nullable: false,
    kind: "DIMENSION",
    unit: null,
    permission: null,
  },
  {
    name: "amount",
    column: "amount",
    type: "DECIMAL",
    nullable: false,
    kind: "MEASURE",
    unit: "AUD",
    permission: null,
  },
];
const dataset: QueryDataset = {
  id: "new_dataset",
  table: "new_dataset",
  readMode: "RESOLVED",
  grain: "one trading day",
  permission: "sales",
  currentFact: false,
  fields,
};
const result: QueryResult = {
  columns: [{ name: "amount", type: "DECIMAL", unit: "AUD" }],
  rows: [[125]],
  pagination: { limit: 100, offset: 0, truncated: false },
  semantics: {
    currentFact: false,
    grain: "one trading day",
    readMode: "RESOLVED",
    datasets: [dataset.id],
    asOf: "2026-10-23T00:00:00Z",
  },
  notices: ["Example coverage notice"],
};
const api = {
  listQueryDatasets: vi.fn(),
  listQueryRelationships: vi.fn(),
  listSavedQueries: vi.fn(),
  listQueryFields: vi.fn(),
  runQuery: vi.fn(),
  createSavedQuery: vi.fn(),
  getSavedQuery: vi.fn(),
};

beforeEach(() => {
  vi.resetAllMocks();
  api.listQueryDatasets.mockResolvedValue([dataset]);
  api.listQueryRelationships.mockResolvedValue([]);
  api.listSavedQueries.mockResolvedValue([]);
  api.listQueryFields.mockResolvedValue(fields);
  api.runQuery.mockResolvedValue(result);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});
async function ready() {
  render(<QueryBuilder />);
  await screen.findByRole("button", { name: "Run query" });
}
async function choose(label: string, option: string) {
  fireEvent.click(screen.getByRole("combobox", { name: label }));
  fireEvent.click(await screen.findByRole("option", { name: option }));
}

describe("QueryBuilder", () => {
  it("starts a new dataset using authorised required fields before nullable fields, without aggregation", async () => {
    api.listQueryDatasets.mockResolvedValue([
      { ...dataset, fields: [...fields, { ...fields[0], name: "restricted" }] },
    ]);
    await ready();
    expect(screen.getByRole("combobox", { name: "Dataset" })).toHaveTextContent(
      "New dataset",
    );
    expect(
      screen.queryByLabelText("Select restricted"),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Run query" }));
    await screen.findByText("Result");
    const plan = api.runQuery.mock.calls[0][0] as QueryPlan;
    expect(plan.select.map((item) => item.field)).toEqual([
      "trading_date",
      "amount",
      "note",
    ]);
    expect(plan.select.every((item) => item.aggregate === null)).toBe(true);
    expect(screen.getByText("Example coverage notice")).toBeInTheDocument();
    expect(screen.getByText("$125.00")).toBeInTheDocument();
    expect(
      screen.queryByRole("tab", { name: "Chart" }),
    ).not.toBeInTheDocument();
  });
  it("limits dynamic defaults and handles empty and entirely nullable field sets", () => {
    expect(defaultQuery("future", []).select).toEqual([]);
    expect(
      defaultQuery(
        "future",
        fields.map((field) => ({ ...field, nullable: true })),
      ).select,
    ).toHaveLength(3);
    const many = Array.from({ length: 7 }, (_, i) => ({
      ...fields[0],
      name: `field_${i}`,
    }));
    expect(
      defaultQuery("future", many).select.map((item) => item.field),
    ).toEqual(["field_0", "field_1", "field_2", "field_3"]);
  });
  it("explains sorting and requires grouping before mixing original values with calculations", async () => {
    await ready();
    expect(
      screen.getByText(/Sort selected columns before applying the row limit/),
    ).toBeInTheDocument();
    await choose("Calculation for Amount", "Sum — add values");
    expect(screen.getByRole("button", { name: "Run query" })).toBeDisabled();
    fireEvent.click(screen.getByLabelText("Group by trading_date"));
    fireEvent.click(screen.getByLabelText("Group by note"));
    expect(screen.getByRole("button", { name: "Run query" })).toBeEnabled();
  });
  it("builds inclusive date ranges and refuses incomplete filters", async () => {
    await ready();
    await choose("Filter field", "Trading date");
    await choose("Condition", "Between (inclusive)");
    expect(screen.getByRole("button", { name: "Add filter" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Filter value"), {
      target: { value: "2026-10-01" },
    });
    fireEvent.change(screen.getByLabelText("Filter value 2"), {
      target: { value: "2026-10-23" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add filter" }));
    fireEvent.click(screen.getByRole("button", { name: "Run query" }));
    await waitFor(() => expect(api.runQuery).toHaveBeenCalled());
    expect(
      api.runQuery.mock.calls[0][0].filter.children[0].predicate.value,
    ).toEqual(["2026-10-01", "2026-10-23"]);
  });
  it("refreshes the submitted plan while edits remain a draft and stops after toggling or unmounting", async () => {
    await ready();
    fireEvent.click(screen.getByRole("button", { name: "Run query" }));
    await screen.findByText("Result");
    const submitted = api.runQuery.mock.calls[0][0];
    fireEvent.change(screen.getByLabelText("Row limit"), {
      target: { value: "20" },
    });
    expect(screen.getByText(/You have unapplied changes/)).toBeInTheDocument();
    vi.useFakeTimers();
    fireEvent.click(
      screen.getByRole("switch", { name: "Auto-refresh every 30 seconds" }),
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(api.runQuery).toHaveBeenCalledTimes(2);
    expect(api.runQuery.mock.calls[1][0]).toEqual(submitted);
    fireEvent.click(
      screen.getByRole("switch", { name: "Auto-refresh every 30 seconds" }),
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(api.runQuery).toHaveBeenCalledTimes(2);
    cleanup();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(api.runQuery).toHaveBeenCalledTimes(2);
  });
  it("keeps the last result when a refresh fails", async () => {
    await ready();
    fireEvent.click(screen.getByRole("button", { name: "Run query" }));
    await screen.findByText("$125.00");
    api.runQuery.mockRejectedValueOnce(
      new ApiError("UNEXPECTED_STATUS", "Unavailable"),
    );
    fireEvent.click(screen.getByRole("button", { name: "Run query" }));
    await screen.findByText(
      /Refresh failed; showing the last successful result/,
    );
    expect(screen.getByText("$125.00")).toBeInTheDocument();
  });
  it("shows save failures and refreshes saved-query choices after success", async () => {
    await ready();
    api.createSavedQuery.mockRejectedValueOnce(
      new ApiError("UNEXPECTED_STATUS", "Save unavailable"),
    );
    fireEvent.click(screen.getByRole("button", { name: "Save query" }));
    await screen.findByText("Could not save query: Save unavailable");
    api.createSavedQuery.mockResolvedValueOnce({
      id: "saved",
      plan: defaultQuery(dataset.id, fields),
    });
    fireEvent.click(screen.getByRole("button", { name: "Save query" }));
    await waitFor(() => expect(api.listSavedQueries).toHaveBeenCalledTimes(2));
  });
  it("preserves advanced saved filters when running", async () => {
    const filter = {
      logic: "OR" as const,
      children: [
        { predicate: { field: "note", op: "EQ" as const, value: "A" } },
        { predicate: { field: "note", op: "EQ" as const, value: "B" } },
      ],
    };
    api.listSavedQueries.mockResolvedValue([
      { id: "saved", title: "Advanced query" },
    ]);
    api.getSavedQuery.mockResolvedValue({
      id: "saved",
      title: "Advanced query",
      plan: { ...defaultQuery(dataset.id, fields), filter },
    });
    await ready();
    await choose("Load saved query", "Advanced query");
    await screen.findByText(/This saved query uses advanced filter groups/);
    fireEvent.click(screen.getByRole("button", { name: "Run query" }));
    await waitFor(() => expect(api.runQuery).toHaveBeenCalled());
    expect(api.runQuery.mock.calls[0][0].filter).toEqual(filter);
    expect(simplePredicates(filter)).toBeNull();
  });
});
