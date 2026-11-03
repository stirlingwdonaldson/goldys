import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, it, expect, vi } from "vitest";
import { ReportingMappingEditor } from "./reporting-mapping-editor";
const api = vi.hoisted(() => ({
  previewReportingMapping: vi.fn(),
  saveReportingMapping: vi.fn(),
  getReportingMappings: vi.fn(),
  getClassifiedWastage: vi.fn(),
}));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => api }));
describe("reporting mapping review", () => {
  beforeEach(() => vi.resetAllMocks());
  it("previews an explicit dated decision, invalidates changed drafts and saves only on approval", async () => {
    api.previewReportingMapping.mockImplementation(async (mapping) => ({
      mapping,
      reviewable: true,
      evidence: "Exact CTB recipe 388; cafe dates unknown",
      through: mapping.effectiveFrom,
      observedRows: 2,
      knownAmount: 10,
      amountBasis: "Recorded value",
      notes: ["Earlier effective date restates history"],
    }));
    api.saveReportingMapping.mockResolvedValue(undefined);
    render(<ReportingMappingEditor />);
    fireEvent.change(screen.getByLabelText(/Exact identity/), {
      target: { value: "steak" },
    });
    fireEvent.change(screen.getByLabelText("Review reason and evidence"), {
      target: { value: "Source evidence reviewed" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Preview mapping impact" }),
    );
    expect(
      await screen.findByText("Exact CTB recipe 388; cafe dates unknown"),
    ).toBeInTheDocument();
    expect(api.saveReportingMapping).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Sales nature"), {
      target: { value: "FOOD" },
    });
    expect(
      screen.queryByRole("button", { name: "Approve reviewed mapping" }),
    ).not.toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("button", { name: "Preview mapping impact" }),
    );
    fireEvent.click(
      await screen.findByRole("button", { name: "Approve reviewed mapping" }),
    );
    await waitFor(() =>
      expect(api.saveReportingMapping).toHaveBeenCalledWith(
        expect.objectContaining({
          entityKey: "steak",
          salesNature: "FOOD",
          businessArea: "UNKNOWN",
          lifecycle: "UNKNOWN",
          reason: "Source evidence reviewed",
        }),
      ),
    );
  });
  it("shows denied reads and keeps an ambiguous identity held", async () => {
    api.getClassifiedWastage.mockRejectedValue(new Error("Not permitted"));
    render(<ReportingMappingEditor />);
    fireEvent.click(
      screen.getByRole("button", { name: "Load wastage evidence" }),
    );
    expect(await screen.findByText("Not permitted")).toBeInTheDocument();
    expect(api.saveReportingMapping).not.toHaveBeenCalled();
  });
});
