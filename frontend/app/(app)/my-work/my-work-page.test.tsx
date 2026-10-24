import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import MyWorkPage from "./page";

const { queue } = vi.hoisted(() => ({ queue: {
  data: { items: [], unavailable: ["data feed status"], truncated: [] as string[] },
  isPending: false, error: null, refetch: vi.fn(),
} }));
vi.mock("@/components/my-work/use-work-queue", () => ({
  useWorkQueue: () => queue,
  useFocus: () => ["business", vi.fn()],
}));

it("does not announce an all-clear when checks are unavailable or capped", () => {
  const { rerender } = render(<MyWorkPage />);
  expect(screen.getByText(/Not included: data feed status/)).toBeInTheDocument();
  expect(screen.getByText("No known items in this view")).toBeInTheDocument();
  expect(screen.getByText(/does not establish that every source was checked/)).toBeInTheDocument();
  expect(screen.queryByText(/Every figure.*reconciles and every feed/)).not.toBeInTheDocument();
  queue.data.truncated = ["daily-sales decisions"];
  rerender(<MyWorkPage />);
  expect(screen.getByRole("status")).toHaveTextContent("Showing the first 200 daily-sales decisions");
  expect(screen.getByRole("status")).toHaveTextContent("Additional decisions are not included");
});
