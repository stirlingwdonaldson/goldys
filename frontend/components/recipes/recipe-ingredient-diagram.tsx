"use client";

import { useMemo } from "react";
import type { RecipeComposition } from "@/lib/api/types";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { Section } from "@/components/layout/section";
import { buildRecipeIngredientGraph } from "./recipe-graphs";

export function RecipeIngredientDiagram({
  data,
  listHref,
}: {
  data: RecipeComposition;
  listHref?: string;
}) {
  const graph = useMemo(
    () => buildRecipeIngredientGraph(data, listHref),
    [data, listHref],
  );
  return (
    <Section
      title="Recipe & ingredients"
      description="Direct ingredients and sub-recipes for the recorded batch. Click a sub-recipe to see its ingredients."
    >
      <FlowCanvas
        key={data.definition.header.recipeId}
        graph={graph}
        ariaLabel="Recipe ingredients and sub-recipes"
        height={420}
        minZoom={0.05}
      />
    </Section>
  );
}
