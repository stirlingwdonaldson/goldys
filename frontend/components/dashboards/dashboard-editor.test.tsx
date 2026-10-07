import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DemoModeProvider } from "@/lib/demo-mode";
import { DashboardCard } from "./dashboard-card";
import { DashboardEditor } from "./dashboard-editor";
import { DashboardsPageView } from "./dashboards-page-view";
import type {
  Api,
  DashboardDocument,
  RenderedWidget,
  SaveDashboardInput,
  SavedDashboardSummary,
  SavedWidget,
} from "@/lib/api/types";
import type { WidgetSpec } from "@/components/widgets/types";

const spec: WidgetSpec = {
  schemaVersion: 2,
  id: "w1",
  type: "time-series",
  title: "Daily sales",
  series: [{ key: "grossSales", label: "Gross sales", points: [{ x: "2026-10-01", y: 1 }] }],
  yFormat: "currency",
};

function widget(): SavedWidget {
  return {
    id: "w1",
    renderType: "time-series",
    queries: [
      {
        metric: "sales.gross",
        range: { from: "2026-10-01", to: "2026-10-05", calendar: "CALENDAR" },
        grain: "DAY",
        dimensions: [],
        comparison: null,
      },
    ],
    layout: { w: 6, h: 2 },
  };
}

function document(): DashboardDocument {
  return {
    id: "d1",
    schemaVersion: 2,
    title: "Sales dashboard",
    description: "Gross and net sales",
    layout: "grid",
    widgets: [widget()],
    filters: { dateRange: null, comparison: null, dimensions: [] },
    visibility: "PRIVATE",
    pinned: false,
    createdBy: "You",
    createdAt: "2026-10-05T00:00:00Z",
    updatedAt: "2026-10-05T00:00:00Z",
  };
}

function rendered(): RenderedWidget[] {
  return [{ widgetId: "w1", widget: spec, deniedResource: null }];
}

function saveResult(input: SaveDashboardInput): DashboardDocument {
  const base = document();
  return {
    ...base,
    title: input.title,
    description: input.description ?? null,
    widgets: input.widgets,
    filters: input.filters ?? base.filters,
    visibility: input.visibility ?? base.visibility,
  };
}

type UpdateDashboardMock = (id: string, input: SaveDashboardInput) => Promise<DashboardDocument>;

function updateMock() {
  return vi.fn<UpdateDashboardMock>(async (_id, input) => saveResult(input));
}

/** A stub Api with sane editor defaults; override any method in tests. */
function editorApi(overrides: Partial<Api> = {}): Api {
  return {
    getDashboard: async () => document(),
    updateDashboard: async (_id: string, input: SaveDashboardInput) => saveResult(input),
    renderDashboard: async () => rendered(),
    listDashboardRevisions: async () => [
      { revision: 1, createdBy: "You", createdAt: "2026-10-05T00:00:00Z" },
    ],
    restoreDashboardRevision: async () => document(),
    ...overrides,
  } as Api;
}

describe("DashboardEditor", () => {
  it("loads the persisted document and saves edits through updateDashboard", async () => {
    const updateDashboard = updateMock();
    render(<DashboardEditor api={editorApi({ updateDashboard })} dashboardId="d1" />);

    expect(await screen.findByText("Sales dashboard")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Gross and net sales")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "New title" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(updateDashboard).toHaveBeenCalled());
    expect(updateDashboard.mock.calls[0][0]).toBe("d1");
    expect(updateDashboard.mock.calls[0][1]).toMatchObject({ title: "New title" });
    expect(await screen.findByText("Saved.")).toBeInTheDocument();
  });

  it("duplicates a widget with a fresh id and the same query/renderType/layout", async () => {
    const updateDashboard = updateMock();
    render(<DashboardEditor api={editorApi({ updateDashboard })} dashboardId="d1" />);
    await screen.findByText("Sales dashboard");

    fireEvent.click(screen.getByLabelText("Duplicate widget"));
    expect(screen.getAllByLabelText("Duplicate widget")).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(updateDashboard).toHaveBeenCalled());
    const widgets = updateDashboard.mock.calls[0][1].widgets;
    expect(widgets).toHaveLength(2);
    expect(widgets[1].id).not.toBe(widgets[0].id);
    expect(widgets[1].renderType).toBe(widgets[0].renderType);
    expect(widgets[1].queries).toEqual(widgets[0].queries);
    expect(widgets[1].layout).toEqual(widgets[0].layout);
  });

  it("resizes a widget's width and clamps it to the 1..12 range", async () => {
    const updateDashboard = updateMock();
    render(<DashboardEditor api={editorApi({ updateDashboard })} dashboardId="d1" />);
    await screen.findByText("Sales dashboard");

    fireEvent.click(screen.getByLabelText("Increase width"));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(updateDashboard).toHaveBeenCalled());
    expect(updateDashboard.mock.calls[0][1].widgets[0].layout.w).toBe(7);

    // Decrease far past the minimum; width must clamp at 1, not go negative.
    for (let i = 0; i < 12; i++) fireEvent.click(screen.getByLabelText("Decrease width"));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(updateDashboard).toHaveBeenCalledTimes(2));
    expect(updateDashboard.mock.calls[1][1].widgets[0].layout.w).toBe(1);
  });

  it("renders a per-widget not-permitted placeholder for a denied resource", async () => {
    const denied: RenderedWidget[] = [{ widgetId: "w1", widget: null, deniedResource: "labour.cost" }];
    render(<DashboardEditor api={editorApi({ renderDashboard: async () => denied })} dashboardId="d1" />);

    expect(await screen.findByText(/don't have access to labour\.cost/i)).toBeInTheDocument();
  });

  it("restores a revision from the revisions panel", async () => {
    const restoreDashboardRevision = vi.fn(async () => document());
    render(
      <DashboardEditor api={editorApi({ restoreDashboardRevision })} dashboardId="d1" />,
    );
    await screen.findByText("Sales dashboard");

    fireEvent.click(screen.getByRole("button", { name: "Revisions" }));
    await screen.findByText(/revision 1/i);
    fireEvent.click(screen.getByRole("button", { name: "Restore" }));

    await waitFor(() => expect(restoreDashboardRevision).toHaveBeenCalledWith("d1", 1));
  });
});

describe("dashboard library", () => {
  it("renders empty state when there are no dashboards", async () => {
    const api = {
      listDashboards: async () => [],
      listDashboardTemplates: async () => [],
    } as unknown as Api;
    render(
      <DemoModeProvider>
        <DashboardsPageView api={api} />
      </DemoModeProvider>,
    );
    expect(await screen.findByText(/no saved dashboards/i)).toBeInTheDocument();
  });

  it("opens the template dialog from the empty state", async () => {
    const templates = [
      { id: "daily", name: "Daily Management", description: "Today's sales", widgets: [widget()] },
    ];
    const api = {
      listDashboards: async () => [],
      listDashboardTemplates: async () => templates,
    } as unknown as Api;
    render(
      <DemoModeProvider>
        <DashboardsPageView api={api} />
      </DemoModeProvider>,
    );
    expect(await screen.findByText(/no saved dashboards/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /new from template/i }));

    // The dialog must actually appear (the regression was that it was never mounted here).
    expect(await screen.findByText("Daily Management")).toBeInTheDocument();
  });

  it("shows a card's title, description, creator, visibility and pin toggle", () => {
    const summary: SavedDashboardSummary = {
      id: "d1",
      title: "Sales",
      updatedAt: "2026-10-05T00:00:00Z",
      description: "Gross and net",
      createdBy: "You",
      pinned: true,
      visibility: "SHARED",
    };
    const onTogglePin = vi.fn();
    render(<DashboardCard dashboard={summary} onTogglePin={onTogglePin} />);

    expect(screen.getByText("Sales")).toBeInTheDocument();
    expect(screen.getByText("Gross and net")).toBeInTheDocument();
    expect(screen.getByText("Shared")).toBeInTheDocument();
    expect(screen.getByText(/You · Updated/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /unpin dashboard/i }));
    expect(onTogglePin).toHaveBeenCalled();
  });
});
