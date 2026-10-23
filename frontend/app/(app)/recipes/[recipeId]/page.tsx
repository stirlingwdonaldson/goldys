"use client";
import { use } from "react";
import Link from "next/link";
import { RecipeDetailsPanel } from "@/components/recipes/recipe-details-panel";
import { RecipeCompositionPanel } from "@/components/recipes/recipe-composition-panel";
export default function RecipeDetailPage({params}:{params:Promise<{recipeId:string}>}){
 const {recipeId}=use(params);const id=Number(recipeId);
 return <div className="flex flex-col gap-6"><Link href="/recipes" className="underline">All recipes</Link>{Number.isSafeInteger(id)&&id>0?<><RecipeDetailsPanel key={id} recipeId={id}/><RecipeCompositionPanel key={`composition-${id}`} recipeId={id}/></>:<p role="alert">Invalid recipe ID</p>}</div>;
}
