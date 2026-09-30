import type { Metadata } from "next";
import { TrendingUp } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Sales" };

export default function SalesPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Sales</h1>
        <p className="text-sm text-muted-foreground">Net sales and product mix.</p>
      </div>
      <AwaitingData
        label="Sales reporting"
        description="Net sales today and this week vs. last week, with product mix."
        reason="Awaiting sales-reporting endpoint."
        icon={TrendingUp}
      />
    </div>
  );
}
