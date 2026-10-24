import { describe, expect, it } from "vitest";
import type { RecipeCosting, RecipeFolder } from "@/lib/api/types";
import type { TreeNode } from "@/components/flow/layout";
import { demoRecipeComposition } from "@/lib/api/demo-recipe-composition";
import {
  buildRecipeFolderTree,
  buildRecipeIngredientGraph,
} from "./recipe-graphs";

const recipe = (id: number, folderId: number | null): RecipeCosting => ({
  recipeId: id,
  folderId,
  name: `Recipe ${id}`,
  folder: "Prep",
  portionSize: null,
  costPerPortion: null,
  actualCost: null,
  costGap: null,
  menuPrice: null,
  margin: null,
  gpPercent: null,
  menuState: "UNKNOWN",
  priceSource: "NONE",
  lastSoldPrice: null,
  lastSoldCost: null,
  lastSoldDate: null,
});
const flatten = (nodes: TreeNode[]): TreeNode[] =>
  nodes.flatMap((node) => [node, ...flatten(node.children ?? [])]);

describe("recipe folder graph", () => {
  const folders: RecipeFolder[] = [
    { folderId: 0, parentId: null, name: "Recipes" },
    { folderId: 1, parentId: 0, name: "Prep" },
    { folderId: 2, parentId: 0, name: "Prep" },
    { folderId: 3, parentId: 1, name: "Sauces" },
    { folderId: 4, parentId: 0, name: "Empty" },
  ];
  it("uses explicit folder IDs, retains empty folders and places unknown ownership separately", () => {
    const tree = buildRecipeFolderTree(
      folders,
      [
        recipe(10, 3),
        recipe(11, 2),
        recipe(12, null),
        recipe(13, 99),
        recipe(14, 0),
      ],
      new Set([0, 1, 2, 3, 4]),
      "/recipes?search=Recipe",
    );
    const all = flatten(tree);
    const get = (id: string) => all.find((node) => node.id === id)!;
    expect(get("folder:3").children?.map((n) => n.id)).toEqual(["recipe:10"]);
    expect(get("folder:2").children?.map((n) => n.id)).toEqual(["recipe:11"]);
    expect(get("folder:4").children).toEqual([]);
    expect(get("folder:0").children?.map((n) => n.id)).toContain("recipe:14");
    expect(get("folder:unlinked").children?.map((n) => n.id)).toEqual([
      "recipe:12",
      "recipe:13",
    ]);
    expect(get("recipe:10").data.href).toBe(
      "/recipes/10?from=%2Frecipes%3Fsearch%3DRecipe",
    );
    expect(get("folder:0").data.subtitle).toBe("3 recipes");
  });
  it("collapses a branch without losing its nested recipe count", () => {
    const all = flatten(
      buildRecipeFolderTree(folders, [recipe(10, 3)], new Set([0])),
    );
    expect(all.find((n) => n.id === "folder:1")?.data.subtitle).toBe(
      "1 recipe",
    );
    expect(all.some((n) => n.id === "recipe:10")).toBe(false);
  });
  it("bounds cycles and keeps disconnected folders and their recipes visible", () => {
    const malformed = [
      { folderId: 1, parentId: 2, name: "A" },
      { folderId: 2, parentId: 1, name: "B" },
      { folderId: 3, parentId: 99, name: "Orphan" },
    ];
    const roots = buildRecipeFolderTree(
      malformed,
      [recipe(10, 1)],
      new Set([1, 2, 3]),
    );
    expect(roots.map((n) => n.id)).toEqual([
      "folder:1",
      "folder:2",
      "folder:3",
    ]);
    expect(flatten(roots).map((n) => n.id)).toContain("recipe:10");
  });
});

describe("recipe ingredient graph", () => {
  it("preserves distinct lines, signed quantities, missing costs and explicit child links", () => {
    const data = demoRecipeComposition(3)!;
    data.definition.stocks[0].cost = null;
    data.definition.usages[0].quantity = -15;
    data.definition.usages.push({
      ...data.definition.usages[0],
      lineId: 2,
      quantity: 25,
    });
    const graph = buildRecipeIngredientGraph(data, "/recipes?folder=Mains");
    expect(graph.columns[1]).toHaveLength(3);
    expect(new Set(graph.columns.flat().map((n) => n.id)).size).toBe(4);
    expect(graph.columns[1][0].data.detail).toBe("CTB contribution —");
    expect(graph.columns[1][1].data.subtitle).toBe("-15 ml");
    expect(graph.columns[1][1].data.href).toBe(
      "/recipes/8?from=%2Frecipes%3Ffolder%3DMains",
    );
    expect(graph.edges).toHaveLength(3);
  });
  it("renders an empty captured composition as a recipe with no invented ingredients", () => {
    const data = demoRecipeComposition(3)!;
    data.definition.stocks = [];
    data.definition.usages = [];
    const graph = buildRecipeIngredientGraph(data);
    expect(graph.columns.flat().map((n) => n.id)).toEqual(["recipe:3"]);
    expect(graph.edges).toEqual([]);
  });
});
