import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import type { ReconciliationRecord } from "@/lib/api";
import { ReconciliationDrillIn } from "./drill-in";

const record: ReconciliationRecord = {
  id: "sale-1",
  entity: "Sale #1",
  entityType: "daily_sales",
  fields: [
    {
      name: "amount",
      label: "Net amount",
      overridden: false,
      sources: [
        { source: "Lightspeed", value: "$248.50" },
        { source: "Cooking the Books", value: "$212.00" },
      ],
    },
    {
      name: "covers",
      label: "Covers",
      overridden: false,
      sources: [
        { source: "Lightspeed", value: "4" },
        { source: "Cooking the Books", value: "4" },
      ],
    },
  ],
};

// A stable Api instance: useApiData re-fetches whenever the Api identity changes.
const fakeApi = vi.hoisted(() => ({ listResolutionRules: async () => [] }));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => fakeApi }));

function renderSheet(onSave = vi.fn(async () => {})) {
  render(
    <Sheet open>
      <SheetContent>
        <ReconciliationDrillIn fetchRecord={async () => record} deps={[]} onSave={onSave} saving={false} />
      </SheetContent>
    </Sheet>,
  );
  return onSave;
}

describe("ReconciliationDrillIn", () => {
  it("hides agreeing fields behind a count and shows the ones needing a decision", async () => {
    renderSheet();
    expect(await screen.findByRole("heading", { name: "Net amount" })).toBeInTheDocument();
    expect(screen.getByText(/1 field agrees across sources/i)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Covers" })).not.toBeInTheDocument();
  });

  it("requires a reason before saving, and names the effect on the button", async () => {
    const onSave = renderSheet();
    fireEvent.click(await screen.findByRole("radio", { name: "Lightspeed: $248.50" }));
    const save = screen.getByRole("button", { name: "Use Lightspeed value" });

    fireEvent.click(save);
    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByText(/add a reason/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "  Till matches bank  " } });
    fireEvent.click(save);
    expect(onSave).toHaveBeenCalledWith("amount", "Lightspeed", "Till matches bank", "Net amount");
  });
});
