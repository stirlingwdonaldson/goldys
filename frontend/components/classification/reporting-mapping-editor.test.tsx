import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import { beforeEach, describe, it, expect, vi } from "vitest";
import { ReportingMappingEditor } from "./reporting-mapping-editor";
const api = vi.hoisted(() => ({
  previewReportingMapping: vi.fn(),
  saveReportingMapping: vi.fn(),
  getReportingMappings: vi.fn(),
  getClassifiedWastage: vi.fn(),
  getReportingReviewQueue: vi.fn(),
}));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => api }));
describe("reporting mapping review", () => {
  beforeEach(() => vi.resetAllMocks());
  it("selects an exact queue identity, requires fresh approval and refreshes coverage after save", async () => {
    const item = {
      scope: "CTB_PRODUCT",
      sourceSystem: "CTB",
      entityKey: "site:0:code:8:20161229",
      names: "Gnocchi - Pumpkin Butter Sage",
      firstDate: "2026-11-02",
      lastDate: "2026-11-02",
      observations: 1,
      knownExposure: 788.6411,
      missingBasisGross: null,
      missingBasis: 0,
      unresolved: 0,
      policies: "No applicable policy",
      holds: "No applicable policy",
      evidence: "Exact site/code; POS Weekly Food Specials",
    };
    const queue = {
      items: [item],
      hasMore: false,
      days: [
        {
          date: "2026-11-02",
          salesCoverage: 0.87,
          unclassifiedSales: 2107.5662,
          missingTax: 0,
          unresolved: 0,
          belowThreshold: true,
        },
      ],
      wastage: {
        lines: 2,
        classifiedLines: 0,
        reasonLines: 2,
        missingCost: 0,
        missingDate: 0,
        knownValue: 5.93,
        unknownValue: 5.93,
      },
    };
    api.getReportingReviewQueue
      .mockResolvedValueOnce(queue)
      .mockResolvedValue({ ...queue, items: [], days: [] });
    api.previewReportingMapping.mockImplementation(async (mapping) => ({
      mapping,
      reviewable: true,
      evidence: "Reviewed exact identity",
      notes: [],
      observedRows: 1,
      knownAmount: 866.7919,
      through: "2026-11-03",
    }));
    api.saveReportingMapping.mockResolvedValue(undefined);
    render(<ReportingMappingEditor />);
    fireEvent.click(
      screen.getByRole("button", { name: "Refresh review queue" }),
    );
    expect(await screen.findByText(/Below 95%/)).toBeInTheDocument();
    const select = screen.getByRole("button", {
      name: "Review site:0:code:8:20161229",
    });
    fireEvent.click(select);
    expect(screen.getByLabelText(/Exact identity/)).toHaveValue(item.entityKey);
    expect(screen.getByLabelText("Effective from")).toHaveValue("2026-11-02");
    expect(screen.getByLabelText("Sales nature")).toHaveValue("UNKNOWN");
    expect(screen.getByLabelText("Review reason and evidence")).toHaveValue("");
    fireEvent.change(screen.getByLabelText("Review reason and evidence"), {
      target: { value: "Evidence reviewed; date restates 2 November" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Preview mapping impact" }),
    );
    await screen.findByRole("button", { name: "Approve reviewed mapping" });
    expect(api.saveReportingMapping).not.toHaveBeenCalled();
    fireEvent.click(select);
    expect(
      screen.queryByRole("button", { name: "Approve reviewed mapping" }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Review reason and evidence"), {
      target: { value: "Confirmed food from source evidence" },
    });
    fireEvent.change(screen.getByLabelText("Sales nature"), {
      target: { value: "FOOD" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Preview mapping impact" }),
    );
    fireEvent.click(
      await screen.findByRole("button", { name: "Approve reviewed mapping" }),
    );
    expect(
      await screen.findByText("No unmapped or held identities on this page."),
    ).toBeInTheDocument();
    expect(api.getReportingReviewQueue).toHaveBeenCalledTimes(2);
    expect(api.saveReportingMapping).toHaveBeenCalledWith(
      expect.objectContaining({
        entityKey: item.entityKey,
        salesNature: "FOOD",
        businessArea: "UNKNOWN",
      }),
    );
  });

  it("shows explicit queue permission failures without presenting an empty queue", async () => {
    api.getReportingReviewQueue.mockRejectedValue(
      new Error("Not permitted: inventory.cost"),
    );
    render(<ReportingMappingEditor />);
    fireEvent.click(
      screen.getByRole("button", { name: "Refresh review queue" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Not permitted");
    expect(
      screen.queryByText("No unmapped or held identities on this page."),
    ).not.toBeInTheDocument();
  });
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
  it("locks identity selectors until a deferred preview settles and identifies the approved draft", async () => {
    const history = {
      scope: "PRODUCT_NAME",
      sourceSystem: "RESOLVED",
      entityKey: "steak",
      effectiveFrom: "2026-10-26",
      salesNature: "FOOD",
      stockBucket: "UNKNOWN",
      itemRole: "UNKNOWN",
      businessArea: "UNKNOWN",
      lifecycle: "UNKNOWN",
      reason: "Reviewed recipe",
    };
    api.getReportingMappings.mockResolvedValue([history]);
    api.getClassifiedWastage.mockResolvedValue([
      {
        sourceRecordRef: "wastage:159:803",
        date: "2026-10-26",
        name: "Gnocchi",
        quantity: 1,
        sourceUnitCost: 2,
        recipeId: 388,
      },
    ]);
    let resolvePreview!: (value: unknown) => void;
    api.previewReportingMapping.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePreview = resolve;
        }),
    );
    render(<ReportingMappingEditor />);
    fireEvent.click(
      screen.getByRole("button", { name: "Load revision history" }),
    );
    const revision = await screen.findByRole("button", {
      name: /PRODUCT_NAME: steak/,
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Load wastage evidence" }),
    );
    const exact = await screen.findByRole("button", { name: "Exact line" });
    const recipe = screen.getByRole("button", { name: "Recipe 388" });
    fireEvent.change(screen.getByLabelText(/Exact identity/), {
      target: { value: "steak" },
    });
    fireEvent.change(screen.getByLabelText("Review reason and evidence"), {
      target: { value: "Reviewed steak" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Preview mapping impact" }),
    );
    for (const selector of [exact, recipe, revision]) {
      expect(selector).toBeDisabled();
      fireEvent.click(selector);
    }
    expect(screen.getByLabelText(/Exact identity/)).toHaveValue("steak");
    const draft = api.previewReportingMapping.mock.calls[0][0];
    await act(async () =>
      resolvePreview({
        mapping: draft,
        reviewable: true,
        evidence: "Steak evidence",
        through: draft.effectiveFrom,
        observedRows: 1,
        knownAmount: 10,
        amountBasis: "Gross",
        notes: [],
      }),
    );
    expect(
      screen.getByText(/Proposed identity: RESOLVED \/ PRODUCT_NAME \/ steak/),
    ).toBeInTheDocument();
    fireEvent.click(exact);
    expect(screen.getByLabelText(/Exact identity/)).toHaveValue(
      "wastage:159:803",
    );
    expect(
      screen.queryByRole("button", { name: "Approve reviewed mapping" }),
    ).not.toBeInTheDocument();
    expect(api.saveReportingMapping).not.toHaveBeenCalled();
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
