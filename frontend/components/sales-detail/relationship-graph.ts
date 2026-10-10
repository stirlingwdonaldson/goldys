import { CreditCard, Ellipsis, Package, Receipt, Trash2 } from "lucide-react";
import type { ColumnGraph, FlowTone, StepNodeData } from "@/components/flow/types";
import type { DeletedSaleRow, PaymentRow, SaleItemRow } from "@/lib/api/types";
import { formatCurrency } from "@/lib/format";

/**
 * The single sale as a hub-and-spokes graph: the sale in the first column, its payment
 * tenders, line items, and (when the sale was voided) its deleted order in the second.
 * The graph is empty only when there is nothing at all to show — a voided-only sale (a
 * deleted order with no surviving tenders or items) still renders the hub and its deleted
 * order.
 */
export function buildRelationshipGraph(
  saleNumber: string,
  payments: PaymentRow[],
  items: SaleItemRow[],
  deletedOrder: DeletedSaleRow | null,
  truncatedPayments = 0,
  truncatedItems = 0,
): ColumnGraph {
  const hasChildren =
    payments.length > 0 ||
    items.length > 0 ||
    deletedOrder != null ||
    truncatedPayments > 0 ||
    truncatedItems > 0;
  if (!hasChildren) {
    return { columns: [], edges: [] };
  }

  const hub: { id: string; data: StepNodeData } = {
    id: "sale",
    data: {
      title: `Sale ${saleNumber}`,
      icon: Receipt,
      tone: "info",
    },
  };

  const children: { id: string; data: StepNodeData }[] = [];

  payments.forEach((p, i) => {
    children.push({
      id: `pay:${i}`,
      data: {
        title: p.paymentTypeName ?? "(unnamed)",
        subtitle: formatCurrency(p.amount),
        icon: CreditCard,
        tone: "neutral",
      },
    });
  });

  items.forEach((it, i) => {
    // A line item reads "2 × $18.50" only when both quantity and sold price are present.
    const subtitle =
      it.quantitySold != null && it.soldPriceIncTax != null
        ? `${it.quantitySold} × ${formatCurrency(it.soldPriceIncTax)}`
        : undefined;
    children.push({
      id: `item:${i}`,
      data: {
        title: it.itemName ?? "(unnamed)",
        subtitle,
        icon: Package,
        tone: "neutral",
      },
    });
  });

  if (deletedOrder) {
    children.push({
      id: "deleted",
      data: {
        title: "Deleted order",
        subtitle: deletedOrder.note ?? formatCurrency(deletedOrder.totalIncTax),
        icon: Trash2,
        tone: "fail" as FlowTone,
      },
    });
  }

  // The list endpoints cap at a fixed page size; when a sale has more tenders or line
  // items than fit, signal the truncation with a trailing, non-drillable indicator node
  // so the graph never silently reads as complete.
  if (truncatedPayments > 0) {
    children.push({
      id: "truncated-payments",
      data: {
        title: `+${truncatedPayments} more tenders`,
        icon: Ellipsis,
        tone: "neutral",
      },
    });
  }
  if (truncatedItems > 0) {
    children.push({
      id: "truncated-items",
      data: {
        title: `+${truncatedItems} more line items`,
        icon: Ellipsis,
        tone: "neutral",
      },
    });
  }

  const edges: ColumnGraph["edges"] = children.map((c) => ({ source: "sale", target: c.id }));

  return { columns: [[hub], children], edges };
}
