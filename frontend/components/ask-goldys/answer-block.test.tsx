import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AnswerBlock } from "./answer-block";
import type { DashboardDraft, AnswerPayload } from "./types";
import type { Api, DashboardDocument, SavedWidget } from "@/lib/api/types";

function widget(): SavedWidget {
  return {
    id: "w1",
    renderType: "time-series",
    queries: [
      {
        metric: "sales.gross",
        range: { from: "2026-10-01", to: "2026-10-07", calendar: "TRADING" },
        grain: "DAY",
        dimensions: [],
        comparison: null,
      },
    ],
    layout: { w: 6, h: 2 },
  };
}

function draft(): DashboardDraft {
  return {
    title: "Weekly sales",
    description: "Gross sales by day",
    filters: { dateRange: null, comparison: null, dimensions: [] },
    widgets: [widget()],
  };
}

/** A stub Api that is only expected to have saveDashboard exercised. */
function stubApi(overrides: Partial<Api> = {}): Api {
  return { saveDashboard: vi.fn(async () => savedDocument()), ...overrides } as unknown as Api;
}

function savedDocument(): DashboardDocument {
  return {
    id: "d1",
    schemaVersion: 2,
    title: "Weekly sales",
    description: "Gross sales by day",
    layout: "grid",
    widgets: [widget()],
    filters: { dateRange: null, comparison: null, dimensions: [] },
    visibility: "PRIVATE",
    pinned: false,
    createdBy: "You",
    createdAt: "2026-10-07T10:00:00Z",
    updatedAt: "2026-10-07T10:00:00Z",
  };
}

describe("AnswerBlock", () => {
  it("shows summary, trace, as-of, and notices", () => {
    const answer: AnswerPayload = {
      widgets: [],
      trace: [
        {
          tool: "get_sales_by_period",
          description: "Resolved daily sales totals.",
          provenance: [],
        },
      ],
      asOf: "2026-10-02T10:00:00Z",
      notices: ["1 date(s) have no resolved total (unresolved conflict)."],
    };
    render(<AnswerBlock summary="Sales were $27,650.66." answer={answer} error={null} api={stubApi()} />);
    expect(screen.getByText(/sales were/i)).toBeInTheDocument();
    expect(screen.getByText(/get_sales_by_period/i)).toBeInTheDocument();
    expect(screen.getByText(/no resolved total/i)).toBeInTheDocument();
  });

  it("shows the error message when present", () => {
    render(
      <AnswerBlock summary="" answer={null} error="You don't have access to that data." api={stubApi()} />,
    );
    // PermissionDenied renders both a heading and the message; assert the unique message text.
    expect(screen.getByText(/that data/i)).toBeInTheDocument();
  });

  it("renders a preview for a dashboard draft and saves on click, not on render", async () => {
    const saveDashboard = vi.fn(async () => savedDocument());
    const answer: AnswerPayload = {
      widgets: [],
      trace: [],
      asOf: "2026-10-07T10:00:00Z",
      notices: [],
      draft: draft(),
    };
    render(<AnswerBlock summary="" answer={answer} error={null} api={stubApi({ saveDashboard })} />);

    // The preview is rendered with a compact textual widget summary.
    expect(screen.getByText("Weekly sales")).toBeInTheDocument();
    expect(screen.getByText("Gross sales by day")).toBeInTheDocument();
    expect(screen.getByText(/time-series · 1 metric/i)).toBeInTheDocument();

    // No auto-save on render.
    expect(saveDashboard).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: /save dashboard/i }));

    await waitFor(() => expect(saveDashboard).toHaveBeenCalledTimes(1));
    expect(saveDashboard).toHaveBeenCalledWith({
      title: "Weekly sales",
      description: "Gross sales by day",
      layout: "grid",
      filters: { dateRange: null, comparison: null, dimensions: [] },
      visibility: "PRIVATE",
      widgets: [widget()],
    });

    expect(await screen.findByText(/dashboard saved/i)).toBeInTheDocument();
  });

  it("surfaces a save failure without leaving the button in a stuck state", async () => {
    const saveDashboard = vi.fn(async () => {
      throw new Error("Network failure");
    });
    const answer: AnswerPayload = {
      widgets: [],
      trace: [],
      asOf: "2026-10-07T10:00:00Z",
      notices: [],
      draft: draft(),
    };
    render(<AnswerBlock summary="" answer={answer} error={null} api={stubApi({ saveDashboard })} />);

    fireEvent.click(screen.getByRole("button", { name: /save dashboard/i }));

    expect(await screen.findByText(/network failure/i)).toBeInTheDocument();
    // The button is re-enabled for a retry.
    expect(screen.getByRole("button", { name: /save dashboard/i })).toBeEnabled();
    expect(saveDashboard).toHaveBeenCalledTimes(1);
  });

  it("renders provenance detail in the trace", () => {
    const answer: AnswerPayload = {
      widgets: [],
      trace: [
        {
          tool: "get_sales_by_period",
          description: "Resolved daily sales totals.",
          provenance: [
            {
              metric: "sales.gross",
              definitionVersion: "v1",
              range: { from: "2026-10-01", to: "2026-10-07", calendar: "TRADING" },
              grain: "DAY",
              sourceDomain: "lightspeed",
              dataFreshness: "2026-10-07T09:00:00Z",
              missingPeriods: ["2026-10-03", "2026-10-04"],
              calculationVersion: "v3",
            },
          ],
        },
      ],
      asOf: "2026-10-02T10:00:00Z",
      notices: [],
    };
    render(<AnswerBlock summary="" answer={answer} error={null} api={stubApi()} />);

    expect(screen.getByText(/get_sales_by_period/i)).toBeInTheDocument();
    expect(screen.getByText("sales.gross")).toBeInTheDocument();
    expect(screen.getByText(/2026-10-01 → 2026-10-07/)).toBeInTheDocument();
    expect(screen.getByText(/data as of 2026-10-07T09:00:00Z/i)).toBeInTheDocument();
    expect(screen.getByText(/missing 2026-10-03, 2026-10-04/)).toBeInTheDocument();
  });

  it("updates a dashboard when the draft has a dashboardId", async () => {
    const updateDashboard = vi.fn(async () => savedDocument());
    const saveDashboard = vi.fn(async () => savedDocument());
    const answer: AnswerPayload = {
      widgets: [],
      trace: [],
      asOf: "2026-10-07T10:00:00Z",
      notices: [],
      draft: { ...draft(), dashboardId: "d1" },
    };
    render(
      <AnswerBlock
        summary=""
        answer={answer}
        error={null}
        api={stubApi({ updateDashboard, saveDashboard })}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /save dashboard/i }));

    await waitFor(() => expect(updateDashboard).toHaveBeenCalledTimes(1));
    expect(updateDashboard).toHaveBeenCalledWith("d1", {
      title: "Weekly sales",
      description: "Gross sales by day",
      layout: "grid",
      filters: { dateRange: null, comparison: null, dimensions: [] },
      visibility: "PRIVATE",
      widgets: [widget()],
    });
    expect(saveDashboard).not.toHaveBeenCalled();

    expect(await screen.findByText(/dashboard saved/i)).toBeInTheDocument();
  });
});
