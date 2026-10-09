import type { Metadata } from "next";
import { BookOpen } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";
import { PageHeader } from "@/components/layout/page-header";

export const metadata: Metadata = { title: "Recipes" };

export default function RecipesPage() {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Recipes" description="Recipe list and ingredient costing." />
      <AwaitingData
        label="Recipes"
        description="Recipe list, per-dish ingredient cost, and gross margin."
        reason="Recipes not yet connected."
        icon={BookOpen}
      />
    </div>
  );
}
