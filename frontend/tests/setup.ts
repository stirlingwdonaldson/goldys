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

// Radix Select/DropdownMenu scroll the highlighted item into view on open; jsdom has no
// layout, so `scrollIntoView` is absent. A no-op keeps those controls renderable in tests.
if (typeof Element.prototype.scrollIntoView === "undefined") {
  Element.prototype.scrollIntoView = () => {};
}
