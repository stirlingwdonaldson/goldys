import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import DataPage from "./page";

vi.mock("next/navigation", async () => {
  const { useSyncExternalStore } = await import("react");
  return {
    usePathname: () => "/data",
    useSearchParams: () => new URLSearchParams(useSyncExternalStore(
      (listener) => { window.addEventListener("popstate", listener); return () => window.removeEventListener("popstate", listener); },
      () => window.location.search,
    )),
    useRouter: () => ({ push: (url: string) => { window.history.pushState(null, "", url); window.dispatchEvent(new PopStateEvent("popstate")); } }),
  };
});

const rawSummary = {
  id: "raw-1",
  sourceSystem: "CTB",
  fetcherIdentity: "ctb-invoices-ajax",
  fetchMethod: "API",
  contentType: "application/json",
  characterEncoding: "UTF-8",
  fetchedAt: "2026-10-07T12:00:00Z",
  byteLength: 312,
  sha256: "0".repeat(64),
};

vi.mock("@tanstack/react-query", async (importOriginal) => ({
  ...await importOriginal<typeof import("@tanstack/react-query")>(),
  useQuery: ({ queryFn: fetcher }: { queryFn: (context: unknown) => Promise<unknown> }) => {
    const src = String(fetcher);
    if (src.includes("listRawRecords")) {
      return {
        data: { items: [rawSummary], total: 1, page: 0, size: 50 },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("getRawRecord")) {
      return {
        data: { summary: rawSummary, payload: '{"a":1}', isJson: true, sha256: "0".repeat(64) },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("listCanonicalEntities")) {
      return {
        data: [
          { id: "daily_sales", label: "Daily sales", placeholder: false },
          { id: "shift", label: "Shifts", placeholder: true },
        ],
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("listCanonicalRows")) {
      return {
        data: {
          items: [{ id: "d1", columns: { source_system: "CTB", trading_date: "2026-10-05" } }],
          total: 1,
          page: 0,
          size: 50,
        },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("listResolvedDomains")) {
      return {
        data: [{ id: "resolved_daily_sales", label: "Daily sales", placeholder: false }],
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    if (src.includes("listResolvedRows")) {
      return {
        data: {
          items: [{ id: "2026-10-05", columns: { trading_date: "2026-10-05", total_sales: "10865.7200" } }],
          total: 1,
          page: 0,
          size: 50,
        },
        isPending: false,
        error: null,
        refetch: async () => {},
      };
    }
    return { data: null, isPending: false, error: null, refetch: async () => {} };
  },
}));

describe("DataPage", () => {
  it("renders raw records and switches to the canonical table", () => {
    render(<DataPage />);
    expect(screen.getByText("CTB")).toBeInTheDocument();
    expect(screen.getByText("ctb-invoices-ajax")).toBeInTheDocument();

    // Radix Tabs activate on mouse-down, not click.
    fireEvent.mouseDown(screen.getByRole("tab", { name: "Canonical" }), { button: 0 });
    expect(screen.getByText("trading_date")).toBeInTheDocument();
    expect(screen.getByText("2026-10-05")).toBeInTheDocument();
  });
});

vi.mock("@/lib/demo-mode", () => ({ useApi: () => ({}), useDemoMode: () => ({ demo: true }) }));
