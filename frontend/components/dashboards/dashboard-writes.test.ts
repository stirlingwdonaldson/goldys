import { QueryClient, QueryObserver } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import type { DashboardDocument, SaveDashboardInput } from "@/lib/api/types";
import { dashboardWrites } from "./dashboard-writes";

const input: SaveDashboardInput = {
  title: "Updated dashboard", layout: "grid", widgets: [], visibility: "PRIVATE",
  filters: { dateRange: null, comparison: null, dimensions: [] },
};
const document: DashboardDocument = {
  ...input, id: "d1", schemaVersion: 2, description: null, layout: "grid",
  filters: input.filters!, visibility: "PRIVATE", pinned: false,
  createdBy: "owner", createdAt: "2026-11-12T00:00:00Z", updatedAt: "2026-11-12T01:00:00Z",
};

function setup() {
  const cache = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
  cache.setQueryData(["listDashboards"], [{ id: "d1", title: "Old" }]);
  for (const id of ["d1", "unrelated"]) {
    cache.setQueryData(["getDashboard", id], { ...document, id, title: "Old" });
    cache.setQueryData(["renderDashboard", id], ["old render"]);
    cache.setQueryData(["listDashboardRevisions", id], ["old revision"]);
  }
  const api = {
    saveDashboard: vi.fn(async () => document),
    updateDashboard: vi.fn(async () => document),
    restoreDashboardRevision: vi.fn(async () => document),
    createDashboardFromTemplate: vi.fn(async () => document),
    toggleDashboardPin: vi.fn(async () => document),
    deleteDashboard: vi.fn(async () => {}),
  };
  const writes = dashboardWrites(api, cache);
  return { api, cache, writes };
}

describe("dashboard writes", () => {
  it.each(["save", "update", "restore", "template", "pin"] as const)(
    "%s updates detail and makes the library, render and revision reads refresh", async (operation) => {
      const { writes, cache, api } = setup();
      const result = await ({
        save: () => writes.save(input),
        update: () => writes.save(input, "d1"),
        restore: () => writes.restore("d1", 2),
        template: () => writes.createFromTemplate("template"),
        pin: () => writes.togglePin("d1"),
      })[operation]();
      expect(result).toEqual(document);
      expect(cache.getQueryData(["getDashboard", "d1"])).toEqual(document);
      for (const key of [["listDashboards"], ["renderDashboard", "d1"], ["listDashboardRevisions", "d1"]]) {
        const read = vi.fn(async () => ["new result"]);
        expect(await cache.fetchQuery({ queryKey: key, queryFn: read })).toEqual(["new result"]);
        expect(read).toHaveBeenCalledOnce();
      }
      expect(cache.getQueryState(["renderDashboard", "unrelated"])?.isInvalidated).toBe(false);
      if (operation === "update") expect(api.updateDashboard).toHaveBeenCalledWith("d1", input);
      if (operation === "save") expect(api.saveDashboard).toHaveBeenCalledWith(input);
      cache.clear();
    },
  );

  it("keeps a successful write authoritative over an older in-flight detail read", async () => {
    const { writes, cache } = setup();
    let resolveRead!: (value: DashboardDocument) => void;
    const reading = cache.fetchQuery({ queryKey: ["getDashboard", "d1"], staleTime: 0,
      queryFn: () => new Promise<DashboardDocument>(resolve => { resolveRead = resolve; }),
    }).catch(() => undefined);
    await writes.save(input, "d1");
    resolveRead({ ...document, title: "Older response" });
    await reading;
    expect(cache.getQueryData(["getDashboard", "d1"])).toEqual(document);
    cache.clear();
  });

  it("removes deleted detail/render/history and refreshes the library", async () => {
    const { writes, cache, api } = setup();
    await writes.remove("d1");
    expect(api.deleteDashboard).toHaveBeenCalledWith("d1");
    for (const name of ["getDashboard", "renderDashboard", "listDashboardRevisions"]) {
      expect(cache.getQueryState([name, "d1"])).toBeUndefined();
      expect(cache.getQueryData([name, "unrelated"])).toBeDefined();
    }
    expect(cache.getQueryState(["listDashboards"])?.isInvalidated).toBe(true);
    cache.clear();
  });

  it("preserves cached data and propagates failed writes", async () => {
    const { writes, cache, api } = setup();
    const error = new Error("Not permitted");
    api.updateDashboard.mockRejectedValue(error);
    api.deleteDashboard.mockRejectedValue(error);
    await expect(writes.save(input, "d1")).rejects.toBe(error);
    await expect(writes.remove("d1")).rejects.toBe(error);
    expect(cache.getQueryData(["getDashboard", "d1"])).toMatchObject({ title: "Old" });
    expect(cache.getQueryState(["listDashboards"])?.isInvalidated).toBe(false);
    cache.clear();
  });
});

it.each([
  ["renderDashboard", "d1"], ["listDashboardRevisions", "d1"], ["listDashboards"],
])("replaces a pending initial %s read after a save", async (...queryKey) => {
  const { writes, cache } = setup();
  cache.removeQueries({ queryKey });
  let resolveOld!: (value: string[]) => void;
  let oldSignal!: AbortSignal;
  const read = vi.fn(({ signal }: { signal: AbortSignal }) => {
    if (read.mock.calls.length === 1) {
      oldSignal = signal;
      return new Promise<string[]>(resolve => { resolveOld = resolve; });
    }
    return Promise.resolve(["post-save result"]);
  });
  const observer = new QueryObserver(cache, { queryKey, queryFn: read });
  const unsubscribe = observer.subscribe(() => {});
  try {
    expect(read).toHaveBeenCalledOnce();
    expect(cache.getQueryData(queryKey)).toBeUndefined();
    await writes.save(input, "d1");
    expect(oldSignal.aborted).toBe(true);
    await vi.waitFor(() => expect(cache.getQueryData(queryKey)).toEqual(["post-save result"]));
    resolveOld(["pre-save result"]);
    await Promise.resolve();
    expect(cache.getQueryData(queryKey)).toEqual(["post-save result"]);
    expect(read).toHaveBeenCalledTimes(2);
  } finally {
    unsubscribe();
    cache.clear();
  }
});
