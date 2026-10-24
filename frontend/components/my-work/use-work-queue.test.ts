import { expect, it, vi } from "vitest";
import type { Api } from "@/lib/api";
import { loadWorkQueue } from "./use-work-queue";

function emptyApi(): Api {
  return {
    listReconciliationExceptions: vi.fn().mockResolvedValue({ items: [], total: 0 }),
    listProductExceptions: vi.fn().mockResolvedValue({ items: [], total: 0 }),
    listConnectorStatuses: vi.fn().mockResolvedValue([]),
    listInvoiceFlags: vi.fn().mockResolvedValue([]),
    getRecomputeStatus: vi.fn().mockResolvedValue({ state: "idle" }),
  } as unknown as Api;
}

it("reports failed checks independently of an empty work list", async () => {
  const api = emptyApi();
  vi.mocked(api.listConnectorStatuses).mockRejectedValue(new Error("Unavailable"));
  const result = await loadWorkQueue(api);
  expect(result.items).toEqual([]);
  expect(result.unavailable).toEqual(["data feed status"]);
  expect(result.truncated).toEqual([]);
});

it("discloses server totals beyond the bounded pages without expanding the read", async () => {
  const api = emptyApi();
  vi.mocked(api.listReconciliationExceptions).mockResolvedValue({ items: [], total: 201, page: 0, size: 200 });
  vi.mocked(api.listProductExceptions).mockResolvedValue({ items: [], total: 202, page: 0, size: 200 });
  const result = await loadWorkQueue(api);
  expect(result.truncated).toEqual(["daily-sales decisions", "product-sales decisions"]);
  expect(api.listReconciliationExceptions).toHaveBeenCalledTimes(1);
  expect(api.listReconciliationExceptions).toHaveBeenCalledWith(0, 200, { signal: undefined });
});
