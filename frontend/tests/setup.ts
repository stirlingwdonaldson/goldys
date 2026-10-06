import "@testing-library/jest-dom/vitest";

// Recharts' ResponsiveContainer uses ResizeObserver, which jsdom does not provide.
// A no-op stub keeps chart components renderable in tests without asserting dimensions.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (typeof globalThis.ResizeObserver === "undefined") {
  (globalThis as unknown as { ResizeObserver: typeof ResizeObserverStub }).ResizeObserver =
    ResizeObserverStub;
}
