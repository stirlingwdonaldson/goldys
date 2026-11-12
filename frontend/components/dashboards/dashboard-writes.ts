import type { QueryClient } from "@tanstack/react-query";
import type { Api, DashboardDocument, SaveDashboardInput } from "@/lib/api/types";

/** A successful dashboard write updates every cached view, regardless of its initiating screen. */
export function dashboardWrites(api: Pick<Api, "saveDashboard" | "updateDashboard" | "restoreDashboardRevision" | "createDashboardFromTemplate" | "toggleDashboardPin" | "deleteDashboard">, cache: QueryClient) {
  async function cancelReads(id: string) {
    // Invalidation alone reuses a pending initial request when no data is cached yet.
    // Cancel all affected reads so a pre-write response cannot become the refreshed result.
    await Promise.all([
      ["listDashboards"], ["getDashboard", id], ["renderDashboard", id], ["listDashboardRevisions", id],
    ].map(queryKey => cache.cancelQueries({ queryKey })));
  }

  async function changed(document: DashboardDocument) {
    await cancelReads(document.id);
    cache.setQueryData(["getDashboard", document.id], document);
    void cache.invalidateQueries({ queryKey: ["listDashboards"] });
    void cache.invalidateQueries({ queryKey: ["renderDashboard", document.id] });
    // Revision history is loaded on demand, so mark even disabled queries stale.
    void cache.invalidateQueries({ queryKey: ["listDashboardRevisions", document.id] });
    return document;
  }

  return {
    save: async (input: SaveDashboardInput, id?: string | null) =>
      changed(await (id ? api.updateDashboard(id, input) : api.saveDashboard(input))),
    restore: async (id: string, revision: number) => changed(await api.restoreDashboardRevision(id, revision)),
    createFromTemplate: async (id: string) => changed(await api.createDashboardFromTemplate(id)),
    togglePin: async (id: string) => changed(await api.toggleDashboardPin(id)),
    remove: async (id: string) => {
      await api.deleteDashboard(id);
      await cancelReads(id);
      for (const name of ["getDashboard", "renderDashboard", "listDashboardRevisions"]) {
        cache.removeQueries({ queryKey: [name, id] });
      }
      void cache.invalidateQueries({ queryKey: ["listDashboards"] });
    },
  };
}
