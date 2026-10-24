"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useApi } from "@/lib/demo-mode";
import type { RecipeCosting } from "@/lib/api/types";
import { FlowCanvas } from "@/components/flow/flow-canvas";
import { layoutTree } from "@/components/flow/layout";
import { Section } from "@/components/layout/section";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { buildRecipeFolderTree } from "./recipe-graphs";

export function RecipeFolderDiagram({
  recipes,
  listHref,
}: {
  recipes: RecipeCosting[];
  listHref?: string;
}) {
  const api = useApi();
  const catalog = useQuery({
    queryKey: ["getRecipeCatalog"],
    queryFn: ({ signal }) => api.getRecipeCatalog({ signal }),
  });
  const [expanded, setExpanded] = useState<Set<number> | null>(null);
  const folders = catalog.data?.folders;
  const roots = useMemo(
    () =>
      new Set(
        (folders ?? [])
          .filter(
            (folder) =>
              folder.parentId == null ||
              !folders?.some((parent) => parent.folderId === folder.parentId),
          )
          .map((folder) => folder.folderId),
      ),
    [folders],
  );
  const visible = expanded ?? roots;
  const graph = useMemo(
    () =>
      layoutTree(
        buildRecipeFolderTree(folders ?? [], recipes, visible, listHref),
        { direction: "horizontal", rootGap: 0 },
      ),
    [folders, recipes, visible, listHref],
  );
  function toggle(value: string) {
    const id = Number(value);
    setExpanded((previous) => {
      const next = new Set(previous ?? roots);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }
  return (
    <Section
      title="Recipe folders"
      description="Click a folder to expand its branches, or a recipe to open it. Pan and zoom to explore."
    >
      {catalog.isPending ? (
        <LoadingState rows={3} />
      ) : catalog.isLoadingError ? (
        <ErrorState
          title="Couldn't load recipe folders"
          message={catalog.error.message}
          onRetry={catalog.refetch}
        />
      ) : (
        <>
          {!catalog.data ? (
            <p className="text-sm text-muted-foreground">
              Folder hierarchy has not been received. Recipes are shown without
              folder links.
            </p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={() =>
                setExpanded(
                  new Set(folders?.map((folder) => folder.folderId) ?? []),
                )
              }
            >
              Expand all folders
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setExpanded(roots)}
            >
              Collapse folders
            </Button>
          </div>
          <FlowCanvas
            key={[...visible].sort((a, b) => a - b).join(",")}
            ariaLabel="Recipe folders and nested recipes"
            laidOut={graph}
            onDrill={toggle}
            height={520}
            minZoom={0.005}
          />
        </>
      )}
    </Section>
  );
}
