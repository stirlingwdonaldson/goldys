import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WorkspaceMessages } from "./workspace-messages";
import { createWorkspaceDemo } from "@/lib/api/workspace-demo";
import { demoApi } from "@/lib/api/demo";
import { provenanceSchema } from "@/lib/api/dashboard-schema";
import { workspaceJobSchema } from "@/lib/api/workspace";

const { save, update } = vi.hoisted(() => ({ save: vi.fn(), update: vi.fn() }));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({ ...demoApi, saveDashboard: save, updateDashboard: update }) }));

describe("workspace dashboard drafts", () => {
  it("shows a saved opening prompt once without hiding identical follow-ups", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    job.messages = [{ id: "opening", role: "user", text: job.prompt, reporting: null }];
    const view = render(<WorkspaceMessages job={job} />);
    expect(screen.getAllByText(job.prompt)).toHaveLength(1);
    view.rerender(<WorkspaceMessages job={{ ...job, messages: [...job.messages, { id: "repeat", role: "user", text: job.prompt, reporting: null }] }} />);
    expect(screen.getAllByText(job.prompt)).toHaveLength(2);
  });
  it("shows the original prompt before the worker has saved any messages", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    render(<WorkspaceMessages job={{ ...job, messages: [] }} />);
    expect(screen.getAllByText(job.prompt)).toHaveLength(1);
  });
  it("shows the recorded provider and model alongside the answer", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    job.messages = [{ id: "answer", role: "assistant", text: "Answer", reporting: null, provider: "openai", model: "recorded-model" }];
    render(<WorkspaceMessages job={job} />);
    expect(screen.getByText("OpenAI · recorded-model")).toBeVisible();
  });
  beforeEach(() => vi.clearAllMocks());
  it("restores a validated draft and saves query configuration only after confirmation", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    save.mockRejectedValueOnce(new Error("Not permitted to save this dashboard"))
      .mockImplementationOnce(async input => ({ ...input, id: "saved", schemaVersion: 2 }));
    render(<QueryClientProvider client={new QueryClient()}><WorkspaceMessages job={job} /></QueryClientProvider>);
    expect(screen.getByText("Demo weekly sales")).toBeInTheDocument();
    expect(save).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Save dashboard" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Not permitted");
    fireEvent.click(screen.getByRole("button", { name: "Save dashboard" }));
    expect(await screen.findByText(/Dashboard saved/)).toBeInTheDocument();
    expect(save).toHaveBeenLastCalledWith({
      title: "Demo weekly sales", description: "A reusable sales query, not a saved result.",
      filters: job.messages[0].draft!.filters, widgets: job.messages[0].draft!.widgets,
      visibility: "PRIVATE", layout: "grid",
    });
    expect(update).not.toHaveBeenCalled();
  });
  it("renders persisted public citations without enabling unsafe links or HTML", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    job.messages = [{ id: "citation", role: "assistant", reporting: null,
      text: "Retrieved 2026-11-16: https://www.fairwork.gov.au/pay https://evil.test/ https://www.fairwork.gov.au.evil.test/ https://user:secret@www.fairwork.gov.au/ https://www.fairwork.gov.au:8443/ <script>alert(1)</script>" }];
    const view = render(<WorkspaceMessages job={job} />);
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute("href", "https://www.fairwork.gov.au/pay");
    expect(links[0]).toHaveAttribute("rel", "noopener noreferrer");
    expect(links[0]).toHaveAttribute("referrerpolicy", "no-referrer");
    expect(view.container.querySelector("script")).toBeNull();
    expect(screen.getByText(/Retrieved 2026-11-16/)).toBeInTheDocument();
  });
  it("collapses activity and data notes independently while keeping follow-up answers visible", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    const reporting = { widget: { schemaVersion: 2 as const, type: "stat" as const, id: "missing", title: "Sales", value: null, format: "currency" as const }, notices: ["Coverage is incomplete."], provenance: [provenanceSchema.parse({
      metric: "sales.gross", definitionVersion: "v1", range: { from: "2026-11-01", to: "2026-11-30", calendar: "CALENDAR" },
      grain: "TOTAL", sourceDomain: "resolved_daily_sales", dataFreshness: null, calculationVersion: "v1", missingPeriods: ["2026-11-19"],
      trust: { state: "INCOMPLETE", freshness: "SOURCE_FAILURE", authoritativeSource: "LIGHTSPEED", resolvedAt: null, lastIngestionAt: null, threshold: "PT2H" },
    })], relatedMetrics: [] };
    job.messages = [
      { id: "progress", role: "assistant", text: "Checking sales.", reporting: null },
      { id: "tool", role: "tool", text: "", reporting },
      { id: "answer", role: "assistant", text: "No reliable total is available.", reporting: null },
      { id: "followup", role: "user", text: "What about labour?", reporting: null },
      { id: "second", role: "assistant", text: "Labour is unavailable.", reporting: null },
    ];
    render(<WorkspaceMessages job={job} />);
    expect(screen.queryByText("Checking sales.")).not.toBeInTheDocument();
    expect(screen.getByText("No reliable total is available.")).toBeVisible();
    expect(screen.getByText("Labour is unavailable.")).toBeVisible();
    expect(screen.queryByText("Coverage is incomplete.")).not.toBeInTheDocument();
    expect(screen.queryByText(/Missing periods/)).not.toBeInTheDocument();
    expect(screen.queryByText("source failed")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Data notes" }));
    expect(screen.getByText("Coverage is incomplete.")).toBeVisible();
    expect(screen.getByText(/Missing periods: 2026-11-19/)).toBeVisible();
    expect(screen.getByText("Incomplete")).toBeVisible();
    expect(screen.getByText("source failed")).toBeVisible();
    expect(screen.queryByText("Checking sales.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Data notes" }));
    expect(screen.queryByText("Coverage is incomplete.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Investigation activity (2)" }));
    expect(screen.getByText("Checking sales.")).toBeVisible();
    expect(screen.getByText("Sales")).toBeVisible();
  });
  it("shows the final chart inline and retains earlier charts in restored history", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    const reporting = { widget: { schemaVersion: 2 as const, type: "time-series" as const, id: "comparison", title: "Weekday sales comparison", yFormat: "currency" as const,
      series: [
        { key: "period-0", label: "9 Nov 2026 – 13 Nov 2026", points: [{ x: "Mon", y: 1100 }, { x: "Tue", y: null }] },
        { key: "period-1", label: "2 Nov 2026 – 6 Nov 2026", points: [{ x: "Mon", y: 1000 }, { x: "Tue", y: 0 }] },
      ] }, notices: ["Tuesday is unavailable."], provenance: [], relatedMetrics: [] };
    job.messages = [
      { id: "earlier", role: "tool", text: "", reporting: { ...reporting, widget: { ...reporting.widget, id: "earlier", title: "Exploratory sales" } } },
      { id: "chart", role: "tool", text: "", reporting },
      { id: "answer", role: "assistant", text: "Here are the two completed weeks.", reporting: null },
      { id: "followup", role: "user", text: "Thanks", reporting: null },
      { id: "reply", role: "assistant", text: "You’re welcome.", reporting: null },
    ];
    render(<WorkspaceMessages job={workspaceJobSchema.parse(JSON.parse(JSON.stringify(job)))} />);
    expect(screen.getByRole("img", { name: "Weekday sales comparison" })).toBeVisible();
    expect(screen.getByRole("list", { name: "Chart legend" })).toHaveTextContent("9 Nov 2026 – 13 Nov 2026");
    expect(screen.getByRole("list", { name: "Chart legend" })).toHaveTextContent("2 Nov 2026 – 6 Nov 2026");
    expect(screen.queryByText("Exploratory sales")).not.toBeInTheDocument();
    expect(screen.queryByText("Tuesday is unavailable.")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save dashboard" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Investigation activity (1)" }));
    expect(screen.getByRole("img", { name: "Exploratory sales" })).toBeVisible();
    expect(screen.getAllByRole("img", { name: "Weekday sales comparison" })).toHaveLength(1);
  });
  it("keeps every explicit supporting chart inline across restored exchanges", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    const widget = { schemaVersion: 2 as const, type: "chart" as const, id: "chart", title: "Sales trend", kind: "LINE" as const, yFormat: "currency" as const, stacked: false, horizontal: false,
      series: [{ key: "sales", label: "Sales", points: [{ x: "Mon", y: 100 }, { x: "Tue", y: 200 }] }] };
    job.messages = [
      { id: "chart-one", role: "tool", text: "", reporting: { widget, notices: [], provenance: [], relatedMetrics: [] } },
      { id: "chart-two", role: "tool", text: "", reporting: { widget: { ...widget, id: "bookings", title: "Bookings trend", yFormat: "number" }, notices: [], provenance: [], relatedMetrics: [] } },
      { id: "answer", role: "assistant", text: "Sales and bookings both increased.", reporting: null },
    ];
    render(<WorkspaceMessages job={workspaceJobSchema.parse(JSON.parse(JSON.stringify(job)))} />);
    expect(screen.getByRole("img", { name: "Sales trend — line chart" })).toBeVisible();
    expect(screen.getByRole("img", { name: "Bookings trend — line chart" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Save dashboard" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Investigation activity/ })).not.toBeInTheDocument();
  });
  it("routes an existing-dashboard proposal through explicit update", async () => {
    const job = workspaceJobSchema.parse(await createWorkspaceDemo().getWorkspaceJob("10000000-0000-4000-8000-000000000001"));
    job.messages[0].draft!.dashboardId = "existing";
    update.mockImplementationOnce(async (id, input) => ({ ...input, id, schemaVersion: 2 }));
    render(<QueryClientProvider client={new QueryClient()}><WorkspaceMessages job={job} /></QueryClientProvider>);
    expect(update).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Save dashboard" }));
    expect(await screen.findByText(/Dashboard saved/)).toBeInTheDocument();
    expect(update).toHaveBeenCalledWith("existing", expect.objectContaining({ widgets: job.messages[0].draft!.widgets }));
    expect(save).not.toHaveBeenCalled();
  });

});
