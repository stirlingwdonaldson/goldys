import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { ClassificationPreviewPanel } from "./classification-preview";
const getPreview = vi.hoisted(() => vi.fn());
vi.mock("@/lib/demo-mode", () => ({useApi: () => ({getClassificationPreview:getPreview})}));
describe("classification review preview", () => {
  it("loads only on request and shows source evidence without applying a proposal", async () => {
    getPreview.mockResolvedValue({runId:"run", contextSnapshot:JSON.stringify({businessContext:"Closure in 2022 is uncertain",categoryOptions:[{categoryId:"food",name:"Food"}],candidates:{porterhouse:{text:"CTB recipe 461; source category Uncategorised",ambiguous:false}}}),proposalSnapshot:JSON.stringify([{product:"porterhouse",categoryId:"food",status:"NEEDS_REVIEW"},{product:"mixer",status:"AMBIGUOUS_IDENTITY_OR_RECIPE"}]),namingSnapshot:null});
    render(<ClassificationPreviewPanel runId="run" />);
    expect(getPreview).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button",{name:"View review preview"}));
    expect(await screen.findByText("porterhouse")).toBeInTheDocument();
    expect(screen.getByText("CTB recipe 461; source category Uncategorised")).toBeInTheDocument();
    expect(screen.getByText("ambiguous identity or recipe")).toBeInTheDocument();
    expect(getPreview).toHaveBeenCalledWith("run");
  });
  it("shows permission errors explicitly", async () => {
    getPreview.mockRejectedValue(new Error("Not permitted"));
    render(<ClassificationPreviewPanel runId="run" />);
    fireEvent.click(screen.getByRole("button",{name:"View review preview"}));
    expect(await screen.findByText("Not permitted")).toBeInTheDocument();
  });
});
