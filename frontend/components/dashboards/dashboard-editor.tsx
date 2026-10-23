"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DataQualifications } from "@/components/data-display/data-qualifications";
import { useEffect, useRef, useState } from "react";
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Copy,
  History,
  Plus,
  RotateCcw,
  Save,
  Trash2,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { DatePicker } from "@/components/ui/date-picker";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import type {
  Api,
  DashboardDocument,
  DashboardFilters,
  MetricQuery,
  SavedWidget,
  SaveDashboardInput,
  Visibility,
} from "@/lib/api/types";

const RENDER_TYPE_LABELS: Record<string, string> = {
  stat: "Stat",
  "time-series": "Time series",
  "bar-chart": "Bar chart",
  table: "Table",
  "ranked-list": "Ranked list",
  query: "Saved query",
};

const COMPARISON_OPTIONS: { value: string; label: string }[] = [
  { value: "PREVIOUS_DAY", label: "Previous day" },
  { value: "PREVIOUS_WEEK", label: "Previous week" },
  { value: "SAME_WEEKDAY_LAST_WEEK", label: "Same weekday last week" },
  { value: "SAME_PERIOD_LAST_YEAR", label: "Same period last year" },
  { value: "ROLLING_4_WEEKS", label: "Rolling 4 weeks" },
  { value: "ROLLING_12_WEEKS", label: "Rolling 12 weeks" },
];

const DIMENSION_OPTIONS: { value: string; label: string }[] = [
  { value: "SERVICE_PERIOD", label: "Service period" },
  { value: "DEPARTMENT", label: "Department" },
  { value: "PRODUCT", label: "Product" },
];

const VISIBILITY_OPTIONS: { value: Visibility; label: string }[] = [
  { value: "PRIVATE", label: "Private" },
  { value: "SHARED", label: "Shared" },
  { value: "ORG_WIDE", label: "Org-wide" },
];

const NO_COMPARISON = "__none__";

let widgetIdCounter = 0;
/** A fresh, locally unique widget id (duplicated widgets must not share ids). */
function freshWidgetId(): string {
  widgetIdCounter += 1;
  return `w-${Date.now().toString(36)}-${widgetIdCounter}`;
}

function defaultRange(): { from: string; to: string; calendar: "TRADING" | "CALENDAR" } {
  const today = new Date();
  const from = new Date(
    Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() - 6),
  )
    .toISOString()
    .slice(0, 10);
  return { from, to: today.toISOString().slice(0, 10), calendar: "CALENDAR" };
}

function defaultQuery(): MetricQuery {
  return { metric: "sales.gross", range: defaultRange(), grain: "DAY", dimensions: [], comparison: null };
}

/** The widget "Add widget" creates until a metric catalogue endpoint exists on the frontend. */
function defaultWidget(): SavedWidget {
  return { id: freshWidgetId(), renderType: "time-series", queries: [defaultQuery()], layout: { w: 6, h: 2 } };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function widgetSummary(widget: SavedWidget): string {
  if (widget.savedQueryId) return `Saved query ${widget.savedQueryId.slice(0, 8)}…`;
  const metrics = widget.queries.map((q) => q.metric).join(", ");
  return metrics || "no query";
}

interface DashboardEditorProps {
  api: Api;
  dashboardId: string;
  /** Called after a successful save/restore so the library can refresh its list. */
  onChanged?: (doc: DashboardDocument) => void;
  /** Returns to the library. */
  onClose?: () => void;
  /** Called after a successful delete. */
  onDeleted?: () => void;
}

function editableFields(d: DashboardDocument) {
  return { title: d.title, description: d.description ?? "", visibility: d.visibility,
    fromDate: d.filters.dateRange?.from ?? "", toDate: d.filters.dateRange?.to ?? "", calendar: d.filters.dateRange?.calendar ?? "CALENDAR",
    comparison: d.filters.comparison ?? NO_COMPARISON, dimensions: d.filters.dimensions, widgets: d.widgets };
}

function useDirtyDashboardGuard(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return;
    const unload = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    const navigate = (event: MouseEvent) => {
      const link = (event.target as Element).closest?.("a[href]");
      if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      if (!window.confirm("Discard unsaved dashboard changes?")) { event.preventDefault(); event.stopPropagation(); }
    };
    window.addEventListener("beforeunload", unload);
    document.addEventListener("click", navigate, true);
    return () => { window.removeEventListener("beforeunload", unload); document.removeEventListener("click", navigate, true); };
  }, [dirty]);
}

/**
 * The dashboard editor: a form over the persisted {@link DashboardDocument} (title/description/
 * filters/visibility), a widget list with add/remove/duplicate/reorder/resize, a live render
 * preview with per-widget permission placeholders, and a revisions panel with restore. Saving
 * routes through `updateDashboard`; the preview re-runs `renderDashboard`.
 */
export function DashboardEditor({ api, dashboardId, onChanged, onClose, onDeleted }: DashboardEditorProps) {
  const [doc, setDoc] = useState<DashboardDocument | null>(null);
  const queryClient = useQueryClient();
  const update = useMutation({ mutationFn: (input: SaveDashboardInput) => api.updateDashboard(dashboardId, input) });
  const remove = useMutation({ mutationFn: () => api.deleteDashboard(dashboardId) });
  const restoreRevision = useMutation({ mutationFn: (revision: number) => api.restoreDashboardRevision(dashboardId, revision) });
  const detail = useQuery({ queryKey: ["getDashboard", dashboardId], queryFn: ({ signal }) => api.getDashboard(dashboardId, { signal }) });
  const preview = useQuery({ queryKey: ["renderDashboard", dashboardId], queryFn: ({ signal }) => api.renderDashboard(dashboardId, { signal }) });
  const queryList = useQuery({ queryKey: ["listSavedQueries"], queryFn: ({ signal }) => api.listSavedQueries({ signal }) });
  const history = useQuery({ queryKey: ["listDashboardRevisions", dashboardId], queryFn: ({ signal }) => api.listDashboardRevisions(dashboardId, { signal }), enabled: false });
  const baseline = useRef("");
  const loadedId = useRef<string | null>(null);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [visibility, setVisibility] = useState<Visibility>("PRIVATE");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [calendar, setCalendar] = useState<"TRADING" | "CALENDAR">("CALENDAR");
  const [comparison, setComparison] = useState<string>(NO_COMPARISON);
  const [dimensions, setDimensions] = useState<string[]>([]);
  const [widgets, setWidgets] = useState<SavedWidget[]>([]);

  const rendered = preview.data ?? null;
  const previewLoading = preview.isPending;
  const savedQueries = queryList.data ?? [];
  const [queryToAdd, setQueryToAdd] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedAt, setSavedAt] = useState<string | null>(null);

  const revisions = history.data ?? null;
  const [revisionsOpen, setRevisionsOpen] = useState(false);

  function applyDocument(d: DashboardDocument) {
    const fields = editableFields(d);
    setDoc(d);
    setTitle(fields.title);
    setDescription(fields.description);
    setVisibility(fields.visibility);
    setFromDate(fields.fromDate);
    setToDate(fields.toDate);
    setCalendar(fields.calendar);
    setComparison(fields.comparison);
    setDimensions(fields.dimensions);
    setWidgets(fields.widgets);
    baseline.current = JSON.stringify(fields);
  }

  useEffect(() => {
    if (detail.data && loadedId.current !== dashboardId) {
      loadedId.current = dashboardId;
      applyDocument(detail.data);
    }
  }, [detail.data, dashboardId]);

  const edits = JSON.stringify({ title, description, visibility, fromDate, toDate, calendar, comparison, dimensions, widgets });
  const latestEdits = useRef(edits);
  latestEdits.current = edits;
  const dirty = baseline.current !== "" && edits !== baseline.current;
  useDirtyDashboardGuard(dirty);

  const layoutByWidgetId = new Map(widgets.map((w) => [w.id, w.layout]));

  function buildFilters(): DashboardFilters {
    return {
      dateRange:
        fromDate && toDate ? { from: fromDate, to: toDate, calendar } : null,
      comparison: comparison === NO_COMPARISON ? null : comparison,
      dimensions,
    };
  }

  async function save() {
    if (saving) return;
    const submitted = latestEdits.current;
    setSaving(true);
    setSaveError(null);
    setSavedAt(null);
    try {
      const updated = await update.mutateAsync({
        title,
        description: description || null,
        layout: "grid",
        filters: buildFilters(),
        visibility,
        widgets,
      });
      if (latestEdits.current === submitted) applyDocument(updated);
      else { setDoc(updated); baseline.current = submitted; }
      queryClient.setQueryData(["getDashboard", dashboardId], updated);
      setSavedAt(new Date().toISOString());
      void queryClient.invalidateQueries({ queryKey: ["renderDashboard", dashboardId] });
      onChanged?.(updated);
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : "Couldn't save the dashboard.");
    } finally {
      setSaving(false);
    }
  }

  async function deleteDashboard() {
    if (saving || !window.confirm("Delete this dashboard? This cannot be undone.")) return;
    setSaving(true);
    setSaveError(null);
    try { await remove.mutateAsync(); onDeleted?.(); }
    catch (error) { setSaveError(error instanceof Error ? error.message : "Couldn't delete the dashboard."); }
    finally { setSaving(false); }
  }

  function openRevisions() {
    setRevisionsOpen(!revisionsOpen);
    if (!revisionsOpen) void history.refetch();
  }

  async function restore(revision: number) {
    if (saving || !window.confirm(dirty ? "Discard your unsaved changes and restore this revision?" : "Restore this revision?")) return;
    const submitted = latestEdits.current;
    setSaving(true);
    setSaveError(null);
    try {
      const restored = await restoreRevision.mutateAsync(revision);
      if (latestEdits.current === submitted) applyDocument(restored);
      else { setDoc(restored); baseline.current = submitted; }
      queryClient.setQueryData(["getDashboard", dashboardId], restored);
      void queryClient.invalidateQueries({ queryKey: ["renderDashboard", dashboardId] });
      onChanged?.(restored);
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : "Couldn't restore that revision.");
    } finally {
      setSaving(false);
    }
  }

  function addWidget() {
    setWidgets((ws) => [...ws, defaultWidget()]);
  }

  function addQueryWidget() {
    if (!queryToAdd) return;
    setWidgets((ws) => [
      ...ws,
      { id: freshWidgetId(), renderType: "query", queries: [], layout: { w: 12, h: 2 }, savedQueryId: queryToAdd },
    ]);
    setQueryToAdd("");
  }

  function removeWidget(id: string) {
    setWidgets((ws) => ws.filter((w) => w.id !== id));
  }

  function duplicateWidget(id: string) {
    setWidgets((ws) => {
      const idx = ws.findIndex((w) => w.id === id);
      if (idx < 0) return ws;
      const src = ws[idx];
      const copy: SavedWidget = {
        id: freshWidgetId(),
        renderType: src.renderType,
        queries: src.queries.map((q) => ({ ...q, dimensions: [...q.dimensions] })),
        layout: { ...src.layout },
        savedQueryId: src.savedQueryId,
      };
      return [...ws.slice(0, idx + 1), copy, ...ws.slice(idx + 1)];
    });
  }

  function moveWidget(id: string, direction: -1 | 1) {
    setWidgets((ws) => {
      const idx = ws.findIndex((w) => w.id === id);
      const to = idx + direction;
      if (idx < 0 || to < 0 || to >= ws.length) return ws;
      const next = [...ws];
      [next[idx], next[to]] = [next[to], next[idx]];
      return next;
    });
  }

  function resizeWidget(id: string, dw: number, dh: number) {
    setWidgets((ws) =>
      ws.map((w) =>
        w.id === id
          ? {
              ...w,
              layout: { w: clamp(w.layout.w + dw, 1, 12), h: clamp(w.layout.h + dh, 1, 4) },
            }
          : w,
      ),
    );
  }

  if (detail.isPending) return <LoadingState rows={3} />;
  if (detail.isLoadingError) return <ErrorState title="Couldn't load dashboard" message={detail.error.message} onRetry={() => { void detail.refetch(); }} />;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          {onClose ? (
            <Button variant="ghost" size="sm" onClick={() => { if (!dirty || window.confirm("Discard unsaved dashboard changes?")) onClose?.(); }}>
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Library
            </Button>
          ) : null}
          <h2 className="text-lg font-semibold">{doc?.title ?? title}</h2>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={openRevisions}>
            <History className="h-4 w-4" aria-hidden="true" />
            Revisions
          </Button>
          {onDeleted ? (
            <Button variant="outline" size="sm" onClick={deleteDashboard} disabled={saving}>
              <Trash2 className="h-4 w-4" aria-hidden="true" />
              Delete
            </Button>
          ) : null}
          <Button size="sm" onClick={save} disabled={saving}>
            <Save className="h-4 w-4" aria-hidden="true" />
            {saving ? "Saving…" : "Save"}
          </Button>
        </div>
      </div>

      {preview.error ? <ErrorState title="Preview unavailable" message={preview.error.message} onRetry={() => { void preview.refetch(); }} /> : null}
      {history.error ? <ErrorState title="Revision history unavailable" message={history.error.message} onRetry={() => { void history.refetch(); }} /> : null}
      {queryList.error ? <ErrorState title="Saved queries unavailable" message={queryList.error.message} onRetry={() => { void queryList.refetch(); }} /> : null}
      {saveError ? (
        <div role="alert" className="rounded-md border border-destructive bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {saveError}
        </div>
      ) : null}
      {savedAt ? (
        <p className="text-xs text-muted-foreground" role="status">
          Saved.
        </p>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="dash-title">Title</Label>
            <Input id="dash-title" value={title} onChange={(e) => setTitle(e.target.value)} />
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="dash-description">Description</Label>
            <Input
              id="dash-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What this dashboard shows"
            />
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="dash-visibility">Visibility</Label>
            <Select value={visibility} onValueChange={(v) => setVisibility(v as Visibility)}>
              <SelectTrigger id="dash-visibility">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {VISIBILITY_OPTIONS.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <fieldset className="flex flex-col gap-2 rounded-md border p-3">
            <legend className="px-1 text-sm font-medium">Filters</legend>
            <div className="grid grid-cols-2 gap-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="dash-from">From</Label>
                <DatePicker id="dash-from" clearable value={fromDate} max={toDate || undefined} onChange={setFromDate} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="dash-to">To</Label>
                <DatePicker id="dash-to" clearable value={toDate} min={fromDate || undefined} onChange={setToDate} />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="dash-calendar">Calendar</Label>
              <Select value={calendar} onValueChange={(v) => setCalendar(v as "TRADING" | "CALENDAR")}>
                <SelectTrigger id="dash-calendar">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CALENDAR">Calendar days</SelectItem>
                  <SelectItem value="TRADING">Trading days</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="dash-comparison">Comparison</Label>
              <Select value={comparison} onValueChange={setComparison}>
                <SelectTrigger id="dash-comparison">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_COMPARISON}>None</SelectItem>
                  {COMPARISON_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label>Dimensions</Label>
              <div className="flex flex-wrap gap-4">
                {DIMENSION_OPTIONS.map((d) => {
                  const checked = dimensions.includes(d.value);
                  return (
                    <label key={d.value} className="flex items-center gap-2 text-sm">
                      <Checkbox
                        checked={checked}
                        onCheckedChange={(c) =>
                          setDimensions((prev) =>
                            c === true ? [...prev, d.value] : prev.filter((x) => x !== d.value),
                          )
                        }
                      />
                      {d.label}
                    </label>
                  );
                })}
              </div>
            </div>
          </fieldset>

          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label>Widgets</Label>
              <div className="flex items-center gap-2">
                {savedQueries.length > 0 ? (
                  <>
                    <select
                      aria-label="Add saved query"
                      value={queryToAdd}
                      onChange={(e) => setQueryToAdd(e.target.value)}
                      className="rounded-md border bg-background px-2 py-1 text-sm"
                    >
                      <option value="">Saved query…</option>
                      {savedQueries.map((q) => (
                        <option key={q.id} value={q.id}>
                          {q.title}
                        </option>
                      ))}
                    </select>
                    <Button variant="outline" size="sm" onClick={addQueryWidget} disabled={!queryToAdd}>
                      Add query
                    </Button>
                  </>
                ) : null}
                <Button variant="outline" size="sm" onClick={addWidget}>
                  <Plus className="h-4 w-4" aria-hidden="true" />
                  Add widget
                </Button>
              </div>
            </div>
            {widgets.length === 0 ? (
              <p className="text-sm text-muted-foreground">No widgets yet. Add one to get started.</p>
            ) : (
              <ol className="flex flex-col gap-2">
                {widgets.map((w, index) => (
                  <li key={w.id} className="rounded-md border p-3">
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <Badge variant="secondary">{RENDER_TYPE_LABELS[w.renderType] ?? w.renderType}</Badge>
                        <span className="text-sm">{widgetSummary(w)}</span>
                      </div>
                      <span className="text-xs text-muted-foreground">
                        {w.layout.w}×{w.layout.h}
                      </span>
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-1">
                      <Button variant="ghost" size="icon" onClick={() => moveWidget(w.id, -1)} disabled={index === 0} aria-label="Move up">
                        <ArrowUp className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => moveWidget(w.id, 1)} disabled={index === widgets.length - 1} aria-label="Move down">
                        <ArrowDown className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => duplicateWidget(w.id)} aria-label="Duplicate widget">
                        <Copy className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <span className="mx-1 text-xs text-muted-foreground">Width</span>
                      <Button variant="outline" size="sm" onClick={() => resizeWidget(w.id, -1, 0)} aria-label="Decrease width">
                        −
                      </Button>
                      <span className="w-6 text-center text-xs tabular-nums">{w.layout.w}</span>
                      <Button variant="outline" size="sm" onClick={() => resizeWidget(w.id, 1, 0)} aria-label="Increase width">
                        +
                      </Button>
                      <span className="mx-1 text-xs text-muted-foreground">Height</span>
                      <Button variant="outline" size="sm" onClick={() => resizeWidget(w.id, 0, -1)} aria-label="Decrease height">
                        −
                      </Button>
                      <span className="w-6 text-center text-xs tabular-nums">{w.layout.h}</span>
                      <Button variant="outline" size="sm" onClick={() => resizeWidget(w.id, 0, 1)} aria-label="Increase height">
                        +
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => removeWidget(w.id)} aria-label="Remove widget">
                        <Trash2 className="h-4 w-4" aria-hidden="true" />
                      </Button>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </section>

        <section className="flex flex-col gap-3">
          <h3 className="text-sm font-semibold text-muted-foreground">Preview</h3>
          {previewLoading ? (
            <LoadingState rows={2} />
          ) : rendered && rendered.length > 0 ? (
            <div className="grid grid-cols-1 gap-4 md:grid-cols-6 lg:grid-cols-12">
              {rendered.map((rw) => {
                const layout = layoutByWidgetId.get(rw.widgetId) ?? { w: 6, h: 2 };
                const span = clamp(layout.w, 1, 12);
                return (
                  <div key={rw.widgetId} style={{ gridColumn: `span ${span}` }} className="min-w-0">
                    {rw.deniedResource ? (
                      <PermissionDenied subject={rw.deniedResource} />
                    ) : rw.widget ? (
                      <><WidgetRenderer widget={rw.widget} trust={rw.trust} /><DataQualifications notices={rw.notices} /></>
                    ) : null}
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">This dashboard has no renderable widgets.</p>
          )}
        </section>
      </div>

      {revisionsOpen ? (
        <section className="rounded-md border p-3">
          <h3 className="text-sm font-semibold">Revisions</h3>
          {revisions === null ? (
            <p className="mt-1 text-sm text-muted-foreground">Loading…</p>
          ) : revisions.length === 0 ? (
            <p className="mt-1 text-sm text-muted-foreground">No revisions recorded.</p>
          ) : (
            <ul className="mt-2 flex flex-col gap-2">
              {revisions.map((r) => (
                <li key={r.revision} className="flex items-center justify-between gap-2 rounded-md border p-2">
                  <div className="flex flex-col">
                    <span className="text-sm font-medium">Revision {r.revision}</span>
                    <span className="text-xs text-muted-foreground">
                      {r.createdBy} · {r.createdAt}
                    </span>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => restore(r.revision)} disabled={saving}>
                    <RotateCcw className="h-4 w-4" aria-hidden="true" />
                    Restore
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}
    </div>
  );
}
