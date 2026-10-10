import type { LucideIcon } from "lucide-react";
import {
  Activity,
  BookOpen,
  CalendarDays,
  ChefHat,
  Database,
  Home,
  LayoutGrid,
  MessagesSquare,
  ReceiptText,
  Scale,
  ScrollText,
  Settings,
  ShieldCheck,
  TrendingUp,
  Users,
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
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: "Business",
    items: [
      { title: "Overview", href: "/dashboard", icon: Home, keywords: ["dashboard", "home", "kpi"] },
      { title: "Sales", href: "/sales", icon: TrendingUp, keywords: ["revenue", "takings"] },
      {
        title: "Sales detail",
        href: "/sales-detail",
        icon: ReceiptText,
        keywords: ["payments", "voids", "deleted orders", "sale items", "line items", "tenders"],
      },
      { title: "Staff & labour", href: "/staff", icon: Users, keywords: ["roster", "wages", "deputy"] },
      { title: "Reservations", href: "/reservations", icon: CalendarDays, keywords: ["covers", "bookings", "opentable"] },
      { title: "Kitchen", href: "/kitchen", icon: ChefHat, keywords: ["food cost", "stock", "invoices", "suppliers"] },
      { title: "Custom dashboards", href: "/dashboards", icon: LayoutGrid, keywords: ["widgets", "reports"] },
      { title: "Conversations", href: "/conversations", icon: MessagesSquare, keywords: ["ask", "chat", "threads"] },
    ],
  },
  {
    label: "Data",
    items: [
      { title: "Reconciliation", href: "/reconciliation", icon: Scale, keywords: ["conflicts", "exceptions", "overrides"] },
      { title: "Resolution rules", href: "/resolution-rules", icon: ShieldCheck, keywords: ["source priority"] },
      { title: "Data health", href: "/data-health", icon: Activity, keywords: ["connectors", "sync", "ingestion"] },
      { title: "Data explorer", href: "/data", icon: Database, keywords: ["tables", "raw"] },
      { title: "Logs", href: "/logs", icon: ScrollText, keywords: ["audit", "errors"] },
    ],
  },
  {
    label: "Coming soon",
    comingSoon: true,
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
