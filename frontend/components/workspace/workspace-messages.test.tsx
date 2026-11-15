import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WorkspaceMessages } from "./workspace-messages";
import { createWorkspaceDemo } from "@/lib/api/workspace-demo";
import { demoApi } from "@/lib/api/demo";
import { workspaceJobSchema } from "@/lib/api/workspace";

const { save, update } = vi.hoisted(() => ({ save: vi.fn(), update: vi.fn() }));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({ ...demoApi, saveDashboard: save, updateDashboard: update }) }));

describe("workspace dashboard drafts", () => {
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
