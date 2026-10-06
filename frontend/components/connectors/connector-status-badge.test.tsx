import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ConnectorStatusBadge } from "./connector-status-badge";

describe("ConnectorStatusBadge", () => {
  it("distinguishes a failure from a never-run connector", () => {
    const { rerender } = render(<ConnectorStatusBadge status="failed" />);
    expect(screen.getByText("Failed")).toBeInTheDocument();
    rerender(<ConnectorStatusBadge status="never_run" />);
    expect(screen.getByText("Never run")).toBeInTheDocument();
    expect(screen.queryByText("Failed")).not.toBeInTheDocument();
  });

  it("labels the no-new-data state distinctly", () => {
    render(<ConnectorStatusBadge status="no_new_data" />);
    expect(screen.getByText("No new data")).toBeInTheDocument();
  });

  it("labels an in-flight run as running", () => {
    render(<ConnectorStatusBadge status="running" />);
    expect(screen.getByText("Running")).toBeInTheDocument();
  });
});
