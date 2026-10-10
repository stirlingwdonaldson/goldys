import {
  AlertTriangle,
  Banknote,
  Beef,
  Bell,
  BookOpen,
  Bot,
  Boxes,
  CalendarCheck,
  CalendarDays,
  CheckCircle2,
  ChefHat,
  Clock,
  CloudSun,
  ClipboardCheck,
  Cog,
  Database,
  FileCheck,
  FileText,
  Filter,
  GitBranch,
  GitMerge,
  Hourglass,
  Inbox,
  KeyRound,
  LayoutGrid,
  ListChecks,
  MessageSquare,
  Package,
  PartyPopper,
  Percent,
  Plug,
  Receipt,
  Scale,
  Search,
  ShieldCheck,
  ShoppingCart,
  Sigma,
  Sparkles,
  StopCircle,
  Store,
  Timer,
  TrendingUp,
  Truck,
  UserCheck,
  Users,
  Utensils,
  Warehouse,
  Wrench,
  Zap,
  type LucideIcon,
} from "lucide-react";
import type { ColumnGraph, FlowTone } from "@/components/flow/types";

/**
 * The Flow lab: concept diagrams for where node graphs earn their place in Goldy's
 * (dependencies, conditional processes, lineage). Every figure here is illustrative
 * design data, not venue data; the live graphs (Data health pipeline, provenance,
 * invoice graph) are linked from the diagrams they prototype.
 */

export type DiagramMode = "Explore" | "Configure" | "Inspect";
export type DiagramAudience = "Owner & GM" | "Venue manager" | "Kitchen manager" | "Front of house" | "Developer";

export interface ConceptNode {
  id: string;
  title: string;
  icon: LucideIcon;
  tone: FlowTone;
  subtitle?: string;
  detail?: string;
  /** What the inspector says about this node: what it is and why it matters. */
  note?: string;
  emphasis?: boolean;
}

export interface ConceptEdge {
  source: string;
  target: string;
  label?: string;
  tone?: FlowTone;
}

export interface ConceptDiagram {
  id: string;
  title: string;
  /** The question a manager would open this to answer. */
  question: string;
  audience: DiagramAudience[];
  mode: DiagramMode;
  summary: string;
  columns: ConceptNode[][];
  edges: ConceptEdge[];
  /** Where a working version of this idea already exists in the app. */
  live?: { href: string; label: string };
}

function n(
  id: string,
  icon: LucideIcon,
  tone: FlowTone,
  title: string,
  subtitle?: string,
  note?: string,
  extra: Partial<ConceptNode> = {},
): ConceptNode {
  return { id, icon, tone, title, subtitle, note, ...extra };
}

function e(source: string, target: string, label?: string, tone?: FlowTone): ConceptEdge {
  return { source, target, label, tone };
}

/** Connect every source to every target (fan-out / fan-in between two stages). */
function all(sources: string[], targets: string[], tone?: FlowTone): ConceptEdge[] {
  return sources.flatMap((s) => targets.map((t) => e(s, t, undefined, tone)));
}

export const DIAGRAMS: ConceptDiagram[] = [
  {
    id: "explore-dependencies",
    title: "Explore dependencies",
    question: "If beef mince goes up 9%, what does it touch?",
    audience: ["Kitchen manager", "Owner & GM"],
    mode: "Explore",
    summary:
      "Start at any ingredient, supplier or metric and walk downstream to everything that depends on it. The reverse lookup (\"what uses this?\") is the part spreadsheets can't do.",
    columns: [
      [n("sup", Truck, "neutral", "Southside Meats", "Supplier · 3 deliveries/wk", "The supplier whose latest invoice carried the price change.")],
      [n("ing", Beef, "warn", "Beef mince", "$9.00 → $9.81/kg (+9%)", "Ingredient price from the latest invoice line, compared with the previous four invoices.", { emphasis: true })],
      [
        n("sub1", Package, "warn", "Burger patty", "Sub-recipe · 180 g", "Component made in-house; cost rises $0.15 per patty."),
        n("sub2", Package, "warn", "Bolognese sauce", "Sub-recipe · 5 L batch", "Batch cost rises $3.40."),
      ],
      [
        n("m1", Utensils, "warn", "Classic burger", "GP 72% → 71.4%", "Sells about 240 a week."),
        n("m2", Utensils, "warn", "Cheeseburger", "GP 70% → 69.3%"),
        n("m3", Utensils, "warn", "Spaghetti bolognese", "GP 74% → 73.1%"),
        n("m4", Utensils, "neutral", "Lasagne special", "Weekend only", "Low volume; included so nothing downstream is hidden."),
      ],
      [
        n("k1", Percent, "warn", "Food GP%", "−0.4 pts est.", "Estimated from last four weeks' sales mix. An estimate, not an accounting figure."),
        n("k2", Banknote, "info", "Revenue exposed", "$4,820 / week", "Weekly sales of every item that uses beef mince."),
      ],
    ],
    edges: [
      e("sup", "ing", "supplies"),
      e("ing", "sub1", undefined, "warn"),
      e("ing", "sub2", undefined, "warn"),
      e("sub1", "m1", undefined, "warn"),
      e("sub1", "m2", undefined, "warn"),
      e("sub2", "m3", undefined, "warn"),
      e("sub2", "m4"),
      ...all(["m1", "m2", "m3", "m4"], ["k1"]),
      ...all(["m1", "m2", "m3", "m4"], ["k2"]),
    ],
    live: { href: "/kitchen", label: "Kitchen invoice graph" },
  },
  {
    id: "build-automations",
    title: "Build automations",
    question: "Tell me when a supplier charges more than we agreed.",
    audience: ["Kitchen manager", "Venue manager", "Owner & GM"],
    mode: "Configure",
    summary:
      "A small, typed set of nodes (trigger, data, condition, action, wait) that managers connect into a rule. The graph is both the editor and the plain-English explanation of what the rule will do.",
    columns: [
      [n("t", Zap, "info", "Invoice received", "Trigger", "Fires when a supplier invoice is ingested and validated.")],
      [n("d", Search, "neutral", "Look up agreed price", "Data · last 90 days", "Compares like-for-like units (per kg, per carton) against the approved reference price.")],
      [n("c", GitBranch, "warn", "Increase > 8%?", "Condition", "Threshold is editable. The branch taken is recorded on every run.")],
      [
        n("a1", Inbox, "warn", "Create item in My work", "Action · Kitchen manager", "Lands in the kitchen manager's queue with the invoice line, price history and affected dishes attached."),
        n("a2", StopCircle, "neutral", "Record and stop", "No action", "Still logged, so you can see the rule is running."),
      ],
      [n("w", Hourglass, "neutral", "Wait 2 business days", "Control")],
      [n("x", Bell, "fail", "Escalate to GM", "Action · if still open", "Only fires if the item hasn't been resolved.")],
    ],
    edges: [e("t", "d"), e("d", "c"), e("c", "a1", "Yes", "warn"), e("c", "a2", "No"), e("a1", "w"), e("w", "x", "unresolved", "fail")],
  },
  {
    id: "inspect-execution",
    title: "Inspect execution",
    question: "Why didn't the GM get the escalation for INV-1042?",
    audience: ["Developer", "Owner & GM"],
    mode: "Inspect",
    summary:
      "The same automation, replayed for one run: which branch was taken, what each step saw, how long it took and where it failed. Read-only and auditable.",
    columns: [
      [n("r0", Zap, "ok", "Invoice INV-1042", "Fired 8 Oct, 6:30 pm", "Run #4821 of \"Supplier price monitoring\" v3.")],
      [n("r1", Search, "ok", "Price lookup", "12 ms · 4 prior invoices")],
      [n("r2", GitBranch, "warn", "Increase > 8%?", "+11.4% → Yes", "Chicken breast $12.50 vs agreed $11.22 per carton.")],
      [n("r3", Inbox, "ok", "Item created", "Assigned to kitchen manager")],
      [n("r4", Hourglass, "ok", "Waited 2 days", "Still open on 10 Oct")],
      [
        n("r5", Bell, "fail", "Escalate to GM", "Failed · email bounced", "Provider returned 550 mailbox unavailable. The in-app notice was still delivered."),
      ],
      [n("r6", Timer, "info", "Retry queued", "Next attempt 10:15 am")],
    ],
    edges: [e("r0", "r1"), e("r1", "r2"), e("r2", "r3", "Yes", "warn"), e("r3", "r4"), e("r4", "r5"), e("r5", "r6", "retry", "fail")],
  },
  {
    id: "connector-pipeline",
    title: "Connector pipeline map",
    question: "Which sources are delivering, and what do they feed?",
    audience: ["Developer", "Owner & GM"],
    mode: "Explore",
    summary:
      "Every source, how it arrives, and the layers it flows through: raw ledger, canonical facts, reconciliation, resolved views. A broken source shows exactly which reports go stale.",
    columns: [
      [
        n("s1", Plug, "ok", "Lightspeed", "Scheduled-report webhook"),
        n("s2", Plug, "warn", "Cooking the Books", "AJAX pull + SFTP drop", "Last run partial: 3 invoice PDFs missing."),
        n("s3", Plug, "missing", "OpenTable", "Manual CSV drop", "No file uploaded this week."),
        n("s4", Plug, "fail", "Deputy", "Webhook · raw only", "Signature check failing since 19 Sep."),
      ],
      [n("raw", Database, "ok", "Raw ledger", "Immutable · 182,410 records", "Every payload stored byte-for-byte with its SHA-256.")],
      [
        n("c1", Boxes, "ok", "Sales & sale items", "Canonical"),
        n("c2", Boxes, "warn", "Invoices & lines", "Canonical"),
        n("c3", Boxes, "missing", "Covers", "Canonical"),
      ],
      [n("rec", GitMerge, "warn", "Reconciliation", "4 open decisions")],
      [
        n("v1", CheckCircle2, "ok", "Daily sales", "Resolved"),
        n("v2", CheckCircle2, "warn", "Food cost", "Resolved · partial"),
        n("v3", CheckCircle2, "missing", "Reservations", "Not received"),
      ],
    ],
    edges: [
      e("s1", "raw"),
      e("s2", "raw", undefined, "warn"),
      e("s3", "raw", undefined, "missing"),
      e("s4", "raw", undefined, "fail"),
      e("raw", "c1"),
      e("raw", "c2", undefined, "warn"),
      e("raw", "c3", undefined, "missing"),
      ...all(["c1", "c2", "c3"], ["rec"]),
      e("rec", "v1"),
      e("rec", "v2", undefined, "warn"),
      e("rec", "v3", undefined, "missing"),
    ],
    live: { href: "/data-health", label: "Live pipeline in Data health" },
  },
  {
    id: "metric-lineage",
    title: "Metric lineage",
    question: "Where does last week's labour % actually come from?",
    audience: ["Owner & GM", "Developer"],
    mode: "Explore",
    summary:
      "From a figure on a dashboard back through its definition, its inputs and the source records, with each input's trust state. Answers \"can I believe this number?\" in one view.",
    columns: [
      [
        n("l1", Plug, "ok", "Deputy timesheets", "412 shifts"),
        n("l2", KeyRound, "ok", "Award rates", "Effective 1 Jul"),
        n("l3", Plug, "ok", "Lightspeed sales", "7 daily reports"),
      ],
      [n("l4", Users, "ok", "Worked hours", "Canonical shifts"), n("l5", TrendingUp, "ok", "Net sales", "Resolved · verified")],
      [n("l6", Sigma, "info", "Labour cost", "Derived · $22,140", "Hours × rate + on-costs. Derived, so marked as an estimate until payroll confirms.")],
      [n("l7", Percent, "info", "Labour %", "26.2% · definition v3", "Labour cost ÷ net sales, excluding GST. Definition changed on 1 Aug (v2 excluded on-costs).", { emphasis: true })],
      [n("l8", LayoutGrid, "neutral", "Home KPI tile"), n("l9", FileText, "neutral", "Weekly owner pack"), n("l10", Bot, "neutral", "Ask Goldy's answers")],
    ],
    edges: [e("l1", "l4"), e("l2", "l6"), e("l3", "l5"), e("l4", "l6"), e("l6", "l7", "numerator"), e("l5", "l7", "denominator"), ...all(["l7"], ["l8", "l9", "l10"])],
    live: { href: "/sales", label: "Provenance on Sales figures" },
  },
  {
    id: "entity-resolution",
    title: "Entity resolution",
    question: "Two systems disagree on Tuesday's sales. Which one wins, and why?",
    audience: ["Owner & GM", "Developer"],
    mode: "Explore",
    summary:
      "How one business fact is resolved from several sources: matching, the disagreement, the rule or decision applied, and the value every report then uses.",
    columns: [
      [n("e1", Store, "neutral", "Lightspeed", "Net sales $12,410"), n("e2", Store, "neutral", "Cooking the Books", "Net sales $12,180")],
      [n("e3", Filter, "ok", "Match", "Same venue · Tue 7 Oct", "Matched on venue and business date (see matching-and-identity.md).")],
      [n("e4", Scale, "warn", "Disagreement", "Δ $230 (1.9%)", "Above the $5 tolerance for daily net sales.")],
      [n("e5", ShieldCheck, "info", "Rule: Lightspeed authoritative", "Set by GM · 12 Aug", "Standing rule for net sales. A manual override would take precedence and is recorded with a reason.")],
      [n("e6", CheckCircle2, "info", "Resolved net sales", "$12,410 · resolved by rule", undefined, { emphasis: true })],
      [n("e7", LayoutGrid, "neutral", "Reports & dashboards", "Show it as resolved")],
    ],
    edges: [e("e1", "e3"), e("e2", "e3"), e("e3", "e4", undefined, "warn"), e("e4", "e5", undefined, "info"), e("e5", "e6", undefined, "info"), e("e6", "e7")],
    live: { href: "/reconciliation", label: "Reconciliation" },
  },
  {
    id: "schema-map",
    title: "Data schema relationship map",
    question: "How do suppliers, stock, recipes and sales connect?",
    audience: ["Developer"],
    mode: "Explore",
    summary:
      "The canonical entities and how they relate. Useful when adding a source or a domain: you can see what a new entity has to join to before writing the migration.",
    columns: [
      [n("sp", Truck, "neutral", "Supplier"), n("st", Users, "neutral", "Staff member"), n("gu", CalendarDays, "neutral", "Guest")],
      [n("in", Receipt, "neutral", "Invoice"), n("sh", Clock, "neutral", "Shift"), n("rs", CalendarCheck, "neutral", "Reservation")],
      [n("il", FileText, "neutral", "Invoice line"), n("ts", ClipboardCheck, "missing", "Timesheet", "Raw only today")],
      [n("si", Warehouse, "neutral", "Stock item")],
      [n("ri", BookOpen, "missing", "Recipe", "Planned")],
      [n("mi", Utensils, "neutral", "Menu item")],
      [n("sl", ShoppingCart, "neutral", "Sale item"), n("cv", Users, "neutral", "Covers")],
      [n("sa", Receipt, "neutral", "Sale"), n("py", Banknote, "neutral", "Payment")],
    ],
    edges: [
      e("sp", "in", "1:n"),
      e("in", "il", "1:n"),
      e("il", "si", "n:1"),
      e("si", "ri", "n:m", "missing"),
      e("ri", "mi", "1:1", "missing"),
      e("mi", "sl", "1:n"),
      e("sl", "sa", "n:1"),
      e("sa", "py", "1:n"),
      e("st", "sh", "1:n"),
      e("sh", "ts", "1:1", "missing"),
      e("gu", "rs", "1:n"),
      e("rs", "cv", "1:1"),
    ],
    live: { href: "/data", label: "Data explorer" },
  },
  {
    id: "recipe-impact",
    title: "Recipe impact explorer",
    question: "What does a Classic burger cost us to make right now?",
    audience: ["Kitchen manager"],
    mode: "Explore",
    summary:
      "A dish broken into components and ingredients with current costs, so a price change or substitution shows its effect on the plate and on GP straight away.",
    columns: [
      [
        n("i1", Beef, "warn", "Beef mince", "$9.81/kg · 180 g"),
        n("i2", Package, "ok", "Brioche bun", "$0.62 each"),
        n("i3", Package, "ok", "Cheddar", "$14.20/kg · 20 g"),
        n("i4", Package, "ok", "Burger sauce", "House made"),
        n("i5", Package, "ok", "Chips", "$2.10/kg · 200 g"),
      ],
      [n("p1", ChefHat, "warn", "Patty", "$1.92"), n("p2", ChefHat, "ok", "Bun & build", "$1.24"), n("p3", ChefHat, "ok", "Side of chips", "$0.62")],
      [n("dish", Utensils, "info", "Classic burger", "Plate cost $3.78", "Menu price $26.00 inc. GST.", { emphasis: true })],
      [n("gp", Percent, "warn", "GP 84.0%", "Target 85%", "Below target since the beef price change.")],
    ],
    edges: [e("i1", "p1", undefined, "warn"), e("i2", "p2"), e("i3", "p2"), e("i4", "p2"), e("i5", "p3"), e("p1", "dish", undefined, "warn"), e("p2", "dish"), e("p3", "dish"), e("dish", "gp", undefined, "warn")],
    live: { href: "/recipes", label: "Recipes (coming soon)" },
  },
  {
    id: "purchase-approval",
    title: "Purchase approval flow",
    question: "Who has to sign off on a $1,400 equipment order?",
    audience: ["Kitchen manager", "Venue manager", "Owner & GM"],
    mode: "Configure",
    summary:
      "Approval limits as a picture: thresholds, approvers and what happens after. Owners set it once; everyone can see why an order is waiting and on whom.",
    columns: [
      [n("q1", ShoppingCart, "info", "Purchase request", "Kitchen · $1,400")],
      [n("q2", GitBranch, "neutral", "Amount?", "Threshold check")],
      [
        n("q3", CheckCircle2, "ok", "Auto-approve", "Under $500"),
        n("q4", UserCheck, "warn", "Venue manager", "$500 to $2,000 · waiting 1 day"),
        n("q5", UserCheck, "neutral", "GM / owner", "Over $2,000"),
      ],
      [n("q6", Truck, "neutral", "Order placed", "With supplier")],
      [n("q7", Package, "neutral", "Delivery checked", "Qty and condition")],
      [n("q8", FileCheck, "neutral", "Invoice matched", "Order · delivery · invoice", "Three-way match: anything that disagrees becomes an item in My work.")],
    ],
    edges: [
      e("q1", "q2"),
      e("q2", "q3", "< $500"),
      e("q2", "q4", "$500–2k", "warn"),
      e("q2", "q5", "> $2k"),
      e("q3", "q6"),
      e("q4", "q6", "approved"),
      e("q5", "q6", "approved"),
      e("q6", "q7"),
      e("q7", "q8"),
    ],
  },
  {
    id: "shift-readiness",
    title: "Shift readiness",
    question: "Are we ready for Friday dinner, and what's blocking us?",
    audience: ["Venue manager", "Front of house"],
    mode: "Explore",
    summary:
      "A checklist says what's incomplete; a dependency view says which incomplete thing is blocking service. Opened from the shift briefing when something is red.",
    columns: [
      [
        n("f1", CalendarDays, "ok", "Bookings", "142 covers · 2 large parties"),
        n("f2", Users, "warn", "Roster", "BOH one short 6–9 pm"),
        n("f3", Package, "warn", "Stock", "2 items 86'd"),
        n("f4", Wrench, "ok", "Maintenance", "Cool room fixed"),
        n("f5", ListChecks, "ok", "Opening checks", "14 / 14 done"),
      ],
      [n("f6", ChefHat, "warn", "Kitchen ready", "Blocked by roster, stock"), n("f7", Store, "ok", "Floor ready", "Sections assigned")],
      [n("f8", CheckCircle2, "warn", "Service: Fri dinner", "1 blocker left", undefined, { emphasis: true })],
    ],
    edges: [
      e("f1", "f6"),
      e("f1", "f7"),
      e("f2", "f6", undefined, "warn"),
      e("f3", "f6", undefined, "warn"),
      e("f4", "f6"),
      e("f5", "f7"),
      e("f6", "f8", undefined, "warn"),
      e("f7", "f8"),
    ],
  },
  {
    id: "event-planning",
    title: "Event planning dependencies",
    question: "What's left before Saturday's 80-guest function?",
    audience: ["Front of house", "Venue manager"],
    mode: "Explore",
    summary:
      "Functions have long chains of dependent steps across FOH and BOH. Showing the chain makes the one missing confirmation (final numbers) obviously the thing holding up ordering and prep.",
    columns: [
      [n("v1", Banknote, "ok", "Deposit paid", "$1,500 · 2 Oct")],
      [n("v2", Utensils, "ok", "Menu confirmed", "Canapés + 2 mains")],
      [n("v3", Users, "fail", "Final numbers", "Due Wed · not received", "Guest list still at \"about 80\". Ordering and rostering can't be finalised.")],
      [n("v4", ShoppingCart, "missing", "Ingredient order", "Waiting on numbers"), n("v5", Users, "missing", "Event roster", "Waiting on numbers")],
      [n("v6", ChefHat, "missing", "Prep plan", "Thu–Sat"), n("v7", LayoutGrid, "ok", "Room setup", "Function room · long tables")],
      [n("v8", PartyPopper, "warn", "Function: Sat 18 Oct", "80 guests", undefined, { emphasis: true })],
    ],
    edges: [
      e("v1", "v2"),
      e("v2", "v3"),
      e("v3", "v4", undefined, "fail"),
      e("v3", "v5", undefined, "fail"),
      e("v4", "v6", undefined, "missing"),
      e("v5", "v8", undefined, "missing"),
      e("v6", "v8", undefined, "missing"),
      e("v7", "v8"),
    ],
  },
  {
    id: "supplier-risk",
    title: "Supplier risk exposure",
    question: "How much of the menu depends on one unreliable supplier?",
    audience: ["Kitchen manager", "Owner & GM"],
    mode: "Explore",
    summary:
      "Suppliers → what they supply → the dishes that need it, weighted by sales. Shows concentration risk and which dishes to keep a second supplier for.",
    columns: [
      [
        n("r1", Truck, "fail", "Southside Meats", "3 late deliveries / 4 wks"),
        n("r2", Truck, "ok", "Green Valley Produce", "On time"),
        n("r3", Truck, "ok", "Coastal Seafood", "On time"),
      ],
      [
        n("r4", Beef, "fail", "Beef mince", "Single supplier"),
        n("r5", Beef, "warn", "Pork belly", "Second supplier available"),
        n("r6", Package, "ok", "Potatoes"),
        n("r7", Package, "ok", "Barramundi"),
      ],
      [
        n("r8", Utensils, "fail", "Burgers (2)", "$3,900 / wk"),
        n("r9", Utensils, "warn", "Pork belly", "$1,100 / wk"),
        n("r10", Utensils, "ok", "Fish & chips", "$2,600 / wk"),
      ],
      [n("r11", AlertTriangle, "fail", "Revenue at risk", "$5,000 / wk (31% of food)", undefined, { emphasis: true })],
    ],
    edges: [
      e("r1", "r4", undefined, "fail"),
      e("r1", "r5", undefined, "warn"),
      e("r2", "r6"),
      e("r3", "r7"),
      e("r4", "r8", undefined, "fail"),
      e("r5", "r9", undefined, "warn"),
      e("r6", "r8"),
      e("r6", "r10"),
      e("r7", "r10"),
      e("r8", "r11", undefined, "fail"),
      e("r9", "r11", undefined, "warn"),
    ],
  },
  {
    id: "labour-forecast",
    title: "Labour forecast logic",
    question: "How many staff should we roster for next Friday?",
    audience: ["Venue manager", "Owner & GM"],
    mode: "Explore",
    summary:
      "The inputs and assumptions behind a staffing suggestion, laid out so a manager can see (and disagree with) each one rather than trusting a single number.",
    columns: [
      [
        n("lf1", CalendarDays, "ok", "Bookings so far", "118 covers"),
        n("lf2", TrendingUp, "ok", "Last 6 Fridays", "Avg $14,200"),
        n("lf3", CloudSun, "info", "Weather", "24° and clear · beer garden open"),
        n("lf4", PartyPopper, "info", "Local event", "AFL final nearby"),
      ],
      [n("lf5", Sigma, "info", "Forecast", "~310 covers · $15,800", "Range $14,100 to $17,300. Confidence is moderate: walk-ins vary a lot on finals weekends.")],
      [n("lf6", Clock, "neutral", "Demand by hour", "Peak 6:30–8:30 pm")],
      [n("lf7", Users, "neutral", "Suggested", "FOH 9 · BOH 5"), n("lf8", ClipboardCheck, "warn", "Rostered", "FOH 9 · BOH 4")],
      [n("lf9", AlertTriangle, "warn", "Gap", "Add 1 BOH 5–9 pm", undefined, { emphasis: true })],
    ],
    edges: [...all(["lf1", "lf2", "lf3", "lf4"], ["lf5"]), e("lf5", "lf6"), e("lf6", "lf7"), e("lf7", "lf9", "compare"), e("lf8", "lf9", undefined, "warn")],
  },
  {
    id: "ai-trace",
    title: "AI investigation trace",
    question: "How did Ask Goldy's reach its answer about food cost?",
    audience: ["Developer", "Owner & GM"],
    mode: "Inspect",
    summary:
      "Every tool call behind an answer, the permission check it passed and the evidence it returned. Lets you audit an answer and spot a tool that's returning the wrong thing.",
    columns: [
      [n("a1", MessageSquare, "neutral", "Question", "\"Why was food cost higher last week?\"")],
      [n("a2", ShieldCheck, "ok", "Permission check", "Kitchen manager · BOH scope", "Tools only see what this person's role can see.")],
      [
        n("a3", Cog, "ok", "get_inventory_summary", "Last 2 weeks · 180 ms"),
        n("a4", Cog, "ok", "compare_metric_periods", "food_cost · wk vs wk"),
        n("a5", Cog, "ok", "get_top_products", "Sales mix · 140 ms"),
      ],
      [n("a6", FileText, "info", "Evidence", "3 tables · 2 invoices cited")],
      [n("a7", Sparkles, "ok", "Answer", "Beef price + burger mix shift", "Every figure in the answer links back to the evidence above.", { emphasis: true })],
    ],
    edges: [e("a1", "a2"), ...all(["a2"], ["a3", "a4", "a5"]), ...all(["a3", "a4", "a5"], ["a6"]), e("a6", "a7")],
    live: { href: "/conversations", label: "Conversations" },
  },
  {
    id: "incident-escalation",
    title: "Incident escalation",
    question: "A guest complaint came in. Who handles it, and by when?",
    audience: ["Front of house", "Venue manager"],
    mode: "Configure",
    summary:
      "Who owns an incident at each severity, the deadline, and where it goes if nobody acts. Configured once; on shift, staff just see the task and its timer.",
    columns: [
      [n("x1", MessageSquare, "info", "Incident logged", "Guest complaint · table 14")],
      [n("x2", GitBranch, "neutral", "Severity?", "Set by duty manager")],
      [
        n("x3", UserCheck, "ok", "Duty manager", "Minor · resolve on shift"),
        n("x4", UserCheck, "warn", "Venue manager", "Serious · within 2 hours"),
        n("x5", ShieldCheck, "fail", "Owner / GM", "Safety or legal · immediately"),
      ],
      [n("x6", Hourglass, "neutral", "Unresolved 24h?", "Control")],
      [n("x7", Bell, "warn", "Escalate to GM", "Action")],
      [n("x8", FileCheck, "ok", "Closed", "Added to shift handover")],
    ],
    edges: [
      e("x1", "x2"),
      e("x2", "x3", "minor"),
      e("x2", "x4", "serious", "warn"),
      e("x2", "x5", "safety", "fail"),
      e("x3", "x6"),
      e("x4", "x6"),
      e("x6", "x7", "yes", "warn"),
      e("x6", "x8", "no"),
      e("x5", "x8"),
      e("x7", "x8"),
    ],
  },
];

export function findDiagram(id: string | null): ConceptDiagram {
  return DIAGRAMS.find((d) => d.id === id) ?? DIAGRAMS[0];
}

/** Lays a concept diagram into the shared column graph. Every node drills into the inspector. */
export function toColumnGraph(d: ConceptDiagram): ColumnGraph {
  return {
    columns: d.columns.map((col) =>
      col.map((node) => ({
        id: node.id,
        data: {
          title: node.title,
          subtitle: node.subtitle,
          detail: node.detail,
          icon: node.icon,
          tone: node.tone,
          emphasis: node.emphasis,
          drill: node.id,
        },
      })),
    ),
    edges: d.edges,
  };
}

/** A node plus what it connects to, for the inspector panel. */
export function describeNode(d: ConceptDiagram, id: string) {
  const nodes = d.columns.flat();
  const node = nodes.find((x) => x.id === id);
  if (!node) return null;
  const byId = new Map(nodes.map((x) => [x.id, x]));
  const incoming = d.edges.filter((x) => x.target === id).map((x) => ({ node: byId.get(x.source), label: x.label }));
  const outgoing = d.edges.filter((x) => x.source === id).map((x) => ({ node: byId.get(x.target), label: x.label }));
  return { node, incoming, outgoing };
}
