"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { LoadingState } from "@/components/states/loading-state";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DateRangeFilter, defaultRange } from "@/components/sales-detail/date-range-filter";
import { PaymentsTab } from "@/components/sales-detail/payments-tab";
import { DeletedOrdersTab } from "@/components/sales-detail/deleted-orders-tab";
import { SaleItemsTab } from "@/components/sales-detail/sale-items-tab";
import { lineageDomainForTab } from "@/components/sales-detail/lineage-graph";
import { LineageGraphView } from "@/components/sales-detail/lineage-graph-view";
import { RelationshipGraphView } from "@/components/sales-detail/relationship-graph-view";

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
  // The sale drilled into from a Payments or Sale items row; null hides the relationship graph.
  const [selectedSale, setSelectedSale] = useState<string | null>(null);

  // The lineage graph's metric node drills to a tab id; switching is local, as with the tab bar.
  function switchTab(tabId: string) {
    if (isTab(tabId)) setTab(tabId);
  }

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

      {selectedSale ? (
        <Section
          title={`Sale ${selectedSale}`}
          description="Payments and line items behind this sale."
          actions={
            <Button variant="outline" size="sm" onClick={() => setSelectedSale(null)}>
              Close
            </Button>
          }
        >
          <RelationshipGraphView saleNumber={selectedSale} />
        </Section>
      ) : null}

      {tab === "payments" ? (
        <PaymentsTab from={range.from} to={range.to} onViewSale={setSelectedSale} />
      ) : null}
      {tab === "deleted-orders" ? <DeletedOrdersTab from={range.from} to={range.to} /> : null}
      {tab === "sale-items" ? (
        <SaleItemsTab from={range.from} to={range.to} onViewSale={setSelectedSale} />
      ) : null}

      <Section
        title="Data lineage"
        description="From the Lightspeed webhook, through the raw ledger and resolution, to the metric."
      >
        <LineageGraphView domain={lineageDomainForTab(tab)} onSwitchTab={switchTab} />
      </Section>
    </div>
  );
}
