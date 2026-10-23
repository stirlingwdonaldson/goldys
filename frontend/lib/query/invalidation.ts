import type { QueryClient } from "@tanstack/react-query";

/** A changed source or resolution can change every business read, but not definitions/templates. */
const definitionReads = new Set([
  "listDashboardTemplates", "listMetricDefinitions", "listCanonicalEntities", "listResolvedDomains",
  "listQueryDatasets", "listQueryFields", "listQueryRelationships", "listForecastMetrics",
]);

export function invalidateBusinessReads(client: QueryClient) {
  return client.invalidateQueries({ predicate: query => !definitionReads.has(String(query.queryKey[0])) });
}
