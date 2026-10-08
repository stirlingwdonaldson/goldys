import { fetchApi } from "./client";
import type { Provenance } from "./types";

/**
 * Full provenance drill-down for a metric's resolved value on one date
 * (`GET /api/provenance/{metricId}/{date}`). Throws `ApiError` on failure, including
 * `NOT_PERMITTED` for a role without the metric's domain read.
 */
export async function getProvenance(metricId: string, date: string): Promise<Provenance> {
  return fetchApi<Provenance>(
    `/api/provenance/${encodeURIComponent(metricId)}/${encodeURIComponent(date)}`,
  );
}
