import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ApiError, type Api, type InventoryObservation } from "@/lib/api";
import { ObservationList } from "./observation-list";

const row: InventoryObservation = {
  kind: "COUNT", sourceSystem: "CTB", sourceRecordRef: "stocktake:1:1", logicalId: "one",
  date: "2026-10-01", productName: "Flour", stockCode: "FLOUR", quantity: 0,
  unit: "KG", unitSize: null, unitCost: null, sourceValue: null, area: null,
  stocktakePartial: true, stocktakeLocked: null, parentSourceId: 1, recordedAt: "2026-10-02T00:00:00Z",
};
function show(api: Partial<Api>) {
  return render(<DemoModeProvider><ObservationList from="2026-10-01" to="2026-10-15" apiOverride={api as Api} /></DemoModeProvider>);
}
describe("Inventory observations", () => {
  it("preserves zero, units, partial state and unknown values; filters reset pagination", async () => {
    const fetch = vi.fn().mockImplementation(async (_from, _to, _kind, page) => ({ observations: page ? [] : [row], page, size: 50, hasMore: page === 0, notices: ["Incomplete observation listing"] }));
    show({ getInventoryObservations: fetch });
    expect(await screen.findByText("0 KG")).toBeInTheDocument();
    expect(screen.getByText("Partial; lock unknown")).toBeInTheDocument();
    expect(screen.getAllByText("Unknown").length).toBeGreaterThan(0);
    expect(screen.getByText(/Incomplete — known subtotals only/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next observations" }));
    expect(await screen.findByText("No recorded observations")).toBeInTheDocument();
    expect(screen.getByText(/does not establish zero stock or waste/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Observation kind"), { target: { value: "WASTE" } });
    await waitFor(() => expect(fetch).toHaveBeenLastCalledWith("2026-10-01", "2026-10-15", "WASTE", 0, 50, expect.objectContaining({ signal: expect.any(AbortSignal) })));
  });
  it("shows permission denial without turning it into an empty observation list", async () => {
    show({ getInventoryObservations: async () => { throw new ApiError("NOT_PERMITTED", "Denied"); } });
    expect(await screen.findByText(/don't have access to stock and waste observations/i)).toBeInTheDocument();
    expect(screen.queryByText("No recorded observations")).not.toBeInTheDocument();
  });
});
