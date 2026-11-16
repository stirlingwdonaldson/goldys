import { describe, expect, it, vi } from "vitest";
import { createWorkspaceDemo } from "./workspace-demo";

describe("private workspace files", () => {
  it("keeps demo uploads offline and enforces deletion", async () => {
    vi.useFakeTimers();
    const network = vi.spyOn(globalThis, "fetch");
    const api = createWorkspaceDemo();
    const id = "10000000-0000-4000-8000-000000000001";
    try {
      const upload = api.uploadWorkspaceFile(id, new File(["sales\n42\n"], "sales.csv"));
      await vi.runAllTimersAsync(); const file = await upload;
      const keep = api.keepWorkspaceFile(id, file.id, true); await vi.runAllTimersAsync(); await keep;
      const list = api.listWorkspaceFiles(id); await vi.runAllTimersAsync(); expect((await list)[0].kept).toBe(true);
      const remove = api.deleteWorkspaceFile(id, file.id); await vi.runAllTimersAsync(); await remove;
      const after = api.listWorkspaceFiles(id); await vi.runAllTimersAsync(); expect((await after)[0].available).toBe(false);
      expect(network).not.toHaveBeenCalled();
    } finally { network.mockRestore(); vi.useRealTimers(); }
  });
});
