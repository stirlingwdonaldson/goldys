import type { NetworkEdgeDto, NetworkGraphDto, NetworkNodeDto } from "./types";

/**
 * Demo platform map. Mirrors the real catalogue's shape, with the hard states on show: CTB's last
 * run failed, OpenTable is stale and Deputy has never run, so the blast radius is visible.
 */
const SOURCES: [id: string, label: string, tone: string, subtitle: string, domains: string[]][] = [
  ["LIGHTSPEED", "Lightspeed", "ok", "Up to date", ["resolved_daily_sales", "resolved_product_sales", "resolved_payment_day", "resolved_sale_item_day"]],
  ["CTB", "Cooking the Books", "fail", "Last run failed", ["resolved_daily_sales", "resolved_product_sales", "resolved_inventory_day"]],
  ["OPENTABLE", "OpenTable", "warn", "Stale", ["resolved_reservation_day"]],
  ["DEPUTY", "Deputy", "missing", "Never run", ["resolved_labour_day"]],
];

const DOMAIN_LABELS: Record<string, string> = {
  resolved_daily_sales: "Daily sales",
  resolved_product_sales: "Product sales",
  resolved_payment_day: "Payment",
  resolved_sale_item_day: "Sale item",
  resolved_inventory_day: "Inventory",
  resolved_reservation_day: "Reservation",
  resolved_labour_day: "Labour",
};

// [metric id, name, formula, domain or inputs, permission]
const BASE: [string, string, string, string, string][] = [
  ["sales.gross", "Gross sales", "totalSales", "resolved_daily_sales", "reconciliation.sales"],
  ["sales.net", "Net sales", "netTotal", "resolved_daily_sales", "reconciliation.sales"],
  ["product.sales_amount", "Product sales amount", "amount", "resolved_product_sales", "reconciliation.sales"],
  ["payments.amount", "Payments taken", "amount", "resolved_payment_day", "payments.metrics"],
  ["sale_items.amount", "Items sold amount", "amount", "resolved_sale_item_day", "sale-items.metrics"],
  ["inventory.purchases", "Purchases (COGS)", "purchases", "resolved_inventory_day", "inventory.cost"],
  ["inventory.wastage", "Wastage", "wastage", "resolved_inventory_day", "inventory.cost"],
  ["reservations.covers", "Covers", "covers", "resolved_reservation_day", "reservations.metrics"],
  ["reservations.bookings", "Bookings", "bookings", "resolved_reservation_day", "reservations.metrics"],
  ["labour.cost", "Labour cost", "actualCost", "resolved_labour_day", "labour.cost"],
  ["labour.actual_hours", "Actual hours", "actualHours", "resolved_labour_day", "labour.hours"],
];

const DERIVED: [string, string, string, string[], string][] = [
  ["sales.average_spend_per_cover", "Average spend per cover", "sales.gross ÷ reservations.covers", ["sales.gross", "reservations.covers"], "reconciliation.sales"],
  ["labour.cost_per_cover", "Cost per cover", "labour.cost ÷ reservations.covers", ["labour.cost", "reservations.covers"], "labour.cost"],
  ["inventory.food_cost_percent", "Food cost %", "inventory.purchases ÷ sales.gross", ["inventory.purchases", "sales.gross"], "inventory.cost"],
  ["labour.foh_percent", "FOH labour cost %", "FOH labour.cost ÷ sales.gross", ["labour.cost", "sales.gross"], "labour.cost"],
];

const DASHBOARDS: [id: string, title: string, uses: Record<string, number>][] = [
  ["demo-weekly", "Weekly trading", { "sales.gross": 2, "sales.average_spend_per_cover": 1, "reservations.covers": 1 }],
  ["demo-kitchen", "Kitchen costs", { "inventory.food_cost_percent": 1, "inventory.wastage": 1 }],
];

const node = (
  id: string,
  kind: string,
  label: string,
  subtitle: string | null,
  size: number,
  tone = "neutral",
  href: string | null = null,
): NetworkNodeDto => ({ id, kind, label, subtitle, tone, size, group: kind, href });

const edge = (source: string, target: string, weight: number, label: string): NetworkEdgeDto => ({
  source,
  target,
  weight,
  label,
});

export function demoPlatformMap(): NetworkGraphDto {
  const nodes: NetworkNodeDto[] = [];
  const edges: NetworkEdgeDto[] = [];
  const permissions = new Set<string>();

  for (const [id, label, tone, subtitle, domains] of SOURCES) {
    nodes.push(node(`source:${id}`, "source", label, subtitle, 6, tone, "/logs"));
    for (const domain of domains) edges.push(edge(`source:${id}`, `domain:${domain}`, 1, "feeds"));
  }
  for (const [domain, label] of Object.entries(DOMAIN_LABELS)) {
    nodes.push(node(`domain:${domain}`, "domain", label, domain, 5));
  }
  for (const [id, name, formula, domain, permission] of BASE) {
    nodes.push(node(`metric:${id}`, "metric", name, formula, 3));
    edges.push(edge(`domain:${domain}`, `metric:${id}`, 1, "reads"));
    edges.push(edge(`metric:${id}`, `permission:${permission}`, 1, "requires"));
    permissions.add(permission);
  }
  for (const [id, name, formula, inputs, permission] of DERIVED) {
    nodes.push(node(`metric:${id}`, "derived-metric", name, formula, 3));
    for (const input of inputs) edges.push(edge(`metric:${id}`, `metric:${input}`, 1, "computed from"));
    edges.push(edge(`metric:${id}`, `permission:${permission}`, 1, "requires"));
    permissions.add(permission);
  }
  for (const permission of permissions) {
    nodes.push(node(`permission:${permission}`, "permission", permission, "Permission resource", 2));
  }
  for (const [id, title, uses] of DASHBOARDS) {
    const total = Object.values(uses).reduce((a, b) => a + b, 0);
    nodes.push(node(`dashboard:${id}`, "dashboard", title, `${Object.keys(uses).length} widgets`, 4, "neutral", "/dashboards"));
    for (const [metric, count] of Object.entries(uses)) {
      edges.push(edge(`dashboard:${id}`, `metric:${metric}`, count / total, "shows"));
    }
  }
  return { nodes, edges };
}
