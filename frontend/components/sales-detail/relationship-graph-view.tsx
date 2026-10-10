"use client";

import { useMemo } from "react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { PermissionDenied } from "@/components/states/permission-denied";
import { Skeleton } from "@/components/ui/skeleton";
import type { Api } from "@/lib/api";
import type { DeletedSaleRow, PaymentRow, SaleItemRow } from "@/lib/api/types";
import { useApiData } from "@/lib/use-api-data";
import { buildRelationshipGraph } from "./relationship-graph";

/** The start of the Lightspeed data; the wide range is cheap thanks to the `sale_number` index. */
export const DATA_EPOCH = "2020-11-23";

/** The list endpoints' page-size cap; fetched whole so a large sale isn't silently truncated. */
const FETCH_SIZE = 200;

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

interface RelationshipData {
  payments: PaymentRow[];
  items: SaleItemRow[];
  deletedOrder: DeletedSaleRow | null;
  truncatedPayments: number;
  truncatedItems: number;
}

/** Loads one sale's tenders, line items, and (if voided) its deleted order. */
async function loadRelationship(api: Api, saleNumber: string): Promise<RelationshipData> {
  const to = todayIso();
  const [payments, items, deletedSales] = await Promise.all([
    api.listPayments({ saleNumber, from: DATA_EPOCH, to }, 0, FETCH_SIZE),
    api.listSaleItems({ saleNumber, from: DATA_EPOCH, to }, 0, FETCH_SIZE),
    api.listDeletedSales({ saleNumber, from: DATA_EPOCH, to }, 0, FETCH_SIZE),
  ]);
  return {
    payments: payments.items,
    items: items.items,
    deletedOrder: deletedSales.items[0] ?? null,
    truncatedPayments: Math.max(0, payments.total - payments.items.length),
    truncatedItems: Math.max(0, items.total - items.items.length),
  };
}

interface RelationshipGraphViewProps {
  saleNumber: string;
  /** Injected by tests; defaults to the demo/live singleton. */
  apiOverride?: Api;
}

/**
 * One sale as a hub-and-spokes graph: tenders and line items (plus a deleted-order node when
 * the sale was voided) hanging off the sale hub. Reuses the drill pattern from the invoice graph.
 */
export function RelationshipGraphView({ saleNumber, apiOverride }: RelationshipGraphViewProps) {
  const data = useApiData((api) => loadRelationship(api, saleNumber), [saleNumber], apiOverride);
  const graph = useMemo(
    () =>
      buildRelationshipGraph(
        saleNumber,
        data.data?.payments ?? [],
        data.data?.items ?? [],
        data.data?.deletedOrder ?? null,
        data.data?.truncatedPayments ?? 0,
        data.data?.truncatedItems ?? 0,
      ),
    [saleNumber, data.data],
  );

  if (data.loading) return <Skeleton className="h-[220px] w-full" />;

  if (data.error) {
    if (data.error.code === "NOT_PERMITTED") return <PermissionDenied subject="sale detail" />;
    return (
      <p role="alert" className="text-sm text-destructive">
        Couldn&apos;t load this sale. {data.error.message}
      </p>
    );
  }

  if (graph.columns.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No payments or line items recorded for sale {saleNumber}.
      </p>
    );
  }

  return (
    <FlowCanvas
      graph={graph}
      ariaLabel={`Sale ${saleNumber}: payments, line items, and deleted orders`}
      height={220}
    />
  );
}
