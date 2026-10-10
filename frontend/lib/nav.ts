import type { LucideIcon } from "lucide-react";
import {
  Activity,
  BookOpen,
  CalendarDays,
  ChefHat,
  Database,
  Home,
  Inbox,
  LayoutGrid,
  MessagesSquare,
  ReceiptText,
  Scale,
  ScrollText,
  Settings,
  ShieldCheck,
  TrendingUp,
  Users,
  Workflow,
} from "lucide-react";

/**
 * One navigation model shared by the sidebar, the header breadcrumb and the ⌘K
 * palette, so a page is called the same thing everywhere (heuristic 4).
 * Labels use the venue's words and Australian spelling (heuristic 2).
 */
export interface NavItem {
  title: string;
  href: string;
  icon: LucideIcon;
  /** Extra words the command palette should match on. */
  keywords?: string[];
}

export interface NavGroup {
  label: string;
  items: NavItem[];
  /** Placeholder surfaces with no data yet; rendered collapsed (heuristic 8). */
  comingSoon?: boolean;
  /** Rendered as an expandable group, closed unless it holds the current page. */
  collapsible?: boolean;
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: "Workspace",
    items: [
      { title: "Home", href: "/dashboard", icon: Home, keywords: ["dashboard", "overview", "kpi"] },
      {
        title: "My work",
        href: "/my-work",
        icon: Inbox,
        keywords: ["inbox", "tasks", "alerts", "attention", "to do", "queue"],
      },
      { title: "Reconciliation", href: "/reconciliation", icon: Scale, keywords: ["conflicts", "exceptions", "overrides", "decisions"] },
      { title: "Conversations", href: "/conversations", icon: MessagesSquare, keywords: ["ask", "chat", "threads"] },
    ],
  },
  {
    label: "Operations",
    items: [
      { title: "Reservations", href: "/reservations", icon: CalendarDays, keywords: ["covers", "bookings", "opentable", "venue"] },
      { title: "Kitchen", href: "/kitchen", icon: ChefHat, keywords: ["food cost", "stock", "invoices", "suppliers"] },
      { title: "Staff & labour", href: "/staff", icon: Users, keywords: ["roster", "wages", "deputy"] },
    ],
  },
  {
    label: "Performance",
    items: [
      { title: "Sales", href: "/sales", icon: TrendingUp, keywords: ["revenue", "takings"] },
      {
        title: "Sales detail",
        href: "/sales-detail",
        icon: ReceiptText,
        keywords: ["payments", "voids", "deleted orders", "sale items", "line items", "tenders"],
      },
      { title: "Custom dashboards", href: "/dashboards", icon: LayoutGrid, keywords: ["widgets", "reports"] },
    ],
  },
  {
    label: "Intelligence",
    items: [
      {
        title: "Flow lab",
        href: "/flow-lab",
        icon: Workflow,
        keywords: ["diagrams", "automations", "lineage", "dependencies", "react flow", "workflow"],
      },
    ],
  },
  {
    // Technical surfaces: most managers only need these when something is wrong, so the
    // group starts collapsed unless it holds the current page or a failing source.
    label: "Administration",
    collapsible: true,
    items: [
      { title: "Resolution rules", href: "/resolution-rules", icon: ShieldCheck, keywords: ["source priority"] },
      { title: "Data health", href: "/data-health", icon: Activity, keywords: ["connectors", "sync", "ingestion"] },
      { title: "Data explorer", href: "/data", icon: Database, keywords: ["tables", "raw"] },
      { title: "Logs", href: "/logs", icon: ScrollText, keywords: ["audit", "errors"] },
    ],
  },
  {
    label: "Coming soon",
    comingSoon: true,
    collapsible: true,
    items: [
      { title: "Recipes", href: "/recipes", icon: BookOpen, keywords: ["costing"] },
    ],
  },
];

export const SETTINGS_ITEM: NavItem = {
  title: "Settings",
  href: "/settings",
  icon: Settings,
  keywords: ["demo mode", "preferences"],
};

export function isActive(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

/** The group and item for the current path, for breadcrumbs. */
export function findNav(pathname: string): { group: string | null; item: NavItem } | null {
  for (const group of NAV_GROUPS) {
    const item = group.items.find((i) => isActive(pathname, i.href));
    if (item) return { group: group.comingSoon ? null : group.label, item };
  }
  if (isActive(pathname, SETTINGS_ITEM.href)) return { group: null, item: SETTINGS_ITEM };
  return null;
}
