import type { Metadata } from "next";
import { ChefHat } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Kitchen" };

export default function KitchenPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Kitchen</h1>
        <p className="text-sm text-muted-foreground">Food cost, stock, and wastage.</p>
      </div>
      <AwaitingData
        label="Food cost"
        description="Cost of goods, stock levels, stocktakes, and wastage."
        reason="Inventory not yet connected."
        icon={ChefHat}
      />
    </div>
  );
}
