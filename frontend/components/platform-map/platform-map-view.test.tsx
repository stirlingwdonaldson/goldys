import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ApiError, type Api } from "@/lib/api";
import { demoPlatformMap } from "@/lib/api/demo-platform-map";
import { PlatformMapView } from "./platform-map-view";

vi.mock("@/components/network/network-canvas", () => ({
  NetworkCanvas: () => <div data-testid="canvas" />,
}));
vi.mock("@/components/hooks/use-mobile", () => ({ useIsMobile: () => false }));

// A stable object: useApiData re-fetches when the Api identity changes.
const api = { getPlatformMap: vi.fn() } as unknown as Api & { getPlatformMap: ReturnType<typeof vi.fn> };
vi.mock("@/lib/demo-mode", () => ({ useApi: () => api }));

beforeEach(() => {
  api.getPlatformMap.mockReset();
});

describe("PlatformMapView", () => {
  it("says what a failing source affects when it is focused", async () => {
    api.getPlatformMap.mockResolvedValue(demoPlatformMap());
    render(<PlatformMapView />);

    fireEvent.click(await screen.findByRole("button", { name: /Cooking the Books/ }));

    expect(
      screen.getByText(
        "Cooking the Books is failing → 3 domains, 5 metrics, 3 derived metrics, 2 dashboards affected",
      ),
    ).toBeInTheDocument();
    const impact = screen.getByText(/is failing →/).closest("div")!;
    expect(impact.querySelectorAll("li")).toHaveLength(13);
    expect(impact).toHaveTextContent("Food cost %");
    expect(impact).toHaveTextContent("Kitchen costs");
  });

  it("opens on a metric's local graph when asked", async () => {
    api.getPlatformMap.mockResolvedValue(demoPlatformMap());
    render(<PlatformMapView initialFocusId="metric:sales.gross" initialLocal />);

    expect(await screen.findByRole("button", { name: "Clear focus" })).toBeInTheDocument();
    expect(screen.getByLabelText("Local graph")).toBeChecked();
    expect(screen.getByRole("region", { name: "Gross sales details" })).toBeInTheDocument();
  });

  it("shows a locked state when the caller lacks connectors access", async () => {
    api.getPlatformMap.mockRejectedValue(
      new ApiError("NOT_PERMITTED", "Not permitted", undefined, undefined, { status: 403 }),
    );
    render(<PlatformMapView />);

    expect(await screen.findByText("You don't have access to the platform map")).toBeInTheDocument();
  });
});
