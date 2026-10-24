"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Play, RefreshCw, Save } from "lucide-react";
import { useApi } from "@/lib/demo-mode";
import type {
  QueryDataset,
  QueryField,
  QueryPlan,
  QueryRelationship,
  SavedQueryDetail,
} from "@/lib/api/types";
import { humanizeKey } from "@/lib/format";
import { useToast } from "@/components/feedback/toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { SelectItem } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import {
  FieldSelector,
  FilterBuilder,
  GroupByPicker,
  OrderByPicker,
  QuerySelect,
} from "./query-controls";
import { QueryResultView } from "./query-result";

/** Defaults use authorised field metadata, so newly registered datasets need no UI configuration. */
export function defaultQuery(dataset: string, fields: QueryField[]): QueryPlan {
  const required = fields.filter((field) => !field.nullable);
  const optional = fields.filter((field) => field.nullable);
  return {
    version: 1,
    dataset,
    select: [...required, ...optional]
      .slice(0, 4)
      .map((field) => ({ field: field.name, aggregate: null })),
    joins: [],
    groupBy: [],
    orderBy: [],
    filter: null,
    limit: 100,
    offset: 0,
  };
}

export function QueryBuilder() {
  const api = useApi();
  const datasets = useQuery({
    queryKey: ["listQueryDatasets"],
    queryFn: ({ signal }) => api.listQueryDatasets({ signal }),
  });
  const relationships = useQuery({
    queryKey: ["listQueryRelationships"],
    queryFn: ({ signal }) => api.listQueryRelationships({ signal }),
  });
  const saved = useQuery({
    queryKey: ["listSavedQueries"],
    queryFn: ({ signal }) => api.listSavedQueries({ signal }),
  });
  const [datasetId, setDatasetId] = useState("");
  const [loadVersion, setLoadVersion] = useState(0);
  const [loaded, setLoaded] = useState<SavedQueryDetail | null>(null);
  const selected =
    datasets.data?.find((dataset) => dataset.id === datasetId) ??
    datasets.data?.[0];
  const load = useMutation({
    mutationFn: (id: string) => api.getSavedQuery(id),
    onSuccess: (detail) => {
      setLoaded(detail);
      setDatasetId(detail.plan.dataset);
      setLoadVersion((version) => version + 1);
    },
  });

  if (datasets.isPending) return <LoadingState rows={4} />;
  if (datasets.error)
    return (
      <ErrorState
        title="Couldn't load datasets"
        message={datasets.error.message}
        onRetry={datasets.refetch}
      />
    );
  if (!selected)
    return (
      <PermissionDenied
        subject="query datasets"
        message="No datasets are available to your role."
      />
    );
  const unavailable =
    loaded &&
    !datasets.data?.some((dataset) => dataset.id === loaded.plan.dataset);

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader>
          <CardTitle>Relational query</CardTitle>
          <CardDescription>
            Explore rows or build a custom summary. Choose your columns, narrow
            the data, then run a read-only query.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <QuerySelect
              label="Dataset"
              description="The starting set of records for your query."
              value={selected.id}
              onChange={(id) => {
                setDatasetId(id);
                setLoaded(null);
                load.reset();
              }}
              disabled={load.isPending}
            >
              {datasets.data?.map((dataset) => (
                <SelectItem key={dataset.id} value={dataset.id}>
                  {humanizeKey(dataset.id)}
                </SelectItem>
              ))}
            </QuerySelect>
            <QuerySelect
              label="Load saved query"
              description="Loads the saved setup. Run it to fetch current data."
              value=""
              onChange={(id) => load.mutate(id)}
              placeholder={
                load.isPending ? "Loading…" : "Choose a saved query…"
              }
              disabled={load.isPending || !saved.data?.length}
            >
              {saved.data?.map((query) => (
                <SelectItem key={query.id} value={query.id}>
                  {query.title}
                </SelectItem>
              ))}
            </QuerySelect>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <Badge
              variant={selected.readMode === "RESOLVED" ? "info" : "neutral"}
            >
              {selected.readMode === "RESOLVED"
                ? "Resolved data"
                : "Source observations"}
            </Badge>
            <span>One row represents {selected.grain}.</span>
          </div>
          {selected.readMode === "CANONICAL_DIRECT" ? (
            <p className="text-xs text-muted-foreground">
              These are source observations with unverified coverage. They are
              not reconciled business metrics.
            </p>
          ) : null}
          {saved.error ? (
            <p role="alert" className="text-sm text-destructive">
              Saved queries could not be listed.{" "}
              <Button variant="link" onClick={() => saved.refetch()}>
                Retry
              </Button>
            </p>
          ) : null}
          {load.error ? (
            <p role="alert" className="text-sm text-destructive">
              Could not load query: {load.error.message}
            </p>
          ) : null}
          {unavailable ? (
            <p role="alert" className="text-sm text-destructive">
              This saved query’s dataset is no longer available to your role.
              Choose an available dataset to continue.
            </p>
          ) : (
            <DatasetEditor
              key={`${selected.id}:${loaded?.id ?? "new"}:${loadVersion}`}
              dataset={selected}
              initial={loaded?.plan}
              relationships={relationships.data ?? []}
              relationshipsError={relationships.error?.message}
              retryRelationships={() => {
                void relationships.refetch();
              }}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function DatasetEditor({
  dataset,
  initial,
  relationships,
  relationshipsError,
  retryRelationships,
}: {
  dataset: QueryDataset;
  initial?: QueryPlan;
  relationships: QueryRelationship[];
  relationshipsError?: string;
  retryRelationships: () => void;
}) {
  const api = useApi();
  const fields = useQuery({
    queryKey: ["listQueryFields", dataset.id],
    queryFn: ({ signal }) => api.listQueryFields(dataset.id, { signal }),
  });
  if (fields.isPending) return <LoadingState rows={3} />;
  if (fields.error)
    return fields.error.code === "NOT_PERMITTED" ? (
      <PermissionDenied message={fields.error.message} />
    ) : (
      <ErrorState
        title="Couldn't load fields"
        message={fields.error.message}
        onRetry={fields.refetch}
      />
    );
  if (!fields.data.length)
    return (
      <p className="text-sm text-muted-foreground">
        No fields are available to your role in this dataset.
      </p>
    );
  return (
    <QueryEditor
      dataset={dataset}
      fields={fields.data}
      initial={initial}
      relationships={relationships}
      relationshipsError={relationshipsError}
      retryRelationships={retryRelationships}
    />
  );
}

function QueryEditor({
  dataset,
  fields,
  initial,
  relationships,
  relationshipsError,
  retryRelationships,
}: {
  dataset: QueryDataset;
  fields: QueryField[];
  initial?: QueryPlan;
  relationships: QueryRelationship[];
  relationshipsError?: string;
  retryRelationships: () => void;
}) {
  const api = useApi();
  const { toast } = useToast();
  const cache = useQueryClient();
  const [draft, setDraft] = useState<QueryPlan>(
    () => initial ?? defaultQuery(dataset.id, fields),
  );
  const [applied, setApplied] = useState<QueryPlan | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [title, setTitle] = useState("");
  const result = useQuery({
    queryKey: ["relationalQuery", applied],
    queryFn: () => api.runQuery(applied!),
    enabled: applied !== null,
    staleTime: 0,
    refetchInterval: autoRefresh && applied ? 30_000 : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const save = useMutation({
    mutationFn: () =>
      api.createSavedQuery({
        title: title.trim() || `${humanizeKey(dataset.id)} query`,
        plan: draft,
      }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["listSavedQueries"] });
      setTitle("");
      toast({
        title: "Query saved",
        description:
          "The setup is saved; results are fetched each time you run it.",
        tone: "success",
      });
    },
  });
  const selectedFields = fields.filter((field) =>
    draft.select.some((item) => item.field === field.name),
  );
  const aggregate = draft.select.some((item) => item.aggregate);
  const ungrouped =
    aggregate &&
    draft.select.some(
      (item) =>
        !item.aggregate &&
        !draft.groupBy?.some((group) => group.field === item.field),
    );
  const invalid =
    !draft.select.length ||
    draft.select.length > 20 ||
    !Number.isInteger(draft.limit) ||
    draft.limit < 1 ||
    draft.limit > 1000 ||
    ungrouped ||
    (!aggregate && !!draft.groupBy?.length);
  const changed =
    applied !== null && JSON.stringify(draft) !== JSON.stringify(applied);
  const joinOptions = relationships.filter(
    (relationship) => relationship.from === dataset.id,
  );

  function run() {
    if (invalid) return;
    if (JSON.stringify(draft) === JSON.stringify(applied))
      void result.refetch();
    else setApplied(draft);
  }

  return (
    <div className="space-y-6 border-t pt-5">
      <section className="space-y-3" aria-label="Columns">
        <div>
          <h3 className="text-sm font-semibold">
            Columns{" "}
            <span className="font-normal text-muted-foreground">
              {draft.select.length} selected
            </span>
          </h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Start with up to four fields, preferring fields required by the
            schema. Original values show individual rows; calculations summarise
            them. Select up to 20 columns.
          </p>
        </div>
        <FieldSelector
          fields={fields}
          select={draft.select}
          onChange={(select) =>
            setDraft({
              ...draft,
              select,
              orderBy: draft.orderBy?.filter((item) =>
                select.some((column) => column.field === item.field),
              ),
            })
          }
        />
      </section>
      <section className="space-y-3 border-t pt-5" aria-label="Filters">
        <div>
          <h3 className="text-sm font-semibold">Filters (AND)</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Keep only records that match every filter. No filters includes all
            available dates and records.
          </p>
        </div>
        <FilterBuilder
          fields={fields}
          filter={draft.filter}
          onChange={(filter) => setDraft({ ...draft, filter })}
        />
      </section>
      <div className="grid gap-5 border-t pt-5 md:grid-cols-2 xl:grid-cols-3">
        <section className="space-y-3" aria-label="Grouping">
          <h3 className="text-sm font-semibold">Group by</h3>
          <GroupByPicker
            fields={fields.filter((field) => field.kind === "DIMENSION")}
            groupBy={draft.groupBy ?? []}
            onChange={(groupBy) => setDraft({ ...draft, groupBy })}
          />
        </section>
        <section className="space-y-3" aria-label="Sorting">
          <h3 className="text-sm font-semibold">Order by</h3>
          <OrderByPicker
            fields={selectedFields}
            orderBy={draft.orderBy ?? []}
            onChange={(orderBy) => setDraft({ ...draft, orderBy })}
          />
        </section>
        <section className="space-y-3" aria-label="Result size">
          <h3 className="text-sm font-semibold">Row limit</h3>
          <p id="query-limit-help" className="text-xs text-muted-foreground">
            Maximum result rows, from 1 to 1,000. For summaries this limits
            groups, not the records used to calculate each group.
          </p>
          <Input
            aria-label="Row limit"
            aria-describedby="query-limit-help"
            type="number"
            min={1}
            max={1000}
            step={1}
            value={draft.limit || ""}
            onChange={(event) =>
              setDraft({ ...draft, limit: Number(event.target.value) })
            }
          />
        </section>
      </div>
      {joinOptions.length || relationshipsError ? (
        <section className="space-y-3 border-t pt-5" aria-label="Relationships">
          <h3 className="text-sm font-semibold">Join related data</h3>
          <p className="text-xs text-muted-foreground">
            Joins match related records using predefined keys. One-to-many joins
            can repeat a starting record; the server rejects calculations that
            would double-count it. Maximum two joins.
          </p>
          {relationshipsError ? (
            <p role="alert" className="text-sm text-destructive">
              Relationships could not be loaded.{" "}
              <Button variant="link" onClick={retryRelationships}>
                Retry
              </Button>
            </p>
          ) : null}
          {joinOptions.map((relationship) => (
            <label
              key={relationship.id}
              className="flex items-start gap-3 rounded-lg border p-3 text-sm"
            >
              <Checkbox
                checked={draft.joins?.includes(relationship.id) ?? false}
                disabled={
                  !draft.joins?.includes(relationship.id) &&
                  (draft.joins?.length ?? 0) >= 2
                }
                onCheckedChange={(checked) =>
                  setDraft({
                    ...draft,
                    joins: checked
                      ? [...(draft.joins ?? []), relationship.id]
                      : draft.joins?.filter((id) => id !== relationship.id),
                  })
                }
              />
              <span>
                {humanizeKey(relationship.to)}
                <span className="mt-1 block text-xs text-muted-foreground">
                  {humanizeKey(relationship.cardinality)}
                  {relationship.fanoutRisk ? " · May repeat starting rows" : ""}
                </span>
              </span>
            </label>
          ))}
        </section>
      ) : null}
      <div className="space-y-3 rounded-lg bg-muted/40 p-4">
        {ungrouped ? (
          <p role="status" className="text-sm">
            Group every original-value column before combining it with a
            calculation.
          </p>
        ) : null}
        {!aggregate && draft.groupBy?.length ? (
          <p role="status" className="text-sm">
            Choose a calculation on at least one column to use grouping.
          </p>
        ) : null}
        <div className="flex flex-wrap items-center gap-4">
          <Button
            variant="brand"
            onClick={run}
            disabled={!!invalid || result.isFetching}
          >
            <span className="mr-2">
              {result.isFetching ? (
                <RefreshCw className="size-4 animate-spin" />
              ) : (
                <Play className="size-4" />
              )}
            </span>
            {result.isFetching ? "Running…" : "Run query"}
          </Button>
          <label className="flex items-center gap-2 text-sm">
            <Switch
              checked={autoRefresh}
              onCheckedChange={setAutoRefresh}
              aria-label="Auto-refresh every 30 seconds"
            />
            Auto-refresh every 30 seconds
          </label>
        </div>
        <p className="text-xs text-muted-foreground">
          Auto-refresh reruns the last submitted query while this page is
          visible. Run query to apply edits. It refreshes stored data; it does
          not pull new records from source systems.
        </p>
        <div className="flex flex-wrap items-center gap-2 border-t pt-3">
          <Input
            aria-label="Saved query title"
            placeholder="Name this query…"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            className="w-full sm:w-64"
          />
          <Button
            variant="outline"
            disabled={!!invalid || save.isPending}
            onClick={() => save.mutate()}
          >
            <Save className="mr-2 size-4" />
            {save.isPending ? "Saving…" : "Save query"}
          </Button>
          <span className="text-xs text-muted-foreground">
            Saves the current setup, including edits.
          </span>
        </div>
        {save.error ? (
          <p role="alert" className="text-sm text-destructive">
            Could not save query: {save.error.message}
          </p>
        ) : null}
      </div>
      {changed ? (
        <p role="status" className="text-sm text-muted-foreground">
          You have unapplied changes. Results and auto-refresh use the last
          submitted query.
        </p>
      ) : null}
      {result.error ? (
        <p role="alert" className="text-sm text-destructive">
          {result.data
            ? "Refresh failed; showing the last successful result. "
            : "Query failed. "}
          {result.error.message}
        </p>
      ) : null}
      {result.data ? (
        <QueryResultView
          result={result.data}
          refreshedAt={result.dataUpdatedAt}
        />
      ) : !result.isFetching ? (
        <p className="text-sm text-muted-foreground">
          Your query is ready. Run it to preview the results.
        </p>
      ) : null}
    </div>
  );
}
