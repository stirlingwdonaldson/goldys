"use client";

// THROWAWAY: three fixture-only dashboard editors on /prototype/dashboards?variant=A|B|C.
// Question: direct canvas, report outline or guided setup for occasional owner/manager editors?
import { useRef, useState, type ReactNode } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Check, ChevronRight, Copy, Eye, LayoutGrid, LockKeyhole, Pencil, Plus, RotateCcw, Save, Settings2, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Sidebar, SidebarContent, SidebarGroup, SidebarGroupLabel, SidebarHeader, SidebarInset, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PrototypeSwitcher } from "@/components/ui/prototype-switcher";
import { SourceLabel } from "@/components/sources/source-tile";
import { NAV_GROUPS } from "@/lib/nav";
import { formatCurrency, formatNumber, formatPercent } from "@/lib/format";
import { cn } from "@/lib/utils";
import { catalogue, initialDraft, variants, type Draft, type Widget } from "./fixtures";

type EditorProps = { draft: Draft; preview: boolean; viewer: string; configure: (id: string) => void; add: () => void; move: (id: string, direction: number) => void; duplicate: (id: string) => void; remove: (id: string) => void; resize: (id: string) => void };

function Choice({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return <div className="space-y-2"><Label>{label}<Select value={value} onValueChange={onChange}><SelectTrigger className="mt-2"><SelectValue /></SelectTrigger><SelectContent>{options.map((option) => <SelectItem key={option} value={option}>{catalogue.find((item) => item.metric === option)?.title ?? ({ stat: "Single figure", trend: "Weekly trend", ranked: "Ranked list", half: "Half width", full: "Full width" } as Record<string, string>)[option] ?? option}</SelectItem>)}</SelectContent></Select></Label></div>;
}

function WidgetContent({ widget, draft, viewer }: { widget: Widget; draft: Draft; viewer: string }) {
  const metric = catalogue.find((item) => item.metric === widget.metric)!;
  const previous = draft.period === "previous-week";
  const restricted = widget.metric === "labour" && viewer === "Manager";
  if (restricted) return <div className="flex min-h-28 flex-col justify-center gap-2 rounded-lg bg-muted/50 p-4"><LockKeyhole className="size-5 text-muted-foreground" /><p className="text-sm font-medium">Labour cost is not permitted</p><p className="text-xs text-muted-foreground">This viewer has no wage-data grant. Ask an owner for access.</p></div>;
  if (metric.value === null) return <div className="min-h-28 space-y-3 py-2"><p className="text-kpi tabular-nums">—</p><Badge variant="missing">Missing closing stock</Badge><p className="text-xs text-muted-foreground">No closing stock from Cooking the Books. Consumed cost cannot be calculated.</p></div>;
  const value = previous && metric.kind !== "percent" ? Math.round(metric.value * 0.92) : metric.value;
  const formatted = metric.kind === "money" ? formatCurrency(value, { cents: false }) : metric.kind === "percent" ? formatPercent(value) : formatNumber(value);
  return <div className="space-y-3">
    {widget.display === "trend" ? <div role="img" aria-label="Daily gross sales: Monday $8,060; Tuesday $8,470; Wednesday $9,340; Thursday $12,180; Friday $18,650; Saturday $22,430; Sunday $13,610. Previous week values are 92% of these." className="space-y-2">
      <div className="flex items-baseline gap-3"><p className="text-kpi font-semibold tabular-nums">{formatted}</p><span className="text-xs text-muted-foreground">across the week</span></div>
      <div className="flex h-32 items-end gap-3 border-b px-2 pt-3">{[8060, 8470, 9340, 12180, 18650, 22430, 13610].map((n, i) => <div key={i} className="flex h-full flex-1 items-end justify-center gap-1">{draft.comparison ? <div className="w-1/3 rounded-t bg-chart-1/20" style={{ height: `${n * (previous ? 0.92 : 1) / 260}%` }} /> : null}<div className="w-1/2 rounded-t bg-chart-1" style={{ height: `${n * (previous ? 0.92 : 1) / 240}%` }} /></div>)}</div>
      <div className="flex justify-around text-2xs text-muted-foreground">{["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((day) => <span key={day}>{day}</span>)}</div><p className="text-2xs text-muted-foreground">Solid: selected week{draft.comparison ? " · Pale: previous week" : ""}</p>
    </div> : widget.display === "ranked" ? <ol className="space-y-3">{[["Chicken parma", 7840], ["Carlton Draught", 6420], ["Fish & chips", 5210]].map(([name, n], i) => <li key={name} className="flex items-center justify-between border-b pb-2 text-sm"><span><span className="mr-3 text-muted-foreground">{i + 1}</span>{name}</span><span className="tabular-nums">{formatCurrency(Number(n) * (previous ? 0.92 : 1), { cents: false })}</span></li>)}</ol> : <><p className="text-kpi font-semibold tracking-tight tabular-nums">{formatted}</p>{draft.comparison ? <p className="text-xs text-muted-foreground">{metric.kind === "percent" ? "0.6 percentage points lower" : "+8.7%"} vs previous week</p> : null}</>}
    {widget.metric === "sales" ? <div className="flex flex-wrap items-center gap-2 border-t pt-3"><Badge variant="conflict">1 disputed day</Badge><span className="text-2xs text-muted-foreground">Sun · source difference {formatCurrency(310.2)}</span></div> : null}
    {widget.metric === "covers" ? <p className="text-2xs text-muted-foreground">Booking party sizes; actual attendance may differ.</p> : null}
    {widget.metric === "purchases" ? <p className="text-2xs text-muted-foreground">Purchases on invoices, not consumed cost.</p> : null}
  </div>;
}

function WidgetTools({ widget, index, count, ...props }: EditorProps & { widget: Widget; index: number; count: number }) {
  return <div className="flex flex-wrap items-center justify-between gap-1 border-t bg-muted/20 px-3 py-1.5">
    <Button size="sm" variant="ghost" onClick={() => props.configure(widget.id)}><Pencil /> Configure</Button>
    <div className="flex items-center gap-0.5">
      <Button size="icon" variant="ghost" aria-label={`Move ${widget.title} up`} disabled={index === 0} onClick={() => props.move(widget.id, -1)}><ArrowUp /></Button>
      <Button size="icon" variant="ghost" aria-label={`Move ${widget.title} down`} disabled={index === count - 1} onClick={() => props.move(widget.id, 1)}><ArrowDown /></Button>
      <Button size="icon" variant="ghost" aria-label={`Duplicate ${widget.title}`} onClick={() => props.duplicate(widget.id)}><Copy /></Button>
      <Button size="icon" variant="ghost" aria-label={`Remove ${widget.title}`} onClick={() => props.remove(widget.id)}><Trash2 /></Button>
    </div>
  </div>;
}

function WidgetCard({ widget, index, ...props }: EditorProps & { widget: Widget; index: number }) {
  const metric = catalogue.find((item) => item.metric === widget.metric)!;
  return <Card className="min-w-0 overflow-hidden">
    <div className="space-y-4 p-5"><div className="flex items-start justify-between gap-2"><div className="min-w-0 space-y-2"><h3 className="truncate text-sm font-medium" title={widget.title}>{widget.title}</h3><SourceLabel source={metric.source} /></div>{!props.preview ? <Button variant="ghost" size="sm" onClick={() => props.resize(widget.id)} aria-label={`Resize ${widget.title}`}>{widget.width === "full" ? "Full" : "Half"}<LayoutGrid /></Button> : null}</div><WidgetContent widget={widget} draft={props.draft} viewer={props.viewer} /></div>
    {!props.preview ? <WidgetTools {...props} widget={widget} index={index} count={props.draft.widgets.length} /> : null}
  </Card>;
}

function Canvas({ compact = false, ...props }: EditorProps & { compact?: boolean }) {
  return <div className={cn("grid gap-4", !compact && "xl:grid-cols-2")}>
    {props.draft.widgets.map((widget, index) => <div key={widget.id} className={cn("min-w-0", !compact && widget.width === "full" && "xl:col-span-2")}><WidgetCard {...props} widget={widget} index={index} /></div>)}
    {!props.preview ? <Button variant="outline" className="min-h-24 border-dashed xl:col-span-full" onClick={props.add}><Plus /> Add a widget</Button> : null}
    {!props.draft.widgets.length && props.preview ? <p className="rounded-xl border border-dashed p-8 text-sm text-muted-foreground">No widgets yet. Return to Edit to add one.</p> : null}
  </div>;
}

export function VariantA(props: EditorProps) {
  return <div className="grid items-start gap-6 2xl:grid-cols-[minmax(0,1fr)_17rem]">
    <section className="min-w-0 rounded-xl border bg-muted/30 p-4 md:p-6"><div className="mb-5 flex items-center justify-between"><div><p className="text-xs font-medium uppercase tracking-widest text-muted-foreground">Your dashboard</p><p className="mt-1 text-xs text-muted-foreground">{props.preview ? "Previewing the current draft" : "Configure, resize and arrange each widget in place."}</p></div><Badge variant="neutral">{props.draft.widgets.length} widgets</Badge></div><Canvas {...props} /></section>
    {!props.preview ? <aside className="space-y-5"><div><h2 className="font-semibold">Build your view</h2><p className="mt-2 text-sm text-muted-foreground">Start with the numbers you check every Monday.</p></div><Card className="space-y-3 p-4"><LayoutGrid className="size-5 text-muted-foreground" /><h3 className="text-sm font-medium">A dashboard, your way</h3><p className="text-xs leading-relaxed text-muted-foreground">Use half-width widgets for a quick comparison. Give trends the full row.</p><Button variant="outline" className="w-full" onClick={props.add}><Plus /> Browse widgets</Button></Card><div className="space-y-3 text-xs text-muted-foreground"><p className="font-medium text-foreground">Trust stays attached</p><p>Source labels and qualifications stay visible when you move or resize a widget.</p><Badge variant="missing">1 missing input</Badge><Badge variant="conflict">1 disputed day</Badge></div></aside> : null}
  </div>;
}

export function VariantB(props: EditorProps) {
  const [selected, setSelected] = useState<string | null>(null);
  return <div className="grid items-start gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
    {!props.preview ? <aside className="space-y-4 lg:sticky lg:top-5"><div className="flex items-center justify-between"><h2 className="text-sm font-semibold">Report outline</h2><Badge variant="neutral">{props.draft.widgets.length}</Badge></div><p className="text-xs leading-relaxed text-muted-foreground">Arrange the story from top to bottom. Select a section to find it in the report.</p><ol className="space-y-2">{props.draft.widgets.map((widget, index) => <li key={widget.id} className={cn("rounded-lg border p-2", selected === widget.id && "border-primary bg-muted")}><button className="flex w-full items-center gap-2 rounded p-1 text-left text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => { setSelected(widget.id); document.getElementById(`section-${widget.id}`)?.scrollIntoView({ block: "center" }); }}><span className="text-xs tabular-nums text-muted-foreground">{String(index + 1).padStart(2, "0")}</span><span className="truncate">{widget.title}</span></button><div className="mt-1 flex items-center justify-end"><Button variant="ghost" size="icon" aria-label={`Move ${widget.title} up`} disabled={index === 0} onClick={() => props.move(widget.id, -1)}><ArrowUp /></Button><Button variant="ghost" size="icon" aria-label={`Move ${widget.title} down`} disabled={index === props.draft.widgets.length - 1} onClick={() => props.move(widget.id, 1)}><ArrowDown /></Button><Button variant="ghost" size="sm" onClick={() => props.configure(widget.id)}>Edit</Button></div></li>)}</ol><Button variant="outline" className="w-full" onClick={props.add}><Plus /> Add section</Button></aside> : null}
    <section className={cn("min-w-0 rounded-xl border bg-muted/30 p-3 md:p-7", props.preview && "lg:col-span-2")}><div className="mx-auto max-w-3xl rounded-xl border bg-background p-5 shadow-panel md:p-8"><p className="text-xs font-medium uppercase tracking-widest text-muted-foreground">Goldy’s / weekly review</p><h2 className="mt-4 text-2xl font-semibold tracking-tight">{props.draft.title || "Untitled dashboard"}</h2><p className="mt-2 text-sm text-muted-foreground">{props.draft.description}</p><p className="mt-3 text-xs text-muted-foreground">{props.draft.period === "last-week" ? "26 Oct–1 Nov 2026" : "19–25 Oct 2026"} · {props.draft.visibility}</p>
      <div className="mt-7 divide-y">{props.draft.widgets.map((widget, index) => <section key={widget.id} id={`section-${widget.id}`} className={cn("space-y-4 py-6", selected === widget.id && "rounded-lg bg-muted/40 px-3")}><div className="flex items-center justify-between gap-2"><h3 className="text-sm font-semibold"><span className="mr-3 text-xs tabular-nums text-muted-foreground">{String(index + 1).padStart(2, "0")}</span>{widget.title}</h3>{!props.preview ? <Button size="sm" variant="ghost" onClick={() => props.configure(widget.id)}><Pencil /> Edit</Button> : null}</div><SourceLabel source={catalogue.find((item) => item.metric === widget.metric)!.source} /><WidgetContent widget={widget} draft={props.draft} viewer={props.viewer} /></section>)}</div>
      {!props.preview ? <Button variant="outline" className="mt-4 w-full border-dashed" onClick={props.add}><Plus /> Add the next section</Button> : null}
      {!props.draft.widgets.length && props.preview ? <p className="py-8 text-sm text-muted-foreground">This report has no sections yet.</p> : null}
      <p className="mt-7 border-t pt-4 text-2xs text-muted-foreground">Prototype · synthetic data · qualifications remain visible</p>
    </div></section>
  </div>;
}

export function VariantC({ settings, save, ...props }: EditorProps & { settings: ReactNode; save: () => void }) {
  const [step, setStep] = useState(0);
  const steps = ["Set the context", "Choose the content", "Review the dashboard"];
  return <div className="space-y-6"><nav aria-label="Dashboard setup"><ol className="grid gap-2 md:grid-cols-3">{steps.map((label, index) => <li key={label}><Button className="h-auto w-full justify-start gap-3 p-4" variant={step === index ? "default" : "outline"} onClick={() => setStep(index)} aria-current={step === index ? "step" : undefined}><span className="flex size-6 items-center justify-center rounded-full border text-xs tabular-nums">{index < step ? <Check className="size-3" /> : index + 1}</span>{label}</Button></li>)}</ol></nav>
    {props.preview ? <Canvas {...props} /> : <div className="grid items-start gap-8 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]"><section className="space-y-6 rounded-xl border p-6"><div><p className="text-xs uppercase tracking-widest text-muted-foreground">Step {step + 1} of 3</p><h2 className="mt-3 text-xl font-semibold">{steps[step]}</h2><p className="mt-2 text-sm text-muted-foreground">{["Decide what this dashboard is for and who can find it.", "Choose only the figures that help you make a decision.", "Check the reading order and what each viewer will see."][step]}</p></div>
      {step === 0 ? settings : step === 1 ? <div className="space-y-3">{props.draft.widgets.map((widget, index) => <div key={widget.id} className="flex items-center justify-between gap-2 rounded-lg border p-3"><div className="min-w-0"><p className="truncate text-sm font-medium">{widget.title}</p><p className="mt-1 text-xs text-muted-foreground">{widget.display} · {widget.width} width</p></div><div className="flex"><Button variant="ghost" size="icon" aria-label={`Move ${widget.title} up`} disabled={index === 0} onClick={() => props.move(widget.id, -1)}><ArrowUp /></Button><Button variant="ghost" size="sm" onClick={() => props.configure(widget.id)}>Edit</Button><Button variant="ghost" size="icon" aria-label={`Remove ${widget.title}`} onClick={() => props.remove(widget.id)}><Trash2 /></Button></div></div>)}<Button variant="outline" className="w-full" onClick={props.add}><Plus /> Choose another widget</Button></div> : <div className="space-y-4"><div className="flex gap-3 rounded-lg bg-status-success-soft p-4"><Check className="size-5 text-status-success" /><div><p className="text-sm font-medium">{props.draft.widgets.length} widgets ready to review</p><p className="mt-1 text-xs text-muted-foreground">{props.draft.visibility} · queries and layout only</p></div></div><p className="text-sm leading-relaxed text-muted-foreground">This draft contains sample source disagreements and missing stock. Restricted wage figures are replaced by a permission notice in Manager preview.</p><p className="text-xs text-muted-foreground">Sharing never grants access to the underlying data.</p><Button onClick={save}><Save /> Save draft in memory</Button></div>}
      <div className="flex justify-between border-t pt-5"><Button variant="ghost" onClick={() => setStep(step - 1)} disabled={step === 0}><ArrowLeft /> Back</Button>{step < 2 ? <Button onClick={() => setStep(step + 1)}>Continue <ArrowRight /></Button> : <Button variant="outline" onClick={() => setStep(0)}>Review settings</Button>}</div>
    </section><section className="min-w-0 space-y-4"><div className="flex items-center justify-between"><h2 className="text-sm font-semibold">Your dashboard so far</h2><Badge variant="neutral">Draft preview</Badge></div><div className="rounded-xl border bg-muted/30 p-4"><Canvas {...props} preview compact /></div></section></div>}
  </div>;
}

export function DashboardEditorPrototype() {
  const router = useRouter();
  const search = useSearchParams();
  const variant = variants.find((item) => item.key === search.get("variant")) ?? variants[0];
  const panel = search.get("panel");
  const [draft, setDraft] = useState<Draft>(initialDraft);
  const [saved, setSaved] = useState<Draft>(initialDraft);
  const [undo, setUndo] = useState<Draft[]>([]);
  const [preview, setPreview] = useState(false);
  const [viewer, setViewer] = useState("Owner");
  const [query, setQuery] = useState("");
  const [notice, setNotice] = useState("Edits live in memory. Reloading resets the prototype.");
  const counter = useRef(0);
  const openedPanel = useRef(false);
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved);
  const widget = draft.widgets.find((item) => item.id === search.get("widget"));
  function urlChange(key: string) {
    const params = new URLSearchParams(search.toString());
    params.set("variant", key); params.delete("panel"); params.delete("widget");
    router.replace(`?${params}`, { scroll: false });
  }
  function openPanel(name: string, id?: string) {
    const params = new URLSearchParams(search.toString());
    params.set("panel", name);
    if (id) params.set("widget", id); else params.delete("widget");
    openedPanel.current = true;
    router.push(`?${params}`, { scroll: false });
  }
  function closePanel() {
    if (openedPanel.current) { openedPanel.current = false; router.back(); return; }
    const params = new URLSearchParams(search.toString()); params.delete("panel"); params.delete("widget");
    router.replace(`?${params}`, { scroll: false });
  }
  function edit(next: Draft) { setUndo((history) => [...history, draft]); setDraft(next); setNotice("Draft changed. Preview updates immediately using sample data."); }
  function patchWidget(id: string, patch: Partial<Widget>) { edit({ ...draft, widgets: draft.widgets.map((item) => item.id === id ? { ...item, ...patch } : item) }); }
  function move(id: string, direction: number) {
    const index = draft.widgets.findIndex((item) => item.id === id);
    if (index + direction < 0 || index + direction >= draft.widgets.length) return;
    const widgets = [...draft.widgets]; [widgets[index], widgets[index + direction]] = [widgets[index + direction], widgets[index]];
    edit({ ...draft, widgets });
  }
  function addMetric(metric: string) {
    const item = catalogue.find((entry) => entry.metric === metric)!;
    counter.current += 1;
    edit({ ...draft, widgets: [...draft.widgets, { id: `added-${counter.current}`, metric, title: item.title, width: "half", display: metric === "mix" ? "ranked" : "stat" }] });
    setNotice(`${item.title} added to the draft.`); closePanel(); setQuery("");
  }
  function save() { setSaved(draft); setNotice("Draft saved in memory only. Reloading resets it; nothing was sent to a server."); }
  const props: EditorProps = { draft, viewer, preview, configure: (id) => openPanel("widget", id), add: () => openPanel("library"), move,
    duplicate: (id) => { const item = draft.widgets.find((entry) => entry.id === id)!; counter.current += 1; const widgets = [...draft.widgets]; widgets.splice(widgets.indexOf(item) + 1, 0, { ...item, id: `copy-${counter.current}`, title: `${item.title} (copy)` }); edit({ ...draft, widgets }); },
    remove: (id) => { edit({ ...draft, widgets: draft.widgets.filter((item) => item.id !== id) }); setNotice("Widget removed. Use Undo to put it back."); },
    resize: (id) => { const item = draft.widgets.find((entry) => entry.id === id)!; patchWidget(id, { width: item.width === "full" ? "half" : "full" }); },
  };
  const settings = <div className="space-y-5"><div className="space-y-2"><Label htmlFor="prototype-title">Dashboard name</Label><Input id="prototype-title" value={draft.title} onChange={(event) => edit({ ...draft, title: event.target.value })} /></div><div className="space-y-2"><Label htmlFor="prototype-description">Description</Label><Input id="prototype-description" value={draft.description} onChange={(event) => edit({ ...draft, description: event.target.value })} /></div><Choice label="Who can find this dashboard?" value={draft.visibility} options={["Private", "Shared", "Org-wide"]} onChange={(visibility) => edit({ ...draft, visibility })} /><p className="text-xs leading-relaxed text-muted-foreground">Sharing the dashboard does not grant permission to its data. Each widget checks the viewer’s existing access.</p><label className="flex items-center gap-3 text-sm"><Checkbox checked={draft.comparison} onCheckedChange={(value) => edit({ ...draft, comparison: value === true })} /> Compare with the previous week</label></div>;
  return <SidebarProvider defaultOpen style={{ "--sidebar-width": "14rem" } as React.CSSProperties}>
    <Sidebar variant="inset" collapsible="icon"><SidebarHeader className="gap-1 px-4 py-5"><span className="text-xl font-semibold tracking-tight">Goldy’s<span className="text-brand">.</span></span><span className="text-2xs text-muted-foreground">THE PUB, IN PERSPECTIVE</span></SidebarHeader><SidebarContent>{NAV_GROUPS.filter((group) => !group.collapsible).map((group) => <SidebarGroup key={group.label}><SidebarGroupLabel>{group.label}</SidebarGroupLabel><SidebarMenu>{group.items.map((item) => <SidebarMenuItem key={item.href}><SidebarMenuButton isActive={item.href === "/dashboards"} onClick={() => setNotice(`${item.title} is outside this dashboard-editing prototype.`)} tooltip={item.title}><item.icon /><span>{item.title}</span></SidebarMenuButton></SidebarMenuItem>)}</SidebarMenu></SidebarGroup>)}</SidebarContent><div className="border-t p-4 text-xs text-muted-foreground">Fixture workspace<br /><span className="mt-1 block text-2xs">No connected accounts</span></div></Sidebar>
    <SidebarInset className="min-w-0 overflow-hidden"><div className="border-b bg-status-info-soft px-6 py-2 text-center text-xs text-status-info">THROWAWAY PROTOTYPE · Synthetic demo data · All changes stay in this tab</div><header className="flex items-center gap-3 border-b px-5 py-4"><SidebarTrigger /><span className="h-4 border-l" /><span className="text-xs text-muted-foreground">Custom dashboards</span><ChevronRight className="size-3 text-muted-foreground" /><span className="truncate text-xs">Edit dashboard</span><Badge className="ml-auto shrink-0" variant="neutral">Fixture workspace</Badge></header>
      <main className="space-y-6 p-4 pb-32 md:p-7 md:pb-32"><div className="flex flex-wrap items-start justify-between gap-4"><div className="min-w-0"><div className="mb-2 flex items-center gap-2 text-xs text-muted-foreground"><LayoutGrid className="size-3" /> Custom dashboards <span>/</span> Editing</div><h1 className="text-2xl font-semibold tracking-tight">{draft.title || "Untitled dashboard"}</h1><p className="mt-2 text-sm text-muted-foreground">{draft.description}</p></div><div className="flex flex-wrap items-center gap-2"><Badge variant={dirty ? "info" : "neutral"}>{dirty ? "Unsaved draft" : "No unsaved changes"}</Badge><Button variant="outline" size="sm" onClick={() => openPanel("settings")}><Settings2 /> Settings</Button><Button variant="brand" size="sm" onClick={save} disabled={!dirty || !draft.title.trim()}><Save /> Save draft</Button></div></div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-y py-3"><div className="flex flex-wrap items-center gap-3"><Tabs value={preview ? "preview" : "edit"} onValueChange={(value) => setPreview(value === "preview")}><TabsList aria-label="Editor mode"><TabsTrigger value="edit"><Pencil className="size-3" /> Edit</TabsTrigger><TabsTrigger value="preview"><Eye className="size-3" /> Preview</TabsTrigger></TabsList></Tabs><Select value={draft.period} onValueChange={(period) => edit({ ...draft, period })}><SelectTrigger aria-label="Dashboard period" className="w-48"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="last-week">26 Oct–1 Nov 2026</SelectItem><SelectItem value="previous-week">19–25 Oct 2026</SelectItem></SelectContent></Select><span className="text-xs text-muted-foreground">{draft.comparison ? "vs previous week" : "No comparison"}</span></div><div className="flex items-center gap-2"><span className="text-xs text-muted-foreground">View as</span><Select value={viewer} onValueChange={setViewer}><SelectTrigger aria-label="Preview viewer" className="w-28"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="Owner">Owner</SelectItem><SelectItem value="Manager">Manager</SelectItem></SelectContent></Select><Button variant="ghost" size="sm" disabled={!undo.length} onClick={() => { setDraft(undo[undo.length - 1]); setUndo(undo.slice(0, -1)); setNotice("Last draft edit undone."); }}><RotateCcw /> Undo</Button></div></div>
      <div className="flex flex-wrap items-center justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">Direction {variant.key} / {variant.name}</p><p className="mt-1 text-sm">{variant.question}</p></div><p className="text-xs text-muted-foreground">{variant.tradeoff}</p></div>
      <p role="status" className="text-xs text-muted-foreground">{notice}</p>
      {variant.key === "A" ? <VariantA {...props} /> : variant.key === "B" ? <VariantB {...props} /> : <VariantC {...props} settings={settings} save={save} />}
      <details className="rounded-lg border p-4"><summary className="cursor-pointer text-xs font-medium">Prototype state · {draft.widgets.length} widgets · {draft.visibility} · {viewer} preview · {dirty ? "unsaved" : "saved in memory"}</summary><pre className="mt-4 max-h-64 overflow-auto whitespace-pre-wrap break-words font-mono text-2xs">{JSON.stringify({ variant: variant.key, preview, viewer, dirty, draft }, null, 2)}</pre></details>
    </main></SidebarInset>
    <PrototypeSwitcher current={variant.key} variants={variants} onChange={urlChange} />
    <Sheet open={panel !== null} onOpenChange={(open) => { if (!open) closePanel(); }}><SheetContent className="overflow-y-auto sm:max-w-lg"><SheetHeader><SheetTitle>{panel === "library" ? "Add a widget" : panel === "widget" ? "Configure widget" : "Dashboard settings"}</SheetTitle><SheetDescription>{panel === "library" ? "Choose a business question. Synthetic sample data only." : "Changes appear immediately in the in-memory draft."}</SheetDescription></SheetHeader><div className="mt-7 space-y-6">
      {panel === "library" ? <><Input aria-label="Search widgets" placeholder="Search sales, labour, covers…" value={query} onChange={(event) => setQuery(event.target.value)} /><div className="space-y-3">{catalogue.filter((item) => `${item.title} ${item.description}`.toLowerCase().includes(query.toLowerCase())).map((item) => <Card key={item.metric} className="space-y-3 p-4"><div className="flex items-center justify-between gap-2"><h3 className="text-sm font-semibold">{item.title}</h3><Button size="sm" variant="outline" onClick={() => addMetric(item.metric)} aria-label={`Add ${item.title}`}><Plus /> Add</Button></div><p className="text-xs leading-relaxed text-muted-foreground">{item.description}</p><SourceLabel source={item.source} />{item.value === null ? <Badge variant="missing" className="ml-3">Missing input</Badge> : null}{item.metric === "labour" && viewer === "Manager" ? <p className="text-xs text-muted-foreground">This viewer will see a permission notice.</p> : null}</Card>)}{!catalogue.some((item) => `${item.title} ${item.description}`.toLowerCase().includes(query.toLowerCase())) ? <p className="text-sm text-muted-foreground">No matching widgets. Try “sales” or “covers”.</p> : null}</div></> : panel === "widget" ? widget ? <><div className="space-y-2"><Label htmlFor="widget-title">Widget title</Label><Input id="widget-title" value={widget.title} onChange={(event) => patchWidget(widget.id, { title: event.target.value })} /></div><Choice label="Metric" value={widget.metric} options={catalogue.map((item) => item.metric)} onChange={(metric) => patchWidget(widget.id, { metric, title: catalogue.find((item) => item.metric === metric)!.title, display: metric === "mix" ? "ranked" : "stat" })} /><Choice label="Display" value={widget.display} options={widget.metric === "mix" ? ["ranked"] : widget.metric === "sales" ? ["stat", "trend"] : ["stat"]} onChange={(display) => patchWidget(widget.id, { display: display as Widget["display"] })} /><Choice label="Width in canvas layout" value={widget.width} options={["half", "full"]} onChange={(width) => patchWidget(widget.id, { width: width as Widget["width"] })} /><p className="text-xs text-muted-foreground">The outline direction uses a full-width reading order. Canvas widths are retained when switching directions.</p><div className="rounded-xl border p-4"><WidgetContent widget={widget} draft={draft} viewer={viewer} /></div><Button variant="outline" onClick={() => { props.remove(widget.id); closePanel(); }}><Trash2 /> Remove widget</Button></> : <p className="text-sm text-muted-foreground">This widget is no longer in the draft.</p> : settings}
      <Button className="w-full" onClick={closePanel}>Done</Button>
    </div></SheetContent></Sheet>
  </SidebarProvider>;
}
