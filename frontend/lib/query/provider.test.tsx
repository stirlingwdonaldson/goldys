import { it, expect, vi, afterEach } from "vitest";
import { useQuery } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { DemoModeProvider, useDemoMode } from "@/lib/demo-mode";
import { QueryProvider } from "./provider";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }), usePathname: () => "/dashboard" }));
afterEach(() => window.localStorage.clear());

it("shares identical reads and keeps existing data after a failed background refresh", async () => {
  const read = vi.fn().mockResolvedValueOnce("known").mockRejectedValueOnce(new Error("offline"));
  function View() {
    const result = useQuery({ queryKey: ["same"], queryFn: read });
    return <div><span>{result.data}</span><button onClick={() => { void result.refetch(); }}>Refresh</button>{result.error ? <p>Refresh failed</p> : null}</div>;
  }
  render(<QueryProvider><View /><View /></QueryProvider>);
  await waitFor(() => expect(screen.getAllByText("known")).toHaveLength(2));
  expect(read).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getAllByText("Refresh")[0]);
  await waitFor(() => expect(screen.getAllByText("Refresh failed")).toHaveLength(2));
  expect(screen.getAllByText("known")).toHaveLength(2);
});

it("aborts a replaced query's transport", async () => {
  let oldSignal!: AbortSignal;
  function View({ id }: { id: string }) {
    const result = useQuery({ queryKey: ["record", id], queryFn: ({ signal }) => {
      if (id === "old") { oldSignal = signal; return new Promise<string>(() => {}); }
      return Promise.resolve("new result");
    } });
    return <p>{result.data}</p>;
  }
  const { rerender } = render(<QueryProvider><View id="old" /></QueryProvider>);
  await waitFor(() => expect(oldSignal).toBeDefined());
  rerender(<QueryProvider><View id="new" /></QueryProvider>);
  expect(oldSignal.aborted).toBe(true);
  expect(await screen.findByText("new result")).toBeInTheDocument();
});

it("clears cached data and aborts reads when demo/live scope changes", async () => {
  const { CurrentUserProvider, useCurrentUser } = await import("@/components/app-shell/current-user-provider");
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ displayName: "Owner", department: "ALL", seniority: "OWNER" }), { headers: { "Content-Type": "application/json" } })));
  let oldSignal!: AbortSignal;
  function View() {
    const { demo, setDemo } = useDemoMode();
    const { status } = useCurrentUser();
    const result = useQuery({ queryKey: ["sensitive"], queryFn: ({ signal }) => {
      if (demo) { oldSignal = signal; return new Promise<string>(() => {}); }
      return Promise.resolve("live scope");
    } });
    return <><button onClick={() => setDemo(false)}>Use live</button><p>{status}</p><p>{result.data}</p></>;
  }
  render(<CurrentUserProvider><DemoModeProvider><View /></DemoModeProvider></CurrentUserProvider>);
  await screen.findByText("authenticated");
  await waitFor(() => expect(oldSignal).toBeDefined());
  fireEvent.click(screen.getByText("Use live"));
  expect(oldSignal.aborted).toBe(true);
  expect(await screen.findByText("live scope")).toBeInTheDocument();
  vi.unstubAllGlobals();
});

it("drops cached business data during identity revalidation even for the same role", async () => {
  const { CurrentUserProvider, useCurrentUser } = await import("@/components/app-shell/current-user-provider");
  const { AuthGate } = await import("@/components/app-shell/auth-gate");
  let owner = "first owner";
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ displayName: owner, department: "ALL", seniority: "OWNER" }), { headers: { "Content-Type": "application/json" } })));
  function View() {
    const { refresh } = useCurrentUser();
    const result = useQuery({ queryKey: ["private-data"], queryFn: async () => owner });
    return <><button onClick={refresh}>Recheck identity</button><p>{result.data}</p></>;
  }
  render(<CurrentUserProvider><AuthGate><DemoModeProvider><View /></DemoModeProvider></AuthGate></CurrentUserProvider>);
  expect(await screen.findByText("first owner")).toBeInTheDocument();
  owner = "second owner";
  fireEvent.click(screen.getByText("Recheck identity"));
  expect(screen.queryByText("first owner")).not.toBeInTheDocument();
  expect(await screen.findByText("second owner")).toBeInTheDocument();
  vi.unstubAllGlobals();
});

it("does not start live reads when the demo-build auth fallback cannot verify a profile", async () => {
  const { CurrentUserProvider } = await import("@/components/app-shell/current-user-provider");
  const { AuthGate } = await import("@/components/app-shell/auth-gate");
  window.localStorage.setItem("goldys-demo-mode", "false");
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ code: "UNAVAILABLE", message: "Unavailable" }), { status: 503, headers: { "Content-Type": "application/json" } })));
  const read = vi.fn(async () => "private result");
  function View() {
    const result = useQuery({ queryKey: ["live-private"], queryFn: read });
    return <p>{result.fetchStatus}</p>;
  }
  render(<CurrentUserProvider><AuthGate><DemoModeProvider><View /></DemoModeProvider></AuthGate></CurrentUserProvider>);
  expect(await screen.findByText("idle")).toBeInTheDocument();
  expect(read).not.toHaveBeenCalled();
  vi.unstubAllGlobals();
});
