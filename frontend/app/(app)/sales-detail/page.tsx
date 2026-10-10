"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/layout/page-header";
import { LoadingState } from "@/components/states/loading-state";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { EmptyState } from "@/components/states/empty-state";
import { DateRangeFilter, defaultRange } from "@/components/sales-detail/date-range-filter";

const TABS = [
  { value: "payments", label: "Payments" },
  { value: "deleted-orders", label: "Deleted orders" },
  { value: "sale-items", label: "Sale items" },
] as const;

type Tab = (typeof TABS)[number]["value"];

function isTab(value: string | null): value is Tab {
  return TABS.some((t) => t.value === value);
}

/**
 * Sales detail: the payments, deleted orders, and sale items behind the sales totals.
 * `?tab=payments|deleted-orders|sale-items` deep-links the active tab (from the pipeline map).
 */
export default function SalesDetailPage() {
  return (
    <Suspense fallback={<LoadingState rows={4} />}>
      <SalesDetail />
    </Suspense>
  );
}

function SalesDetail() {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  // The URL seeds the tab once; switching is local and does not rewrite the URL.
  const [tab, setTab] = useState<Tab>(isTab(tabParam) ? tabParam : "payments");
  // Hoisted so all three tabs read one window once Tasks 8–10 fill them in.
  const [range, setRange] = useState(defaultRange);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Sales detail"
        description="Payments, deleted orders, and sale items behind the sales totals."
      />

      <DateRangeFilter from={range.from} to={range.to} onChange={setRange} />

      <Tabs value={tab} onValueChange={(v) => setTab(v as Tab)}>
        <TabsList aria-label="Sales detail section">
          {TABS.map((t) => (
            <TabsTrigger key={t.value} value={t.value}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {tab === "payments" ? <PaymentsTab /> : null}
      {tab === "deleted-orders" ? <DeletedOrdersTab /> : null}
      {tab === "sale-items" ? <SaleItemsTab /> : null}
    </div>
  );
}

// Placeholder stubs. Tasks 8–10 replace these with the real tab components.
function PaymentsTab() {
  return (
    <EmptyState
      title="No payments yet"
      description="Payment and tender detail for the selected dates will appear here."
    />
  );
}

function DeletedOrdersTab() {
  return (
    <EmptyState
      title="No deleted orders yet"
      description="Voided and deleted sales for the selected dates will appear here."
    />
  );
}

function SaleItemsTab() {
  return (
    <EmptyState
      title="No sale items yet"
      description="Line-item detail for the selected dates will appear here."
    />
  );
}
