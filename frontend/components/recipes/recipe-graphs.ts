import { BookOpen, Folder, Package } from "lucide-react";
import type {
  RecipeComposition,
  RecipeCosting,
  RecipeFolder,
} from "@/lib/api/types";
import type { ColumnGraph } from "@/components/flow/types";
import type { TreeNode } from "@/components/flow/layout";
import { formatCurrency, formatNumber } from "@/lib/format";
import { recipeHref } from "./recipe-presentation";

/** Missing or cyclic ancestry stays at the top level rather than inventing a parent. */
function parentFolder(
  folder: RecipeFolder,
  folders: Map<number, RecipeFolder>,
): number | null {
  const seen = new Set([folder.folderId]);
  let parent = folder.parentId;
  while (parent != null && folders.has(parent)) {
    if (seen.has(parent)) return null;
    seen.add(parent);
    parent = folders.get(parent)!.parentId;
  }
  return folder.parentId != null && folders.has(folder.parentId)
    ? folder.parentId
    : null;
}

export function buildRecipeFolderTree(
  folders: RecipeFolder[],
  recipes: RecipeCosting[],
  expanded: ReadonlySet<number>,
  listHref?: string,
): TreeNode[] {
  const records = new Map(folders.map((folder) => [folder.folderId, folder]));
  const nodes = new Map<number, TreeNode>();
  const parents = new Map(
    folders.map((folder) => [folder.folderId, parentFolder(folder, records)]),
  );
  for (const folder of records.values()) {
    nodes.set(folder.folderId, {
      id: `folder:${folder.folderId}`,
      data: {
        title: folder.name?.trim() || "Unnamed folder",
        icon: Folder,
        tone: "neutral",
        drill: String(folder.folderId),
      },
      children: [],
    });
  }
  const roots: TreeNode[] = [];
  for (const [id, node] of nodes) {
    const parent = parents.get(id);
    if (parent != null) nodes.get(parent)!.children!.push(node);
    else roots.push(node);
  }
  const unlinked: TreeNode = {
    id: "folder:unlinked",
    data: { title: "Folder not linked", icon: Folder, tone: "neutral" },
    children: [],
  };
  const counts = new Map<number, number>();
  for (const recipe of recipes) {
    const node: TreeNode = {
      id: `recipe:${recipe.recipeId}`,
      data: {
        title: recipe.name?.trim() || "Unnamed recipe",
        subtitle: recipe.portionSize ?? undefined,
        icon: BookOpen,
        tone: "neutral",
        href: recipeHref(recipe.recipeId, listHref),
      },
    };
    const owner =
      recipe.folderId == null ? undefined : nodes.get(recipe.folderId);
    (owner ?? unlinked).children!.push(node);
    let id = owner ? recipe.folderId : null;
    while (id != null) {
      counts.set(id, (counts.get(id) ?? 0) + 1);
      id = parents.get(id);
    }
  }
  for (const [id, node] of nodes) {
    const count = counts.get(id) ?? 0;
    node.data.subtitle = `${count} recipe${count === 1 ? "" : "s"}`;
    if (!node.children!.length) {
      delete node.data.drill;
      continue;
    }
    node.data.detail = expanded.has(id)
      ? "Click to collapse"
      : "Click to expand";
    if (!expanded.has(id)) node.children = [];
  }
  if (unlinked.children!.length) roots.push(unlinked);
  return roots;
}

export function buildRecipeIngredientGraph(
  data: RecipeComposition,
  listHref?: string,
): ColumnGraph {
  const { header, stocks, usages } = data.definition;
  const children = [
    ...stocks.map((stock) => ({
      id: `stock:${stock.lineId}`,
      data: {
        title: stock.description?.trim() || "Unnamed ingredient",
        subtitle: `${formatNumber(stock.quantity, 4)} ${stock.unit ?? "—"}`,
        detail: `CTB contribution ${formatCurrency(stock.cost)}`,
        icon: Package,
        tone: stock.brokenLink ? ("warn" as const) : ("neutral" as const),
      },
    })),
    ...usages.map((usage) => ({
      id: `usage:${usage.lineId}`,
      data: {
        title: usage.name?.trim() || `Recipe #${usage.recipeId}`,
        subtitle: `${formatNumber(usage.quantity, 4)} ${usage.unit ?? "—"}`,
        detail: `Sub-recipe · CTB ${formatCurrency(usage.cost)}`,
        icon: BookOpen,
        tone: usage.brokenLink ? ("warn" as const) : ("neutral" as const),
        href: recipeHref(usage.recipeId, listHref),
      },
    })),
  ];
  const root = {
    id: `recipe:${header.recipeId}`,
    data: {
      title: header.name?.trim() || "Unnamed recipe",
      subtitle: `${formatNumber(header.portionCount, 4)} × ${formatNumber(header.portionSize, 4)} ${header.unit ?? "—"}`,
      icon: BookOpen,
      tone: "neutral" as const,
      emphasis: true,
    },
  };
  return {
    columns: children.length ? [[root], children] : [[root]],
    edges: children.map((child) => ({
      source: root.id,
      target: child.id,
      tone: child.data.tone,
    })),
  };
}
