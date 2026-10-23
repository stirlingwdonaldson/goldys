import { vi } from "vitest";
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
// Node-environment tests (route/upload) have no `Element` global, so guard that too.
if (typeof Element !== "undefined" && typeof Element.prototype.scrollIntoView === "undefined") {
  Element.prototype.scrollIntoView = () => {};
}


// Exercise native Query observers in component tests, with a fresh non-retrying cache per render.
vi.mock("@testing-library/react", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@testing-library/react")>();
  const { QueryClient, QueryClientProvider } = await vi.importActual<typeof import("@tanstack/react-query")>("@tanstack/react-query");
  const { createElement, useState } = await import("react");
  function Wrapper({ children }: { children: import("react").ReactNode }) {
    const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } }));
    return createElement(QueryClientProvider, { client }, children);
  }
  return { ...actual,
    render: (ui: import("react").ReactNode, options?: import("@testing-library/react").RenderOptions) => actual.render(ui, { wrapper: Wrapper, ...options }),
    renderHook: (hook: (props: unknown) => unknown, options?: import("@testing-library/react").RenderHookOptions<unknown>) => actual.renderHook(hook, { wrapper: Wrapper, ...options }),
  };
});
