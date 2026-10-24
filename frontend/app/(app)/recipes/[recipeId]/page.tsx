"use client";
import { Suspense, use } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { RecipeDetailsPanel } from "@/components/recipes/recipe-details-panel";
import { LoadingState } from "@/components/states/loading-state";
function RecipeDetail({ recipeId }: { recipeId: string }) {
  const params = useSearchParams();
  const from = params.get("from");
  const back =
    from === "/recipes" || from?.startsWith("/recipes?") ? from : "/recipes";
  const id = Number(recipeId);
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <Link href={back} className="text-sm underline">
        Back to recipes
      </Link>
      {Number.isSafeInteger(id) && id > 0 ? (
        <RecipeDetailsPanel key={id} recipeId={id} listHref={back} />
      ) : (
        <p role="alert">Invalid recipe ID</p>
      )}
    </div>
  );
}
export default function RecipeDetailPage({
  params,
}: {
  params: Promise<{ recipeId: string }>;
}) {
  const { recipeId } = use(params);
  return (
    <Suspense fallback={<LoadingState rows={4} />}>
      <RecipeDetail recipeId={recipeId} />
    </Suspense>
  );
}
