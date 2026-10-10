"use client";

import { useMemo } from "react";
import type { Api } from "@/lib/api";
import { useApiData } from "@/lib/use-api-data";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { buildSettlementGraph, buildSettlementInput, type SettlementInput } from "./settlement-graph";

/** Loads the three mix/totals endpoints and aggregates them into the graph's input. */
async function loadSettlement(api: Api, from: string, to: string): Promise<SettlementInput> {
  const [saleItemMix, paymentMix, deletedSaleTotals] = await Promise.all([
    api.getSaleItemMix(from, to),
    api.getPaymentMix(from, to),
    api.getDeletedSaleTotals(from, to),
  ]);
  return buildSettlementInput({ saleItemMix, paymentMix, deletedSaleTotals });
}

/**
 * The daily settlement as a four-column node graph (items rung → sales → tenders → settled),
 * scoped to the same date range as the rest of the Sales detail screen. All three figures come
 * from the resolved mix/totals endpoints, so the diagram shows the reconciled numbers — never a
 * client-side guess.
 */
export function SettlementFlowView({
  from,
  to,
  apiOverride,
}: {
  from: string;
  to: string;
  apiOverride?: Api;
}) {
  const input = useApiData((api) => loadSettlement(api, from, to), [from, to], apiOverride);
  const graph = useMemo(() => (input.data ? buildSettlementGraph(input.data) : null), [input.data]);

  if (input.loading) return <Skeleton className="h-[360px] w-full" />;
  if (input.error) {
    if (input.error.code === "NOT_PERMITTED") return <PermissionDenied subject="settlement" />;
    return (
      <ErrorState
        title="Couldn't load the settlement"
        message={input.error.message}
        onRetry={input.reload}
      />
    );
  }
  if (!input.data || !graph) return null;

  const empty =
    input.data.itemsRung.length === 0 &&
    input.data.tenders.length === 0 &&
    input.data.deletedOrders.count === 0;
  if (empty) {
    return (
      <EmptyState
        title="No settlement data"
        description="Items rung and tenders will appear here once sales ingest for this period."
      />
    );
  }

  const tallest = Math.max(...graph.columns.map((c) => c.length));
  return (
    <FlowCanvas
      graph={graph}
      ariaLabel="Daily settlement: items rung to sales, tenders, and settled payments"
      height={Math.min(480, Math.max(300, tallest * 72 + 96))}
    />
  );
}
