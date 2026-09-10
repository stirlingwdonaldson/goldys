// Mock data for the dashboard landing page. Nothing here is wired to the
// backend — real KPI/chart data depends on the raw log + canonical layer
// and the reconciliation engine, none of which exist yet (see
// docs/prd.md's Open Questions). This exists purely so the dashboard's
// visual shape (stat tiles, charts, exception list) can be reviewed before
// real data is available.

export const kpis = [
  {
    label: "Net sales (last 7 days)",
    value: "$48,236",
    delta: "+4.2% vs prior week",
    status: "success" as const,
  },
  {
    label: "Open exceptions",
    value: "7",
    delta: "3 new since yesterday",
    status: "warning" as const,
  },
  {
    label: "Ingestion completeness",
    value: "96%",
    delta: "1 source lagging",
    status: "warning" as const,
  },
  {
    label: "Labor cost %",
    value: "28.4%",
    delta: "-1.1pt vs target",
    status: "success" as const,
  },
]

export const salesTrend = [
  { day: "Mon", pos: 5120, bank: 5080 },
  { day: "Tue", pos: 4780, bank: 4790 },
  { day: "Wed", pos: 5340, bank: 5310 },
  { day: "Thu", pos: 6110, bank: 6050 },
  { day: "Fri", pos: 8920, bank: 8840 },
  { day: "Sat", pos: 9760, bank: 9760 },
  { day: "Sun", pos: 7204, bank: 7150 },
]

export const conflictsBySource = [
  { source: "Lightspeed", resolved: 18, open: 2 },
  { source: "Cooking the Books", resolved: 11, open: 3 },
  { source: "OpenTable", resolved: 9, open: 1 },
  { source: "Deputy", resolved: 14, open: 1 },
]

export const connectorStatus = [
  {
    name: "Lightspeed",
    role: "POS — sales & item mix",
    state: "healthy" as const,
    lastSync: "4 minutes ago",
  },
  {
    name: "Cooking the Books",
    role: "Wage & rostering data",
    state: "lagging" as const,
    lastSync: "6 hours ago",
  },
  {
    name: "OpenTable",
    role: "Covers & reservations",
    state: "healthy" as const,
    lastSync: "12 minutes ago",
  },
  {
    name: "Deputy",
    role: "Shift & timeclock data",
    state: "failed" as const,
    lastSync: "Failed 1 hour ago",
  },
]

export const recentExceptions = [
  {
    id: "EXC-1042",
    entity: "Shift · Sat, Jul 26 · FOH",
    field: "Hours worked",
    sources: "Deputy vs Lightspeed",
    severity: "warning" as const,
  },
  {
    id: "EXC-1041",
    entity: "Sale #48213 · Dinner service",
    field: "Tip amount",
    sources: "Lightspeed vs bank deposit",
    severity: "warning" as const,
  },
  {
    id: "EXC-1039",
    entity: "Shift · Fri, Jul 25 · BOH",
    field: "Wage rate",
    sources: "Cooking the Books",
    severity: "missing" as const,
  },
  {
    id: "EXC-1036",
    entity: "Reservation · Table 12",
    field: "Cover count",
    sources: "OpenTable vs Lightspeed",
    severity: "warning" as const,
  },
]
