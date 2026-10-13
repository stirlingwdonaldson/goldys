"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Box, ChevronDown } from "lucide-react";
import {
  isApiError,
  type AssignmentRow,
  type CategoryNode,
  type ClassificationRun,
  type ClassificationRunKind,
  type ClassificationRunStatus,
  type DendrogramNode,
  type ProductPoint,
} from "@/lib/api";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { formatDateTime, formatPercent, humanizeKey } from "@/lib/format";
import { AwaitingData } from "@/components/states/awaiting-data";
import { ErrorState } from "@/components/states/error-state";
import { InlineError } from "@/components/states/inline-error";
import { LoadingState } from "@/components/states/loading-state";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { StatCard } from "@/components/data-display/stat-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DataTable } from "@/components/data-table/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { TaxonomyTree } from "@/components/classification/taxonomy-tree";
import { DendrogramView, type DendroAction, type DendroSelection } from "@/components/classification/dendrogram-view";
import { EmbeddingScatter } from "@/components/classification/embedding-scatter";
import { PipelineFlow, type PipelineStage } from "@/components/classification/pipeline-flow";

type Tab = "categories" | "clusters" | "pipeline";

/** Status pills follow the platform's status vocabulary, not a bespoke one. */
function StatusBadge({ status }: { status: string }) {
  if (status === "NEEDS_REVIEW") return <Badge variant="conflict">Needs review</Badge>;
  if (status === "UNASSIGNED") return <Badge variant="missing">Unassigned</Badge>;
  return <Badge variant="success">{humanizeKey(status)}</Badge>;
}

/**
 * The select-then-action move control. An empty selection means "no category": that
 * drops the product back to the classifier's suggestion rather than inventing a
 * category for it.
 */
function MoveToCategory({
  productNameKey,
  categoryId,
  categories,
  onMove,
  onClear,
}: {
  productNameKey: string;
  categoryId: string | null;
  categories: CategoryNode[];
  onMove: (productNameKey: string, categoryId: string) => void;
  onClear: (productNameKey: string) => void;
}) {
  return (
    <select
      aria-label={`Move ${productNameKey} to a category`}
      className="h-8 rounded-md border bg-background px-2 text-sm"
      value={categoryId ?? ""}
      onChange={(event) => {
        const next = event.target.value;
        if (next === "") {
          if (categoryId != null) onClear(productNameKey);
          return;
        }
        onMove(productNameKey, next);
      }}
    >
      <option value="">Uncategorised</option>
      {categories.map((category) => (
        <option key={category.id} value={category.id}>
          {category.groupName ? `${category.groupName} · ${category.name}` : category.name}
        </option>
      ))}
    </select>
  );
}

function RenameCategoryForm({
  category,
  onSave,
}: {
  category: CategoryNode;
  onSave: (name: string) => void;
}) {
  const [name, setName] = useState(category.name);
  const dirty = name.trim() !== "" && name !== category.name;
  return (
    <div className="flex flex-wrap items-center gap-2">
      <label htmlFor="category-name" className="text-sm text-muted-foreground">
        Category name
      </label>
      <Input
        id="category-name"
        value={name}
        onChange={(event) => setName(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter" && dirty) onSave(name.trim());
        }}
        className="h-8 w-56"
      />
      <Button size="sm" disabled={!dirty} onClick={() => onSave(name.trim())}>
        Save name
      </Button>
    </div>
  );
}

/** The drilled-in category: its name, and the products that sit in it. */
function CategoryDetail({
  category,
  products,
  onRename,
}: {
  category: CategoryNode;
  products: AssignmentRow[];
  onRename: (categoryId: string, name: string) => void;
}) {
  return (
    <Section
      title={category.name}
      description={`${category.groupName ? `${category.groupName} · ` : ""}${products.length} products in this category`}
    >
      {/* Keyed so drilling into another category resets the name draft. */}
      <RenameCategoryForm key={category.id} category={category} onSave={(name) => onRename(category.id, name)} />
      {products.length === 0 ? (
        <p className="text-sm text-muted-foreground">No products are assigned to this category yet.</p>
      ) : (
        <ul className="flex flex-col">
          {products.map((product) => (
            <li
              key={product.productNameKey}
              className="flex items-center justify-between gap-3 border-b border-muted py-1.5 text-sm last:border-0"
            >
              <span className="truncate">{product.productNameKey}</span>
              <span className="shrink-0 text-xs text-muted-foreground">
                {product.confidence == null ? "—" : formatPercent(product.confidence)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

function AssignmentsTable({
  rows,
  categories,
  onMove,
  onClear,
}: {
  rows: AssignmentRow[];
  categories: CategoryNode[];
  onMove: (productNameKey: string, categoryId: string) => void;
  onClear: (productNameKey: string) => void;
}) {
  const columns = useMemo<ColumnDef<AssignmentRow>[]>(
    () => [
      {
        id: "product",
        accessorFn: (row) => row.productNameKey,
        header: "Product",
        cell: ({ row }) => <span className="font-medium">{row.original.productNameKey}</span>,
        meta: { title: "Product" },
      },
      {
        id: "category",
        accessorFn: (row) => row.categoryName ?? "",
        header: "Category",
        cell: ({ row }) =>
          row.original.categoryName ?? <span className="text-muted-foreground">Uncategorised</span>,
        meta: { title: "Category" },
      },
      {
        id: "group",
        accessorFn: (row) => row.groupName ?? "",
        header: "Group",
        cell: ({ row }) => row.original.groupName ?? <span className="text-muted-foreground">—</span>,
        meta: { title: "Group" },
      },
      {
        id: "confidence",
        accessorFn: (row) => row.confidence ?? -1,
        header: "Confidence",
        cell: ({ row }) => (
          <span className="tabular-nums">{formatPercent(row.original.confidence)}</span>
        ),
        meta: { title: "Confidence", align: "right" },
      },
      {
        id: "status",
        accessorFn: (row) => row.status,
        header: "Status",
        cell: ({ row }) => <StatusBadge status={row.original.status} />,
        meta: { title: "Status" },
      },
      {
        id: "move",
        header: "Move to",
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <MoveToCategory
              productNameKey={row.original.productNameKey}
              categoryId={row.original.categoryId}
              categories={categories}
              onMove={onMove}
              onClear={onClear}
            />
            {row.original.source === "MANUAL" ? (
              <Button
                variant="ghost"
                size="sm"
                aria-label={`Clear the manual override for ${row.original.productNameKey}`}
                onClick={() => onClear(row.original.productNameKey)}
              >
                Clear override
              </Button>
            ) : null}
          </div>
        ),
        meta: { title: "Move to" },
      },
    ],
    [categories, onMove, onClear],
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(row) => row.productNameKey}
      filterColumn="product"
      filterPlaceholder="Filter products"
      emptyMessage="No products have been classified yet."
    />
  );
}

function CategoriesTab({
  categories,
  rows,
  selectedCategoryId,
  onSelectCategory,
  onRename,
  onMove,
  onClear,
}: {
  categories: CategoryNode[];
  rows: AssignmentRow[];
  selectedCategoryId: string | null;
  onSelectCategory: (categoryId: string | null) => void;
  onRename: (categoryId: string, name: string) => void;
  onMove: (productNameKey: string, categoryId: string) => void;
  onClear: (productNameKey: string) => void;
}) {
  const needsReview = rows.filter((row) => row.status === "NEEDS_REVIEW").length;
  const unassigned = rows.filter((row) => row.status === "UNASSIGNED").length;
  const selected = categories.find((category) => category.id === selectedCategoryId) ?? null;

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Categories" value={String(categories.length)} />
        <StatCard label="Products" value={String(rows.length)} />
        <StatCard label="Needs review" value={String(needsReview)} hint="Low-confidence assignments" />
        <StatCard label="Unassigned" value={String(unassigned)} hint="No category yet" />
      </div>

      <Section
        title="Taxonomy"
        description="Groups and curated categories, with product counts. Flat categories have no group; click one to drill in."
      >
        <TaxonomyTree
          categories={categories}
          onDrill={(categoryId) => onSelectCategory(categoryId === selectedCategoryId ? null : categoryId)}
        />
      </Section>

      {selected ? (
        <CategoryDetail
          category={selected}
          products={rows.filter((row) => row.categoryId === selected.id)}
          onRename={onRename}
        />
      ) : null}

      <Section title="Assignments" description="Every product's category, confidence and status. Move a product to fix it.">
        <AssignmentsTable rows={rows} categories={categories} onMove={onMove} onClear={onClear} />
      </Section>
    </div>
  );
}

function ClustersTab({
  dendrogram,
  points,
  rows,
  categories,
  onCurate,
  onMove,
  onClear,
}: {
  dendrogram: DendrogramNode[];
  points: ProductPoint[];
  rows: AssignmentRow[];
  categories: CategoryNode[];
  onCurate: (action: DendroAction, selection: DendroSelection, name?: string) => void;
  onMove: (productNameKey: string, categoryId: string) => void;
  onClear: (productNameKey: string) => void;
}) {
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const selected = points.find((point) => point.productNameKey === selectedKey) ?? null;
  const categoryName = (id: string | null) =>
    id == null ? "No category yet" : categories.find((c) => c.id === id)?.name ?? id;

  return (
    <div className="flex flex-col gap-6">
      <Section
        title="Clusters"
        description="The merge tree for the latest run. Select a node to name, merge or split a cluster."
      >
        <DendrogramView nodes={dendrogram} assignments={rows} onCurate={onCurate} />
      </Section>

      <Section
        title="Embedding space"
        description="Two-dimensional projection of the product embeddings, coloured by category. Select a point to move that product."
      >
        <EmbeddingScatter points={points} categories={categories} onSelect={(point) => setSelectedKey(point.productNameKey)} />
        {selected ? (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border bg-card p-3">
            <span className="text-sm font-medium">{selected.productNameKey}</span>
            {selected.confidence == null ? null : (
              <Badge variant="outline">{formatPercent(selected.confidence)}</Badge>
            )}
            <Badge variant={selected.categoryId ? "success" : "missing"}>
              {categoryName(selected.categoryId)}
            </Badge>
            <MoveToCategory
              productNameKey={selected.productNameKey}
              categoryId={selected.categoryId}
              categories={categories}
              onMove={onMove}
              onClear={onClear}
            />
          </div>
        ) : null}
      </Section>
    </div>
  );
}

/** Drilling a pipeline stage opens the tab that shows that stage's data. */
const STAGE_TAB: Record<PipelineStage, Tab> = {
  products: "categories",
  embed: "clusters",
  cluster: "clusters",
  curate: "categories",
  assign: "categories",
  report: "pipeline",
};

const RUN_STATUS_VARIANT: Record<
  ClassificationRunStatus,
  "success" | "failed" | "info" | "conflict"
> = {
  RUNNING: "info",
  SUCCEEDED: "success",
  FAILED: "failed",
  PARTIAL: "conflict",
};

const RUN_KIND_LABEL: Record<ClassificationRunKind, string> = {
  FULL: "Full pipeline",
  CLUSTER: "Cluster only",
  ASSIGN: "Assign only",
};

/**
 * The latest run's status and any failure reason, shown in the Pipeline tab so a run that fails
 * in the background is not invisible (its message is recorded in `classification_run.error_message`).
 */
function RunStatusLine({ run }: { run: ClassificationRun | null }) {
  if (!run) return null;
  return (
    <div data-testid="latest-run-status" className="flex flex-wrap items-center gap-2 text-sm">
      <Badge variant={RUN_STATUS_VARIANT[run.status]}>{run.status}</Badge>
      <span className="font-medium">{RUN_KIND_LABEL[run.kind]}</span>
      {run.status === "SUCCEEDED" && run.productCount != null ? (
        <span className="text-muted-foreground">
          {run.productCount} products · {formatDateTime(run.finishedAt ?? run.startedAt)}
        </span>
      ) : null}
      {run.status === "RUNNING" ? (
        <span className="text-muted-foreground">Running — this can take a moment.</span>
      ) : null}
      {run.status === "FAILED" && run.errorMessage ? (
        <span role="alert" className="text-destructive">
          {run.errorMessage}
        </span>
      ) : null}
    </div>
  );
}

/** The run trigger: a primary FULL button plus a small menu for CLUSTER/ASSIGN. */
function RunClassificationMenu({
  onRun,
  starting,
}: {
  onRun: (kind: ClassificationRunKind) => void;
  starting: boolean;
}) {
  return (
    <div className="flex items-center">
      <Button
        variant="brand"
        className="rounded-r-none"
        onClick={() => onRun("FULL")}
        disabled={starting}
      >
        {starting ? "Starting…" : "Run classification"}
      </Button>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="brand"
            size="icon"
            className="rounded-l-none border-l border-brand-foreground/25"
            aria-label="More classification run options"
            disabled={starting}
          >
            <ChevronDown />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onRun("CLUSTER")}>
            {RUN_KIND_LABEL.CLUSTER}
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => onRun("ASSIGN")}>
            {RUN_KIND_LABEL.ASSIGN}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}

export default function ProductsPage() {
  // All four datasets are read once, because the pipeline flow summarises all of them and
  // every curation write re-reads them together.
  const taxonomy = useApiData((api) => api.getCategoryTaxonomy());
  const assignments = useApiData((api) => api.getCategoryAssignments());
  const points = useApiData((api) => api.getCategoryEmbeddingSpace());
  const dendrogram = useApiData((api) => api.getCategoryDendrogram());
  const runsQuery = useApiData((api) => api.listClassificationRuns());

  const [tab, setTab] = useState<Tab>("categories");
  const [selectedCategoryId, setSelectedCategoryId] = useState<string | null>(null);
  const [writeError, setWriteError] = useState<string | null>(null);
  const [runStarting, setRunStarting] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);
  const [activeRun, setActiveRun] = useState<ClassificationRun | null>(null);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const api = useApi();

  const reloadAll = useCallback(async () => {
    await Promise.all([taxonomy.reload(), assignments.reload(), points.reload(), dendrogram.reload()]);
  }, [taxonomy, assignments, points, dendrogram]);

  // Clear any in-flight poll on unmount, mirroring the forecasting page.
  useEffect(() => {
    return () => {
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, []);

  /** Every write goes through the same Api the page reads from, then the data re-reads. */
  const write = useCallback(
    async (label: string, operation: () => Promise<void>) => {
      setWriteError(null);
      try {
        await operation();
        await reloadAll();
      } catch (e: unknown) {
        setWriteError(isApiError(e) ? `${label} failed: ${e.message}` : `${label} failed.`);
      }
    },
    [reloadAll],
  );

  const rename = useCallback(
    (categoryId: string, name: string) => write("Rename", () => api.renameCategory(categoryId, { name })),
    [api, write],
  );
  const move = useCallback(
    (productNameKey: string, categoryId: string) =>
      write("Move", () => api.moveProduct(productNameKey, categoryId)),
    [api, write],
  );
  const clearOverride = useCallback(
    (productNameKey: string) => write("Clearing the override", () => api.clearProductOverride(productNameKey)),
    [api, write],
  );

  const curateCluster = useCallback(
    async (action: DendroAction, selection: DendroSelection, name?: string) => {
      if (action === "rename") {
        const categoryId = selection.categoryId;
        const newName = name?.trim();
        if (categoryId == null || newName == null || newName === "") return;
        await write("Rename", () => api.renameCategory(categoryId, { name: newName }));
        return;
      }
      if (action === "merge") {
        // The cluster's label is its first name; the curator renames it afterwards.
        const label = selection.node.productNameKey ?? `Merge of ${selection.node.size}`;
        const leaves = selection.leaves;
        await write("Merge", async () => {
          const created = await api.createCategory({
            name: label,
            groupName: null,
            displayOrder: taxonomy.data?.length ?? 0,
          });
          for (const leaf of leaves) {
            if (leaf.productNameKey != null) await api.moveProduct(leaf.productNameKey, created.id);
          }
        });
        return;
      }
      const leaves = selection.leaves;
      await write("Split", async () => {
        for (const leaf of leaves) {
          if (leaf.productNameKey != null) await api.clearProductOverride(leaf.productNameKey);
        }
      });
    },
    [api, taxonomy.data, write],
  );

  /** Polls the runs list until the just-submitted run leaves RUNNING, then refreshes the screen. */
  const pollLatestRun = useCallback(async () => {
    try {
      const runs = await api.listClassificationRuns();
      const latest = runs[0] ?? null;
      setActiveRun(latest);
      if (latest && latest.status === "RUNNING") {
        pollTimer.current = setTimeout(() => void pollLatestRun(), 2000);
      } else {
        void runsQuery.reload();
        if (latest && latest.status === "SUCCEEDED") {
          await reloadAll();
        }
      }
    } catch {
      // The list stays at its last known state; a later refresh recovers it.
    }
  }, [api, reloadAll, runsQuery]);

  const handleRun = useCallback(
    async (kind: ClassificationRunKind) => {
      setRunStarting(true);
      setRunError(null);
      setActiveRun(null);
      try {
        await api.runClassification(kind);
        void pollLatestRun();
      } catch (e) {
        setRunError(isApiError(e) ? e.message : "Couldn't start the classification run.");
      } finally {
        setRunStarting(false);
      }
    },
    [api, pollLatestRun],
  );

  const loading = taxonomy.loading || assignments.loading || points.loading || dendrogram.loading;
  const error = taxonomy.error ?? assignments.error ?? points.error ?? dendrogram.error;
  const categories = taxonomy.data ?? [];
  const rows = assignments.data ?? [];
  const scatterPoints = points.data ?? [];
  const clusterNodes = dendrogram.data ?? [];

  const header = (
    <PageHeader
      title="Products"
      description="Internal categorisation: how products are grouped, and what needs a decision."
      actions={<RunClassificationMenu onRun={(kind) => void handleRun(kind)} starting={runStarting} />}
    />
  );

  if (loading) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        <LoadingState rows={5} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col gap-6">
        {header}
        <ErrorState title="Couldn't load products" message={error.message} onRetry={() => void reloadAll()} />
      </div>
    );
  }

  const hasAny =
    categories.length > 0 || rows.length > 0 || scatterPoints.length > 0 || clusterNodes.length > 0;

  const latestRun = activeRun ?? runsQuery.data?.[0] ?? null;

  return (
    <div className="flex flex-col gap-6">
      {header}
      {writeError ? <InlineError>{writeError}</InlineError> : null}
      {runError ? <InlineError>{runError}</InlineError> : null}

      {hasAny ? (
        <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
          <TabsList aria-label="Product categorisation views">
            <TabsTrigger value="categories">Categories</TabsTrigger>
            <TabsTrigger value="clusters">Clusters</TabsTrigger>
            <TabsTrigger value="pipeline">Pipeline</TabsTrigger>
          </TabsList>

          <TabsContent value="categories">
            <CategoriesTab
              categories={categories}
              rows={rows}
              selectedCategoryId={selectedCategoryId}
              onSelectCategory={setSelectedCategoryId}
              onRename={rename}
              onMove={move}
              onClear={clearOverride}
            />
          </TabsContent>

          <TabsContent value="clusters">
            <ClustersTab
              dendrogram={clusterNodes}
              points={scatterPoints}
              rows={rows}
              categories={categories}
              onCurate={curateCluster}
              onMove={move}
              onClear={clearOverride}
            />
          </TabsContent>

          <TabsContent value="pipeline">
            <Section
              title="Pipeline"
              description="products → embed → cluster → curate → assign → report. Click a stage to jump to its data."
            >
              <RunStatusLine run={latestRun} />
              <PipelineFlow
                categories={categories}
                assignments={rows}
                points={scatterPoints}
                dendrogram={clusterNodes}
                onDrill={(stage) => setTab(STAGE_TAB[stage])}
              />
            </Section>
          </TabsContent>
        </Tabs>
      ) : (
        <div className="flex flex-col gap-4">
          <RunStatusLine run={latestRun} />
          <AwaitingData
            label="Products"
            description="The product taxonomy, the assignment queue and the clustering views."
            reason="No classification run has produced data yet."
            icon={Box}
          />
        </div>
      )}
    </div>
  );
}
