"use client";

import { Suspense, useRef } from "react";
import { useInvestigationUrl, investigationRange } from "@/lib/use-investigation-url";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { LoadingState } from "@/components/states/loading-state";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { TimeRangeSelector, defaultRange } from "@/components/ui/time-range-selector";
import { PaymentsTab } from "@/components/sales-detail/payments-tab";
import { DeletedOrdersTab } from "@/components/sales-detail/deleted-orders-tab";
import { SaleItemsTab } from "@/components/sales-detail/sale-items-tab";
import { lineageDomainForTab } from "@/components/sales-detail/lineage-graph";
import { LineageGraphView } from "@/components/sales-detail/lineage-graph-view";
import { SaleSummaryView } from "@/components/sales-detail/sale-summary-view";
import { SettlementFlowView } from "@/components/sales-detail/settlement-flow-view";

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
  const { params, update, openPanel, closePanel, restoreFocus } = useInvestigationUrl();
  const tabParam = params.get("tab");
  const tab = isTab(tabParam) ? tabParam : "payments";
  const range = investigationRange(params, defaultRange());
  const selectedSale = params.get("sale")?.slice(0, 200) || null;
  const lastSale = useRef(selectedSale);
  if (selectedSale) lastSale.current = selectedSale;
  function setTab(value: string) { if (isTab(value)) update({ tab: value }); }
  function viewSale(sale: string) { openPanel("sale", sale, { tab, ...range }); }
  function switchTab(value: string) { setTab(value); }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Sales detail"
        description="Payments, deleted orders, and sale items behind the sales totals."
      />

      <TimeRangeSelector value={range} onChange={(value) => update({ ...value, "payments-page": null, "deleted-page": null, "items-page": null })} />

      <Section
        title="Sales and POS payments"
        description="Compare recorded merchandise with POS payments for the selected period."
      >
        <SettlementFlowView from={range.from} to={range.to} />
      </Section>

      <Tabs value={tab} onValueChange={(v) => setTab(v as Tab)}>
        <TabsList aria-label="Sales detail section">
          {TABS.map((t) => (
            <TabsTrigger key={t.value} value={t.value}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <Sheet open={selectedSale !== null} onOpenChange={(open) => { if (!open) closePanel("sale"); }}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-5xl" onCloseAutoFocus={(event) => restoreFocus(event, Array.from(document.querySelectorAll<HTMLButtonElement>("button[aria-label]")).find(button => button.getAttribute("aria-label") === `View sale ${lastSale.current}`))}>
          <SheetHeader><SheetTitle>Sale {selectedSale}</SheetTitle><SheetDescription>{range.from} to {range.to}, inclusive. Recorded merchandise and payments.</SheetDescription></SheetHeader>
          {selectedSale ? <SaleSummaryView key={selectedSale} saleNumber={selectedSale} from={range.from} to={range.to} /> : null}
        </SheetContent>
      </Sheet>

      {tab === "payments" ? (
        <PaymentsTab from={range.from} to={range.to} onViewSale={viewSale} />
      ) : null}
      {tab === "deleted-orders" ? (
        <DeletedOrdersTab from={range.from} to={range.to} onViewSale={viewSale} />
      ) : null}
      {tab === "sale-items" ? (
        <SaleItemsTab from={range.from} to={range.to} onViewSale={viewSale} />
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
