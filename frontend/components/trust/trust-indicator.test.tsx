import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { formatAgo, TrustIndicator, TRUST_LABEL } from "./trust-indicator";
import type { TrustState, TrustSummary } from "@/lib/api/types";

const ALL_TRUST_STATES: TrustState[] = [
  "VERIFIED",
  "RESOLVED_BY_RULE",
  "MANUALLY_OVERRIDDEN",
  "SINGLE_SOURCE",
  "CONFLICTED",
  "INCOMPLETE",
  "NOT_RECEIVED",
];

function trust(overrides: Partial<TrustSummary> = {}): TrustSummary {
  return {
    state: "VERIFIED",
    freshness: "FRESH",
    authoritativeSource: "Lightspeed",
    resolvedAt: null,
    lastIngestionAt: null,
    threshold: null,
    ...overrides,
  };
}

describe("formatAgo", () => {
  it("formats a 7-minute-old instant as '7 min ago'", () => {
    const now = new Date("2026-10-08T10:00:00Z");
    const sevenMinAgo = new Date(now.getTime() - 7 * 60_000).toISOString();
    expect(formatAgo(sevenMinAgo, now)).toBe("7 min ago");
  });

  it("formats a sub-minute instant as 'just now'", () => {
    const now = new Date("2026-10-08T10:00:00Z");
    const halfMinAgo = new Date(now.getTime() - 30_000).toISOString();
    expect(formatAgo(halfMinAgo, now)).toBe("just now");
  });
});

describe("TrustIndicator", () => {
  it("renders the venue-friendly label for every trust state", () => {
    const { rerender } = render(<TrustIndicator trust={trust()} value={123} />);
    for (const state of ALL_TRUST_STATES) {
      rerender(<TrustIndicator trust={trust({ state })} value={123} />);
      expect(screen.getByText(TRUST_LABEL[state])).toBeInTheDocument();
    }
  });

  it("renders freshness: updated X ago / stale / source failed / no data", () => {
    const now = Date.now();
    const sevenMinAgo = new Date(now - 7 * 60_000).toISOString();

    const { rerender } = render(
      <TrustIndicator trust={trust({ lastIngestionAt: sevenMinAgo })} value={123} />,
    );
    expect(screen.getByText(/^updated .+ ago$/)).toBeInTheDocument();

    rerender(<TrustIndicator trust={trust({ freshness: "STALE" })} value={123} />);
    expect(screen.getByText("stale")).toBeInTheDocument();

    rerender(<TrustIndicator trust={trust({ freshness: "SOURCE_FAILURE" })} value={123} />);
    expect(screen.getByText("source failed")).toBeInTheDocument();

    rerender(<TrustIndicator trust={trust({ freshness: "UNKNOWN" })} value={123} />);
    expect(screen.getByText("no data")).toBeInTheDocument();
  });

  it("renders a null value with NOT_RECEIVED as 'No data', never '0'", () => {
    render(
      <TrustIndicator
        trust={trust({ state: "SINGLE_SOURCE", freshness: "UNKNOWN" })}
        value={null}
        status="NOT_RECEIVED"
      />,
    );
    expect(screen.getByText("No data")).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });

  it("renders a null value with UNRESOLVED as 'Unresolved'", () => {
    render(
      <TrustIndicator
        trust={trust({ state: "CONFLICTED", freshness: "UNKNOWN" })}
        value={null}
        status="UNRESOLVED"
      />,
    );
    expect(screen.getByText("Unresolved")).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });
});
