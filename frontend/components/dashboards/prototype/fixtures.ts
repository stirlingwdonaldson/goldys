// Synthetic fixtures, 26 October–1 November 2026. Never operational evidence.
export type Widget = { id: string; metric: string; title: string; width: "half" | "full"; display: "stat" | "trend" | "ranked" };
export type Draft = { title: string; description: string; period: string; comparison: boolean; visibility: string; widgets: Widget[] };

export const catalogue = [
  { metric: "sales", title: "Gross sales", source: "lightspeed", description: "Sales including GST, before refunds.", value: 92740, kind: "money" },
  { metric: "covers", title: "Booked covers", source: "opentable", description: "Booking party sizes; a proxy for attendance.", value: 1248, kind: "number" },
  { metric: "labour", title: "Labour cost / sales", source: "deputy", description: "Recorded wages as a share of gross sales. Restricted in the manager preview.", value: 0.284, kind: "percent" },
  { metric: "purchases", title: "Invoice purchases", source: "ctb", description: "Invoice purchases, not consumed food cost.", value: 21860, kind: "money" },
  { metric: "food", title: "Consumed food cost", source: "ctb", description: "Needs opening and closing stock. No closing stock in this fixture.", value: null, kind: "money" },
  { metric: "mix", title: "Top selling products", source: "lightspeed", description: "Ranked by gross sales, including GST.", value: 92740, kind: "money" },
] as const;

export const initialDraft: Draft = {
  title: "Weekly trading overview",
  description: "A Monday morning check-in on sales, bookings and costs.",
  period: "last-week", comparison: true, visibility: "Private",
  widgets: [
    { id: "sales", metric: "sales", title: "Gross sales", width: "half", display: "stat" },
    { id: "covers", metric: "covers", title: "Booked covers", width: "half", display: "stat" },
    { id: "trend", metric: "sales", title: "Sales through the week", width: "full", display: "trend" },
    { id: "labour", metric: "labour", title: "Labour cost / sales", width: "half", display: "stat" },
    { id: "food", metric: "food", title: "Consumed food cost", width: "half", display: "stat" },
  ],
};

export const variants = [
  { key: "A", name: "Canvas first", question: "Can editing the dashboard itself make layout decisions easier?", tradeoff: "Direct and visual · more controls around each widget" },
  { key: "B", name: "Report outline", question: "Is arranging a clear reading order easier than arranging a grid?", tradeoff: "A deliberate reading order · less freedom to compose the page" },
  { key: "C", name: "Guided setup", question: "Can a short sequence help occasional editors finish with confidence?", tradeoff: "One decision at a time · more steps for frequent editors" },
] as const;
