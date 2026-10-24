"use client";
import Link from "next/link";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import type {
  RecipeDetails,
  RecipeComposition,
  RecipeEvidence,
  RecipeSection,
  RecipeReference,
  RecipeField,
} from "@/lib/api/types";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { RecipeCompositionPanel } from "./recipe-composition-panel";
import {
  recipeFieldValue,
  selectedRecipeLabels,
  recipeHref,
} from "./recipe-presentation";
import { Section as ContentSection } from "@/components/layout/section";
import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { formatCurrency, formatDateTime } from "@/lib/format";

const labels: Record<string, string> = {
  CATALOGUE_ENTRY: "Catalogue listing",
  MAJOR_FLAVOUR: "Major flavours",
  MAJOR_INGREDIENT: "Major ingredients",
  COOKING_METHOD: "Cooking methods",
  SECTION: "Sections",
  DISH_TYPE: "Dish types",
  HEADING: "Headings",
  isActive: "Active",
  isLocked: "Locked",
  HEADER: "Recipe details and method",
  STOCKS: "Direct ingredient details",
  USAGES: "Sub-recipe usage details",
  EQUIPMENT: "Equipment",
  EQUIPMENT_OPTIONS: "Available equipment choices",
  NUTRITION: "Nutrition",
  DIETARY: "Dietary selections",
  ALLERGENS: "Allergen selections",
  INGREDIENT_METHODS: "Ingredient methods",
  LEGACY_COST: "Legacy CTB costs",
  POS_PRICE_INPUT: "POS price and cost evidence",
  recipeDesc: "Method",
  recipeHandyHints: "Handy hints",
  isChecked: "Selected in CTB",
  noOfPortion: "Number of portions",
  portionIncludingLabour: "Cost per portion including labour",
  recipeId: "CTB recipe ID",
};
function label(path: string) {
  return (
    path
      .replace(/^data\.?/, "")
      .split(".")
      .map(
        (part) =>
          labels[part] ??
          part
            .replace(/([a-z])([A-Z])/g, "$1 $2")
            .replace(/_/g, " ")
            .replace(/\[(\d+)\]/g, (_, n) => ` ${Number(n) + 1}`),
      )
      .join(" · ")
      .replace(/^./, (c) => c.toUpperCase()) || "Recorded entries"
  );
}
function when(value: string) {
  return new Date(value).toLocaleString("en-AU", {
    timeZone: "Australia/Melbourne",
  });
}
function stale(value: string, limit: number) {
  return Date.now() - new Date(value).getTime() > limit * 1000;
}
function Source({
  evidence,
  fields = [],
}: {
  evidence: RecipeEvidence;
  fields?: RecipeField[];
}) {
  return (
    <details className="rounded border p-3 text-xs">
      <summary className="cursor-pointer font-medium">
        Source: {evidence.sourceSystem} · {when(evidence.capturedAt)}
      </summary>
      <dl className="mt-2 grid gap-2">
        <div>
          <dt>Import</dt>
          <dd>{evidence.fetcher}</dd>
        </div>
        <div>
          <dt>Endpoint</dt>
          <dd className="break-all">{evidence.endpoint}</dd>
        </div>
        {evidence.sourceRecordRef ? (
          <div>
            <dt>Source reference</dt>
            <dd className="break-all">{evidence.sourceRecordRef}</dd>
          </div>
        ) : null}
        {evidence.ingestionRunId ? (
          <div>
            <dt>Ingestion run</dt>
            <dd>{evidence.ingestionRunId}</dd>
          </div>
        ) : null}
        {evidence.entityRefs?.length ? (
          <div>
            <dt>Contributing source entities</dt>
            <dd className="max-h-40 overflow-auto break-all">
              {evidence.entityRefs.join(", ")}
            </dd>
          </div>
        ) : null}
        {evidence.sha256 ? (
          <div>
            <dt>Capture SHA-256</dt>
            <dd className="break-all">{evidence.sha256}</dd>
          </div>
        ) : null}
      </dl>
      {evidence.rawRecordId ? (
        <Link
          className="mt-2 inline-block underline"
          href={`/data?layer=raw&id=${encodeURIComponent(evidence.rawRecordId)}`}
        >
          Inspect retained response
        </Link>
      ) : (
        <p className="mt-2 text-muted-foreground">
          Raw evidence access is restricted or unavailable.
        </p>
      )}
      {fields.length ? (
        <details className="mt-3">
          <summary className="cursor-pointer">
            Field paths in the retained response
          </summary>
          <ul className="mt-2 space-y-1">
            {fields.map((field) => (
              <li className="break-all" key={field.path}>
                <code>{field.path}</code>
              </li>
            ))}
          </ul>
          <p className="mt-2">
            Text is extracted as plain text; the retained response preserves its
            original representation.
          </p>
        </details>
      ) : null}
    </details>
  );
}
function Relationships({
  title,
  items,
  listHref,
}: {
  title: string;
  items: RecipeReference[];
  listHref?: string;
}) {
  return (
    <div>
      <h3 className="font-medium">{title}</h3>
      {items.length ? (
        <ul className="mt-1 space-y-1">
          {items.map((item, index) => (
            <li key={`${item.recipeId}:${index}`}>
              <Link
                className="underline"
                href={recipeHref(item.recipeId, listHref)}
              >
                {item.name ?? "Unnamed recipe"} · #{item.recipeId}
              </Link>
              {!item.available ? " · Details not received" : ""}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-muted-foreground">
          No links recorded in captured recipe data.
        </p>
      )}
    </div>
  );
}
function Section({
  section,
  limit,
}: {
  section: RecipeSection;
  limit: number;
}) {
  return (
    <details className="rounded border p-4" open={section.section === "HEADER"}>
      <summary className="cursor-pointer font-medium">
        {labels[section.section] ?? label(section.section)}
        {stale(section.evidence.capturedAt, limit) ? " · Refresh overdue" : ""}
      </summary>
      <div className="mt-3 flex flex-col gap-3">
        {section.notice ? (
          <p className="text-muted-foreground">{section.notice}</p>
        ) : null}
        {section.section === "NUTRITION" ? (
          <p className="text-muted-foreground">
            Values as reported by CTB. Its screen shows per-serving and per-100g
            figures, but this response does not identify the basis. Zero does
            not establish nutritional composition.
          </p>
        ) : null}
        {section.section === "ALLERGENS" ? (
          <p className="text-muted-foreground">
            Unchecked means not selected in CTB; it does not establish that an
            allergen is absent.
          </p>
        ) : null}
        <dl className="divide-y">
          {section.fields.map((field) => (
            <div className="grid gap-1 py-2 sm:grid-cols-2" key={field.path}>
              <dt
                title={`Source field: ${field.path}`}
                className="text-muted-foreground"
              >
                {label(field.path)}
              </dt>
              <dd className="whitespace-pre-wrap break-words">
                {recipeFieldValue(field)}
              </dd>
            </div>
          ))}
        </dl>
        <Source evidence={section.evidence} fields={section.fields} />
      </div>
    </details>
  );
}
function OptionalSection({ section }: { section: RecipeSection }) {
  const selected = selectedRecipeLabels(section);
  const nutrition = section.section === "NUTRITION";
  const allZero =
    nutrition &&
    section.fields.some((f) => f.type === "NUMBER") &&
    section.fields
      .filter((f) => f.type === "NUMBER")
      .every((f) => f.value === "0");
  return (
    <div className="rounded-lg border p-3">
      <h3 className="text-sm font-medium">
        {labels[section.section] ?? label(section.section)}
      </h3>
      <div className="mt-2 flex flex-wrap gap-2">
        {selected.length ? (
          selected.map((name, index) => (
            <Badge key={`${name}:${index}`} variant="neutral">
              {name}
            </Badge>
          ))
        ) : (
          <p className="text-sm text-muted-foreground">
            {allZero
              ? "CTB reports zero for every recorded nutrient; nutritional basis is unverified."
              : nutrition
                ? "Values reported by CTB; serving / per-100g basis is unverified. See source details."
                : section.fields.some((f) => f.path.endsWith(".isChecked"))
                  ? "None selected in CTB."
                  : "No entries recorded in CTB."}
          </p>
        )}
      </div>
    </div>
  );
}

export function RecipeDetailsPanel({
  recipeId,
  listHref,
}: {
  recipeId: number;
  listHref?: string;
}) {
  const api = useApi();
  const result = useQuery({
    queryKey: ["getRecipeDetails", recipeId],
    queryFn: ({ signal }) => api.getRecipeDetails(recipeId, { signal }),
  });
  const composition = useQuery({
    queryKey: ["getRecipeComposition", recipeId],
    queryFn: ({ signal }) => api.getRecipeComposition(recipeId, { signal }),
  });
  if (result.isPending) return <LoadingState rows={4} />;
  if (result.isLoadingError)
    return (
      <ErrorState
        title="Couldn't load recipe information"
        message={result.error.message}
        onRetry={result.refetch}
      />
    );
  const data = result.data;
  if (!data)
    return (
      <p role="status">
        Recipe details have not been received. This ID may be missing or
        unavailable in the captured catalogue.
      </p>
    );
  return (
    <div className="flex min-w-0 flex-col gap-6">
      <RecipeOverview data={data} composition={composition.data} />{" "}
      {composition.isPending ? (
        <LoadingState rows={3} />
      ) : composition.isLoadingError ? (
        <ErrorState
          title="Couldn't load composition"
          message={composition.error.message}
          onRetry={composition.refetch}
        />
      ) : composition.data ? (
        <RecipeCompositionPanel data={composition.data} listHref={listHref} />
      ) : (
        <>
          <LegacyComposition data={data} listHref={listHref} />
        </>
      )}
      {data.usedIn.length ? (
        <ContentSection title="Used in">
          <Relationships
            title="Parent recipes"
            items={data.usedIn}
            listHref={listHref}
          />
        </ContentSection>
      ) : null}
      <PosObservations data={data} /> <OptionalDetails data={data} />
      <SourceHistory data={data} />
    </div>
  );
}

function OptionalDetails({ data }: { data: RecipeDetails }) {
  const optionalNames = [
    "DIETARY",
    "ALLERGENS",
    "NUTRITION",
    "COOKING_METHOD",
    "MAJOR_FLAVOUR",
    "MAJOR_INGREDIENT",
    "DISH_TYPE",
    "SECTION",
    "HEADING",
    "EQUIPMENT",
    "INGREDIENT_METHODS",
  ];
  const optional = optionalNames.flatMap((name) =>
    data.sections.filter((s) => s.section === name),
  );
  const missing = optionalNames.filter(
    (name) => !data.sections.some((s) => s.section === name),
  );
  return (
    <ContentSection
      title="Dietary & other details"
      description="Selections recorded in CTB. Unchecked allergens do not establish absence; names and ingredients do not establish dietary suitability."
    >
      <div className="grid gap-3 sm:grid-cols-2">
        {optional
          .filter(
            (s) =>
              ["DIETARY", "ALLERGENS", "NUTRITION"].includes(s.section) ||
              selectedRecipeLabels(s).length > 0,
          )
          .map((s) => (
            <OptionalSection key={s.section} section={s} />
          ))}
      </div>
      <details className="text-sm text-muted-foreground">
        <summary className="cursor-pointer">
          Other sections & availability
        </summary>
        <ul className="mt-2 space-y-1">
          {optional
            .filter(
              (s) =>
                !["DIETARY", "ALLERGENS", "NUTRITION"].includes(s.section) &&
                !selectedRecipeLabels(s).length,
            )
            .map((s) => (
              <li key={s.section}>
                {labels[s.section] ?? label(s.section)}: No entries selected or
                recorded.
              </li>
            ))}
          {missing.map((name) => (
            <li key={name}>
              {labels[name] ?? label(name)}: No successful observation received.
            </li>
          ))}
        </ul>
      </details>
    </ContentSection>
  );
}

function SourceHistory({ data }: { data: RecipeDetails }) {
  return (
    <details id="source-details" className="min-w-0 rounded-xl border p-4">
      <summary className="cursor-pointer text-sm font-semibold">
        Source details & capture history
      </summary>
      <div className="mt-4 flex min-w-0 flex-col gap-4">
        <p className="text-sm text-muted-foreground">{data.costSelection}</p>
        {data.collection ? (
          <p className="text-xs text-muted-foreground">
            Latest CTB connector attempt:{" "}
            {data.collection.lastRunAt
              ? when(data.collection.lastRunAt)
              : "Not recorded"}{" "}
            · {data.collection.status}. Section capture times establish recipe
            freshness.
          </p>
        ) : null}
        {data.latestAttempts.map((section) => (
          <p className="text-xs text-muted-foreground" key={section.section}>
            Last attempt: {labels[section.section] ?? label(section.section)} ·{" "}
            {when(section.evidence.capturedAt)} ·{" "}
            {section.successful
              ? "Received"
              : "Failed; previous successful values retained where available"}
            {!section.successful ? ` · ${section.notice ?? "Unavailable"}` : ""}
          </p>
        ))}
        {data.sections.map((section, index) => (
          <Section
            key={`${section.section}:${index}`}
            section={section}
            limit={data.freshnessLimitSeconds ?? 36 * 60 * 60}
          />
        ))}
        {data.compositionSources.length ? (
          <details className="rounded border p-4">
            <summary className="cursor-pointer font-medium">
              Calculation input sources
            </summary>
            {data.compositionSources.map((e, index) => (
              <Source
                key={`${e.rawRecordId ?? e.endpoint}:${index}`}
                evidence={e}
              />
            ))}
          </details>
        ) : null}
      </div>
    </details>
  );
}

function LegacyComposition({
  data,
  listHref,
}: {
  data: RecipeDetails;
  listHref?: string;
}) {
  const oldIngredients = data.sections.filter((s) =>
    s.section.startsWith("LEGACY_INGREDIENT_"),
  );
  const method = data.sections
    .find((s) => s.section === "HEADER")
    ?.fields.find((f) => f.path === "recipeDesc")?.value;
  return (
    <>
      {" "}
      <p className="text-sm text-muted-foreground">
        Complete composition has not been received. Available source
        observations are shown below.
      </p>
      {method ? (
        <ContentSection title="Method">
          <p className="whitespace-pre-wrap text-sm">{method}</p>
        </ContentSection>
      ) : null}
      {data.sections
        .filter((s) => s.section === "LEGACY_COST")
        .map((section, index) => (
          <ContentSection title="Recorded CTB costs" key={index}>
            <dl className="flex flex-wrap gap-6 text-sm">
              {section.fields.map((field) => (
                <div key={field.path}>
                  <dt className="text-xs text-muted-foreground">
                    {label(field.path)}
                  </dt>
                  <dd className="tabular-nums">
                    {formatCurrency(field.value)}
                  </dd>
                </div>
              ))}
            </dl>
          </ContentSection>
        ))}
      {oldIngredients.length ? (
        <ContentSection
          title="Recorded ingredients"
          description="Legacy ingredient observations; sub-recipe quantities are not scaled into the parent."
        >
          <ul className="space-y-2 text-sm">
            {oldIngredients.map((s) => (
              <li key={s.section}>
                <strong>
                  {s.fields.find((f) => f.path === "stockDescription")?.value ??
                    "Unnamed ingredient"}
                </strong>
                {" · "}
                {s.fields.find((f) => f.path === "portionWeight")?.value ??
                  "—"}{" "}
                {s.fields.find((f) => f.path === "unitOfMeasurement")?.value ??
                  "—"}
                {" · "}
                {formatCurrency(
                  s.fields.find((f) => f.path === "costPerQty")?.value,
                )}
              </li>
            ))}
          </ul>
        </ContentSection>
      ) : null}
      <Relationships
        title="Uses sub-recipes"
        items={data.uses}
        listHref={listHref}
      />
    </>
  );
}

function PosObservations({ data }: { data: RecipeDetails }) {
  return (
    <>
      {" "}
      {data.sections.some((s) => s.section === "POS_PRICE_INPUT") ? (
        <ContentSection
          title="POS price observations"
          description="Associated sold-price inputs; these do not establish a current menu listing."
        >
          <div className="flex flex-wrap gap-4">
            {data.sections
              .filter((s) => s.section === "POS_PRICE_INPUT")
              .map((s, index) => (
                <dl key={index} className="flex flex-wrap gap-4 text-sm">
                  {s.fields
                    .filter((f) => f.value != null)
                    .map((f) => (
                      <div key={f.path}>
                        <dt className="text-xs text-muted-foreground">
                          {label(f.path)}
                        </dt>
                        <dd className="tabular-nums">
                          {f.path.toLowerCase().includes("date")
                            ? f.value
                            : formatCurrency(f.value)}
                        </dd>
                      </div>
                    ))}
                </dl>
              ))}
          </div>
        </ContentSection>
      ) : null}
    </>
  );
}

function RecipeOverview({
  data,
  composition,
}: {
  data: RecipeDetails;
  composition: RecipeComposition | null | undefined;
}) {
  const failed = data.latestAttempts.filter((s) => !s.successful);
  const path = composition?.folderPath ?? [];
  return (
    <>
      {" "}
      <PageHeader
        title={data.name?.trim() || "Unnamed recipe"}
        description="Cooking the Books recipe · source estimates"
      />
      <nav
        aria-label="Recipe folder"
        className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground"
      >
        <Link className="underline" href="/recipes">
          All recipes
        </Link>
        {path.map((folder, index) => (
          <span key={`${folder}:${index}`}>
            {" "}
            /{" "}
            <Link
              className="underline"
              href={`/recipes?folder=${encodeURIComponent(folder)}`}
            >
              {folder}
            </Link>
          </span>
        ))}
      </nav>
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="neutral">CTB #{data.recipeId}</Badge>
        <Badge variant="neutral">
          {composition ? "Details received" : "Complete details unavailable"}
        </Badge>
        {composition ? (
          <span className="text-xs text-muted-foreground">
            Observed {formatDateTime(composition.definition.observedAt)}
          </span>
        ) : null}
        {composition?.stale ? (
          <Badge variant="conflict">Refresh overdue</Badge>
        ) : null}
      </div>
      {data.collectionEnabled === false ? (
        <p role="status" className="text-sm text-muted-foreground">
          Recipe updates are paused. Displayed information may be out of date.
        </p>
      ) : null}
      {failed.length ? (
        <p
          role="status"
          className="rounded-lg bg-status-conflict-soft p-3 text-sm"
        >
          {failed.length} section refresh{failed.length === 1 ? "" : "es"}{" "}
          failed. Previous successful values are retained where available. See
          source details.
        </p>
      ) : null}
    </>
  );
}
