import Link from "next/link";
import {
  CalendarDays,
  ChefHat,
  LayoutGrid,
  ReceiptText,
  Scale,
  TrendingUp,
  Users,
  Workflow,
  type LucideIcon,
} from "lucide-react";
import { IconTile } from "@/components/ui/icon-tile";
import type { Focus } from "@/lib/work-queue";

interface Shortcut {
  title: string;
  description: string;
  href: string;
  icon: LucideIcon;
}

/**
 * The pages each perspective reaches for first. Owners and GMs look at trends and
 * decisions; the venue manager at this week's trading; the kitchen at cost; front of
 * house at bookings and the till. Access is still enforced on each page.
 */
export const SHORTCUTS: Record<Focus, Shortcut[]> = {
  business: [
    { title: "Sales", description: "Trading against prior periods", href: "/sales", icon: TrendingUp },
    { title: "Staff & labour", description: "Hours and labour cost", href: "/staff", icon: Users },
    { title: "Custom dashboards", description: "Saved views and packs", href: "/dashboards", icon: LayoutGrid },
    { title: "Reconciliation", description: "Figures awaiting a decision", href: "/reconciliation", icon: Scale },
  ],
  venue: [
    { title: "Sales", description: "Today and this week", href: "/sales", icon: TrendingUp },
    { title: "Reservations", description: "Covers and bookings ahead", href: "/reservations", icon: CalendarDays },
    { title: "Staff & labour", description: "Who's on and hours worked", href: "/staff", icon: Users },
    { title: "Kitchen", description: "Food cost and invoices", href: "/kitchen", icon: ChefHat },
  ],
  kitchen: [
    { title: "Kitchen", description: "Food cost, suppliers, invoices", href: "/kitchen", icon: ChefHat },
    { title: "Sales detail", description: "What sold, item by item", href: "/sales-detail", icon: ReceiptText },
    { title: "Reservations", description: "Covers to prep for", href: "/reservations", icon: CalendarDays },
    { title: "Flow lab", description: "Ingredient and supplier impact", href: "/flow-lab?d=recipe-impact", icon: Workflow },
  ],
  foh: [
    { title: "Reservations", description: "Tonight's bookings", href: "/reservations", icon: CalendarDays },
    { title: "Sales detail", description: "Payments, voids, line items", href: "/sales-detail", icon: ReceiptText },
    { title: "Sales", description: "How trading is tracking", href: "/sales", icon: TrendingUp },
    { title: "Staff & labour", description: "Who's on shift", href: "/staff", icon: Users },
  ],
};

export function FocusShortcuts({ focus }: { focus: Focus }) {
  return (
    <nav aria-label="Shortcuts" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {SHORTCUTS[focus].map((s) => (
        <Link
          key={s.href}
          href={s.href}
          className="flex items-center gap-3 rounded-xl border bg-card p-3 transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <IconTile>
            <s.icon />
          </IconTile>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{s.title}</p>
            <p className="truncate text-xs text-muted-foreground">{s.description}</p>
          </div>
        </Link>
      ))}
    </nav>
  );
}
