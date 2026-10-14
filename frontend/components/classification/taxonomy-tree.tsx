"use client";

import { useMemo } from "react";
import { Folder, Tag } from "lucide-react";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { layoutTree, type TreeNode } from "@/components/flow/layout";
import { EmptyState } from "@/components/states/empty-state";
import type { CategoryNode } from "@/lib/api";

interface TaxonomyTreeProps {
  categories: CategoryNode[];
  /** Called with the id of the category whose node was clicked. */
  onDrill?: (categoryId: string) => void;
  height?: number;
}

/** A category leaf. `grouped` distinguishes a curated category from a flat (null-group) one. */
function categoryNode(category: CategoryNode, grouped: boolean): TreeNode {
  const count = category.productCount;
  return {
    id: category.id,
    data: {
      title: category.name,
      subtitle: count === 1 ? "1 product" : `${count} products`,
      icon: Tag,
      // A category inside a group has been curated; a flat one is still unattached.
      // An empty category holds no products yet, which is "no data", not "bad".
      tone: count === 0 ? "missing" : grouped ? "ok" : "neutral",
      drill: category.id,
    },
  };
}

function groupNode(groupName: string, children: CategoryNode[]): TreeNode {
  const products = children.reduce((sum, child) => sum + child.productCount, 0);
  return {
    id: `group:${groupName}`,
    data: {
      title: groupName,
      subtitle: `${children.length} categories · ${products} products`,
      icon: Folder,
      tone: "neutral",
      // A group is a level above its categories: the bold border reads as a header.
      emphasis: true,
    },
    children: children.map((child) => categoryNode(child, true)),
  };
}

/**
 * The taxonomy as a tree: categories with a null `groupName` are roots at the top level,
 * grouped categories sit under a group node. A group takes its place in root order from
 * its first category, so the picture follows the API's ordering rather than reshuffling.
 */
export function buildTaxonomyTree(categories: CategoryNode[]): TreeNode[] {
  const grouped = new Map<string, CategoryNode[]>();
  for (const category of categories) {
    if (category.groupName == null) continue;
    const bucket = grouped.get(category.groupName) ?? [];
    bucket.push(category);
    grouped.set(category.groupName, bucket);
  }

  const roots: TreeNode[] = [];
  const placed = new Set<string>();
  for (const category of categories) {
    if (category.groupName == null) {
      roots.push(categoryNode(category, false));
      continue;
    }
    if (placed.has(category.groupName)) continue;
    placed.add(category.groupName);
    roots.push(groupNode(category.groupName, grouped.get(category.groupName) ?? []));
  }
  return roots;
}

/**
 * The curated taxonomy, drawn top-down: flat categories beside the groups, categories
 * nested under their group. Clicking a category drills into it (the page shows that
 * category's products).
 */
export function TaxonomyTree({ categories, onDrill, height = 420 }: TaxonomyTreeProps) {
  const laid = useMemo(() => layoutTree(buildTaxonomyTree(categories)), [categories]);

  if (categories.length === 0) {
    return (
      <EmptyState
        title="No categories yet"
        description="Curation turns clusters into categories in the Clusters tab; they land here."
      />
    );
  }

  return (
    <FlowCanvas
      laidOut={laid}
      ariaLabel="Product taxonomy: groups and categories, with product counts"
      onDrill={onDrill}
      height={height}
    />
  );
}
