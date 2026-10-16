"use client";

import { useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { isApiError, ApiError } from "@/lib/api";
import { DataQualifications } from "@/components/data-display/data-qualifications";
import { EMPTY, formatNumber } from "@/lib/format";
import type {
  QueryAggregate,
  QueryDataset,
  QueryField,
  QueryFilterNode,
  QueryFilterOp,
  QueryGroupByItem,
  QueryOrderByItem,
  QueryPlan,
  QueryPredicate,
  QueryResult,
  QuerySelectItem,
  SavedQueryDetail,
} from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { LoadingState } from "@/components/states/loading-state";
import { ErrorState } from "@/components/states/error-state";
import { PermissionDenied } from "@/components/states/permission-denied";

const AGGREGATES: QueryAggregate[] = ["SUM", "AVG", "MIN", "MAX", "COUNT", "COUNT_DISTINCT"];
const OPS: QueryFilterOp[] = ["EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "IN", "IS_NULL"];

/** The relational query builder: discover → build → run → render, plus save/load. */
export function QueryBuilder() {
  const api = useApi();
  const datasets = useApiData((api) => api.listQueryDatasets(), []);
  const relationships = useApiData((api) => api.listQueryRelationships(), []);
  const saved = useApiData((api) => api.listSavedQueries(), []);

  const [dataset, setDataset] = useState("");
  const fields = useApiData(
    (api) => (dataset ? api.listQueryFields(dataset) : Promise.resolve([])),
    [dataset],
  );
  const selectedDataset = datasets.data?.find((d) => d.id === dataset);

  const [select, setSelect] = useState<QuerySelectItem[]>([]);
  const [filters, setFilters] = useState<QueryPredicate[]>([]);
  const [groupBy, setGroupBy] = useState<QueryGroupByItem[]>([]);
  const [orderBy, setOrderBy] = useState<QueryOrderByItem[]>([]);
  const [joins, setJoins] = useState<string[]>([]);
  const [limit, setLimit] = useState(100);
  const [title, setTitle] = useState("");

  const [result, setResult] = useState<QueryResult | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  function plan(): QueryPlan {
    return { version: 1, dataset, joins, select, groupBy, orderBy, limit, offset: 0, filter: andFilter(filters) };
  }

  async function run() {
    setRunning(true);
    setError(null);
    setResult(null);
    try {
      setResult(await api.runQuery(plan()));
    } catch (e) {
      setError(isApiError(e) ? e : new ApiError("UNEXPECTED_STATUS", "Something went wrong."));
    } finally {
      setRunning(false);
    }
  }

  async function save() {
    const t = title.trim() || `Query ${new Date().toLocaleDateString()}`;
    const created = await api.createSavedQuery({ title: t, plan: plan() });
    setTitle("");
    await saved.reload();
    await loadSaved(created);
  }

  async function loadSaved(detail: SavedQueryDetail) {
    setDataset(detail.plan.dataset);
    setSelect(detail.plan.select);
    setJoins(detail.plan.joins ?? []);
    setGroupBy(detail.plan.groupBy ?? []);
    setOrderBy(detail.plan.orderBy ?? []);
    setLimit(detail.plan.limit);
    setFilters(flattenAnd(detail.plan.filter));
    setResult(null);
    setError(null);
  }

  const joinOptions = relationships.data?.filter((r) => r.from === dataset) ?? [];
  const dimensionFields = fields.data?.filter((f) => f.kind === "DIMENSION") ?? [];

  if (datasets.loading || relationships.loading) return <LoadingState rows={4} />;
  if (datasets.error) {
    if (datasets.error.code === "NOT_PERMITTED" || datasets.data?.length === 0)
      return (
        <PermissionDenied
          subject="query datasets"
          message="No datasets are available to your role."
        />
      );
    return (
      <ErrorState
        title="Couldn't load datasets"
        message={datasets.error.message}
        onRetry={datasets.reload}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>Relational query</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <DatasetPicker
            datasets={datasets.data ?? []}
            value={dataset}
            onChange={(v) => {
              setDataset(v);
              setSelect([]);
              setGroupBy([]);
              setFilters([]);
              setJoins([]);
              setOrderBy([]);
              setResult(null);
            }}
          />

          {selectedDataset ? <DatasetMeta dataset={selectedDataset} /> : null}

          {fields.loading ? <LoadingState rows={3} /> : null}
          {fields.error ? (
            fields.error.code === "NOT_PERMITTED" ? (
              <PermissionDenied message={fields.error.message} />
            ) : (
              <ErrorState title="Couldn't load fields" message={fields.error.message} onRetry={fields.reload} />
            )
          ) : null}

          {fields.data && fields.data.length > 0 ? (
            <>
              <FieldSelector
                fields={fields.data}
                select={select}
                onChange={setSelect}
              />
              <FilterBuilder
                fields={fields.data}
                filters={filters}
                onChange={setFilters}
              />
              <div className="flex flex-wrap items-end gap-4">
                <GroupByPicker
                  fields={dimensionFields}
                  groupBy={groupBy}
                  onChange={setGroupBy}
                />
                <OrderByPicker
                  fields={fields.data}
                  orderBy={orderBy}
                  onChange={setOrderBy}
                />
                {joinOptions.length > 0 ? (
                  <JoinPicker
                    relationships={joinOptions}
                    joins={joins}
                    onChange={setJoins}
                  />
                ) : null}
                <label className="flex flex-col gap-1 text-xs text-muted-foreground">
                  Limit
                  <Input
                    type="number"
                    min={1}
                    max={1000}
                    value={limit}
                    onChange={(e) => setLimit(Number(e.target.value) || 100)}
                    className="w-24"
                  />
                </label>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <Button onClick={run} disabled={running || !dataset || select.length === 0}>
                  {running ? "Running…" : "Run query"}
                </Button>
                <Input
                  aria-label="Saved query title"
                  placeholder="Save as…"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="w-48"
                />
                <Button variant="outline" onClick={save} disabled={!dataset || select.length === 0}>
                  Save
                </Button>
                {saved.data && saved.data.length > 0 ? (
                  <select
                    aria-label="Load saved query"
                    defaultValue=""
                    onChange={async (e) => {
                      const id = e.target.value;
                      if (!id) return;
                      const detail = await api.getSavedQuery(id);
                      await loadSaved(detail);
                    }}
                    className="rounded-md border bg-background px-2 py-1 text-sm"
                  >
                    <option value="">Load saved query…</option>
                    {saved.data.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.title}
                      </option>
                    ))}
                  </select>
                ) : null}
              </div>
            </>
          ) : null}
        </CardContent>
      </Card>

      {error ? <QueryError error={error} /> : null}
      {result ? <QueryResultView result={result} /> : null}
    </div>
  );
}

function andFilter(filters: QueryPredicate[]): QueryFilterNode | null {
  if (filters.length === 0) return null;
  return { logic: "AND", children: filters.map((p) => ({ predicate: p })) };
}

function flattenAnd(filter: QueryPlan["filter"]): QueryPredicate[] {
  if (!filter) return [];
  if (filter.logic === "AND" && filter.children) {
    return filter.children.map((c) => c.predicate).filter((p): p is QueryPredicate => !!p);
  }
  return filter.predicate ? [filter.predicate] : [];
}

function DatasetPicker({
  datasets,
  value,
  onChange,
}: {
  datasets: QueryDataset[];
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <label className="flex flex-col gap-1 text-sm">
      Dataset
      <select aria-label="Dataset" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Choose a dataset…</option>
        {datasets.map((d) => (
          <option key={d.id} value={d.id}>
            {d.id}
          </option>
        ))}
      </select>
    </label>
  );
}

function DatasetMeta({ dataset }: { dataset: QueryDataset }) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
      <Badge variant="outline">{dataset.readMode}</Badge>
      <span>grain: {dataset.grain}</span>
      {dataset.currentFact ? <span>current-fact</span> : null}
      <span>{dataset.fields.length} fields</span>
    </div>
  );
}

function FieldSelector({
  fields,
  select,
  onChange,
}: {
  fields: QueryField[];
  select: QuerySelectItem[];
  onChange: (v: QuerySelectItem[]) => void;
}) {
  function toggle(field: QueryField) {
    const existing = select.find((s) => s.field === field.name);
    if (existing) onChange(select.filter((s) => s.field !== field.name));
    else
      onChange([
        ...select,
        { field: field.name, aggregate: field.kind === "MEASURE" ? "SUM" : null, distinct: false },
      ]);
  }
  function setAggregate(field: string, aggregate: QueryAggregate | null) {
    onChange(select.map((s) => (s.field === field ? { ...s, aggregate } : s)));
  }
  function setDistinct(field: string, distinct: boolean) {
    onChange(select.map((s) => (s.field === field ? { ...s, distinct } : s)));
  }
  return (
    <div className="flex flex-col gap-2">
      <span className="text-xs font-medium text-muted-foreground">Columns</span>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {fields.map((f) => {
          const sel = select.find((s) => s.field === f.name);
          return (
            <div key={f.name} className="flex items-center gap-2 rounded border px-2 py-1">
              <input
                type="checkbox"
                aria-label={`Select ${f.name}`}
                checked={!!sel}
                onChange={() => toggle(f)}
              />
              <span className="flex-1 text-sm">
                {f.name}
                {f.unit ? <span className="text-xs text-muted-foreground"> ({f.unit})</span> : null}
              </span>
              <Badge variant="secondary" className="text-[10px]">
                {f.type}
              </Badge>
              {sel ? (
                <select
                  aria-label={`Aggregate for ${f.name}`}
                  value={sel.aggregate ?? ""}
                  onChange={(e) => setAggregate(f.name, (e.target.value || null) as QueryAggregate | null)}
                  className="w-24"
                >
                  <option value="">none</option>
                  {AGGREGATES.map((a) => (
                    <option key={a} value={a}>
                      {a}
                    </option>
                  ))}
                </select>
              ) : null}
              {sel?.aggregate ? (
                <label className="text-[10px] text-muted-foreground">
                  <input
                    type="checkbox"
                    aria-label={`Distinct ${f.name}`}
                    checked={!!sel.distinct}
                    onChange={(e) => setDistinct(f.name, e.target.checked)}
                  />{" "}
                  distinct
                </label>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function FilterBuilder({
  fields,
  filters,
  onChange,
}: {
  fields: QueryField[];
  filters: QueryPredicate[];
  onChange: (v: QueryPredicate[]) => void;
}) {
  const [field, setField] = useState("");
  const [op, setOp] = useState<QueryFilterOp>("EQ");
  const [value, setValue] = useState("");
  const [value2, setValue2] = useState("");

  function add() {
    if (!field) return;
    const typed = fields.find((f) => f.name === field);
    let v: QueryPredicate["value"] = value;
    if (op === "BETWEEN") v = [value, value2];
    if (op === "IN") v = value.split(",").map((s) => s.trim());
    if (op === "IS_NULL") v = null;
    if (typed?.type === "BOOLEAN" && op !== "IS_NULL") v = value === "true";
    onChange([...filters, { field, op, value: v }]);
    setValue("");
    setValue2("");
  }
  function remove(index: number) {
    onChange(filters.filter((_, i) => i !== index));
  }

  return (
    <div className="flex flex-col gap-2">
      <span className="text-xs font-medium text-muted-foreground">Filters (AND)</span>
      {filters.map((f, i) => (
        <div key={i} className="flex items-center gap-2 text-sm">
          <span className="font-mono text-xs">{f.field} {f.op}</span>
          <span className="font-mono text-xs">{JSON.stringify(f.value)}</span>
          <Button variant="ghost" size="sm" onClick={() => remove(i)}>
            remove
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap items-center gap-2">
        <select aria-label="Filter field" value={field} onChange={(e) => setField(e.target.value)} className="w-40">
          <option value="">field…</option>
          {fields.map((f) => (
            <option key={f.name} value={f.name}>
              {f.name}
            </option>
          ))}
        </select>
        <select aria-label="Filter op" value={op} onChange={(e) => setOp(e.target.value as QueryFilterOp)} className="w-32">
          {OPS.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
        {op !== "IS_NULL" ? (
          <>
            <Input
              aria-label="Filter value"
              placeholder={op === "IN" ? "a, b, c" : "value"}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              className="w-40"
            />
            {op === "BETWEEN" ? (
              <Input
                aria-label="Filter value 2"
                placeholder="to"
                value={value2}
                onChange={(e) => setValue2(e.target.value)}
                className="w-40"
              />
            ) : null}
          </>
        ) : null}
        <Button variant="outline" size="sm" onClick={add} disabled={!field}>
          Add filter
        </Button>
      </div>
    </div>
  );
}

function GroupByPicker({
  fields,
  groupBy,
  onChange,
}: {
  fields: QueryField[];
  groupBy: QueryGroupByItem[];
  onChange: (v: QueryGroupByItem[]) => void;
}) {
  function toggle(field: QueryField) {
    if (groupBy.some((g) => g.field === field.name))
      onChange(groupBy.filter((g) => g.field !== field.name));
    else onChange([...groupBy, { field: field.name, grain: field.type === "DATE" ? "DAY" : null }]);
  }
  function setGrain(field: string, grain: "DAY" | "MONTH" | null) {
    onChange(groupBy.map((g) => (g.field === field ? { ...g, grain } : g)));
  }
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-muted-foreground">Group by</span>
      {fields.map((f) => {
        const g = groupBy.find((x) => x.field === f.name);
        return (
          <label key={f.name} className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={!!g} onChange={() => toggle(f)} />
            {f.name}
            {g && f.type === "DATE" ? (
              <select
                aria-label={`Grain for ${f.name}`}
                value={g.grain ?? "DAY"}
                onChange={(e) => setGrain(f.name, (e.target.value || null) as "DAY" | "MONTH" | null)}
                className="w-24"
              >
                <option value="DAY">DAY</option>
                <option value="MONTH">MONTH</option>
              </select>
            ) : null}
          </label>
        );
      })}
    </div>
  );
}

function OrderByPicker({
  fields,
  orderBy,
  onChange,
}: {
  fields: QueryField[];
  orderBy: QueryOrderByItem[];
  onChange: (v: QueryOrderByItem[]) => void;
}) {
  const [field, setField] = useState("");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-muted-foreground">Order by</span>
      {orderBy.map((o, i) => (
        <div key={i} className="text-sm">
          {o.field} {o.direction}{" "}
          <Button variant="ghost" size="sm" onClick={() => onChange(orderBy.filter((_, j) => j !== i))}>
            ×
          </Button>
        </div>
      ))}
      <div className="flex items-center gap-2">
        <select aria-label="Order field" value={field} onChange={(e) => setField(e.target.value)} className="w-40">
          <option value="">field…</option>
          {fields.map((f) => (
            <option key={f.name} value={f.name}>
              {f.name}
            </option>
          ))}
        </select>
        <select aria-label="Order direction" value={direction} onChange={(e) => setDirection(e.target.value as "ASC" | "DESC")} className="w-24">
          <option value="DESC">DESC</option>
          <option value="ASC">ASC</option>
        </select>
        <Button variant="outline" size="sm" onClick={() => field && onChange([...orderBy, { field, direction }])}>
          Add
        </Button>
      </div>
    </div>
  );
}

function JoinPicker({
  relationships,
  joins,
  onChange,
}: {
  relationships: { id: string; to: string; cardinality: string; fanoutRisk: boolean }[];
  joins: string[];
  onChange: (v: string[]) => void;
}) {
  function toggle(id: string) {
    if (joins.includes(id)) onChange(joins.filter((j) => j !== id));
    else onChange([...joins, id]);
  }
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-muted-foreground">Join</span>
      {relationships.map((r) => (
        <label key={r.id} className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={joins.includes(r.id)} onChange={() => toggle(r.id)} />
          → {r.to}
          <Badge variant="outline" className="text-[10px]">
            {r.cardinality}
          </Badge>
          {r.fanoutRisk ? (
            <Badge variant="secondary" className="text-[10px]">
              fanout
            </Badge>
          ) : null}
        </label>
      ))}
    </div>
  );
}

function QueryError({ error }: { error: ApiError }) {
  if (error.code === "NOT_PERMITTED") return <PermissionDenied message={error.message} />;
  return (
    <Card className="border-destructive">
      <CardHeader>
        <CardTitle className="text-destructive">{error.code}</CardTitle>
      </CardHeader>
      <CardContent className="text-sm">{error.message}</CardContent>
    </Card>
  );
}

function QueryResultView({ result }: { result: QueryResult }) {
  const [view, setView] = useState<"table" | "chart">("table");
  const canChart = result.columns.length === 2;
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Result</CardTitle>
          <div className="flex items-center gap-2">
            {canChart ? (
              <Button variant="outline" size="sm" onClick={() => setView(view === "table" ? "chart" : "table")}>
                {view === "table" ? "Chart" : "Table"}
              </Button>
            ) : null}
            <Badge variant="outline">{result.semantics.readMode}</Badge>
            <span className="text-xs text-muted-foreground">grain: {result.semantics.grain}</span>
            {result.pagination.truncated ? <Badge variant="secondary">truncated</Badge> : null}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <DataQualifications notices={result.notices} />
        {view === "table" || !canChart ? (
          <div className="overflow-auto rounded border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/40 text-left text-xs text-muted-foreground">
                  {result.columns.map((c) => (
                    <th key={c.name} className="px-3 py-2 font-medium">
                      {c.name}
                      {c.unit ? ` (${c.unit})` : ""}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {result.rows.map((row, i) => (
                  <tr key={i} className="border-b last:border-0">
                    {row.map((cell, j) => (
                      <td key={j} className="px-3 py-2 font-mono text-xs">
                        {cell == null ? EMPTY : typeof cell === "number" ? formatNumber(cell, 2) : String(cell)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <ChartView result={result} />
        )}
        {result.rows.length === 0 ? <p className="text-sm text-muted-foreground">No rows.</p> : null}
      </CardContent>
    </Card>
  );
}

function ChartView({ result }: { result: QueryResult }) {
  const data = result.rows.map((row) => ({
    x: String(row[0]),
    y: typeof row[1] === "number" ? row[1] : 0,
  }));
  return (
    <div className="h-64">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="x" />
          <YAxis />
          <Tooltip />
          <Bar dataKey="y" fill="hsl(var(--chart-1))" />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
