import type { Metadata } from "next";
import { BookOpen } from "lucide-react";
import { AwaitingData } from "@/components/states/awaiting-data";

export const metadata: Metadata = { title: "Recipes" };

export default function RecipesPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Recipes</h1>
        <p className="text-sm text-muted-foreground">Recipe list and ingredient costing.</p>
      </div>
      <AwaitingData
        label="Recipes"
        description="Recipe list, per-dish ingredient cost, and gross margin."
        reason="Recipes not yet connected."
        icon={BookOpen}
      />
    </div>
  );
}
