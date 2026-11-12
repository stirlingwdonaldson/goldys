"use client";

import { useMemo } from "react";
import type { Api } from "@/lib/api";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { buildSettlementGraph, buildSettlementInput, type SettlementInput } from "./settlement-graph";

/** Loads the three mix/totals endpoints and aggregates them into the graph's input. */
async function loadSettlement(api: Api, from: string, to: string, signal?: AbortSignal): Promise<SettlementInput> {
  const [saleItemMix, paymentMix, deletedSaleTotals] = await Promise.all([
    api.getSaleItemMix(from, to, { signal }),
    api.getPaymentMix(from, to, { signal }),
    api.getDeletedSaleTotals(from, to, { signal }),
  ]);
  return buildSettlementInput({ saleItemMix, paymentMix, deletedSaleTotals });
}

/** Date-scoped comparison of resolved merchandise totals and POS payment amounts. */
export function SettlementFlowView({
  from,
  to,
  apiOverride,
}: {
  from: string;
  to: string;
  apiOverride?: Api;
}) {
  const queryApi = useApi();
  const input = useQuery({ queryKey: ["settlement-flow-view", from, to], queryFn: ({ signal }) => loadSettlement(apiOverride ?? queryApi, from, to, signal) });
  const graph = useMemo(() => (input.data ? buildSettlementGraph(input.data) : null), [input.data]);

  if (input.isPending) return <Skeleton className="h-[360px] w-full" />;
  if (input.isLoadingError) {
    if (input.error.code === "NOT_PERMITTED") return <PermissionDenied subject="sales and payments" />;
    return (
      <ErrorState
        title="Couldn't load sales and payments"
        message={input.error.message}
        onRetry={input.refetch}
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
        title="No sales or payment data"
        description="Items rung and tenders will appear here once sales ingest for this period."
      />
    );
  }

  const tallest = Math.max(...graph.columns.map((c) => c.length));
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted-foreground">
        Sales use the date the sale closed; POS payments use the payment date. Later payments or refunds can fall in another period. Deleted orders are shown separately.
      </p>
      {input.data.hasCoverageGap ? <p role="status" className="text-sm text-status-conflict">Sales and payments cover different dates. Missing dates have not been treated as zero.</p> : null}
      {input.data.hasConflict ? <p role="status" className="text-sm text-status-conflict">Source conflicts remain in this period; matching totals do not resolve them.</p> : null}
      <dl aria-label="Sales and payment totals" className="flex flex-wrap gap-x-8 gap-y-3 text-sm tabular-nums">
        <div>
          <dt className="text-muted-foreground">Merchandise sales</dt>
          <dd className="font-semibold">{graph.columns[1][0].data.subtitle}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">POS payments</dt>
          <dd className="font-semibold">{graph.columns[3][0].data.subtitle}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Comparison</dt>
          <dd className="font-semibold">{graph.columns[3][0].data.detail}</dd>
        </div>
      </dl>
      <FlowCanvas
        graph={graph}
        ariaLabel="Merchandise sales compared with POS payments"
        height={Math.min(480, Math.max(300, tallest * 72 + 96))}
      />
    </div>
  );
}
