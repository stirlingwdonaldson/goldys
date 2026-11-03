"use client";
import { useRef, useState } from "react";
import { useApi } from "@/lib/demo-mode";
import type {
  ReportingMapping,
  ReportingMappingPreview,
  ClassifiedWasteLine,
} from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { InlineError } from "@/components/states/inline-error";
import { formatNumber } from "@/lib/format";

const choices = {
  scope: [
    "PRODUCT_NAME",
    "CTB_PRODUCT",
    "CTB_RECIPE",
    "CTB_STOCK",
    "WASTE_LINE",
  ],
  salesNature: ["UNKNOWN", "FOOD", "BEVERAGE", "OTHER"],
  stockBucket: [
    "UNKNOWN",
    "KEGS",
    "WINE",
    "SPIRITS",
    "TINNIES",
    "OTHER_BEVERAGE",
    "MERCHANDISE",
    "MEAT",
    "SEAFOOD",
    "POULTRY",
    "DAIRY",
    "FROZEN",
    "BREAD",
  ],
  itemRole: [
    "UNKNOWN",
    "SALE_PRODUCT",
    "INGREDIENT",
    "MODIFIER",
    "MERCHANDISE",
    "OTHER",
  ],
  businessArea: ["UNKNOWN", "PUB", "CAFE", "SHARED"],
  lifecycle: ["UNKNOWN", "CURRENT", "HISTORICAL", "RETIRED"],
} as const;
const labels = {
  scope: "Identity scope",
  salesNature: "Sales nature",
  stockBucket: "Stock bucket",
  itemRole: "Item role",
  businessArea: "Business area",
  lifecycle: "Lifecycle",
};
export function ReportingMappingEditor() {
  const api = useApi();
  const today = new Date().toISOString().slice(0, 10);
  const [mapping, setMapping] = useState<ReportingMapping>({
    scope: "PRODUCT_NAME",
    sourceSystem: "RESOLVED",
    entityKey: "",
    effectiveFrom: today,
    salesNature: "UNKNOWN",
    stockBucket: "UNKNOWN",
    itemRole: "UNKNOWN",
    businessArea: "UNKNOWN",
    lifecycle: "UNKNOWN",
    reason: "",
  });
  const draftRevision = useRef(0);
  const [through, setThrough] = useState(today);
  const [preview, setPreview] = useState<ReportingMappingPreview | null>(null);
  const [history, setHistory] = useState<ReportingMapping[] | null>(null);
  const [lines, setLines] = useState<ClassifiedWasteLine[] | null>(null);
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  function change(patch: Partial<ReportingMapping>) {
    draftRevision.current++;
    setMapping((m) => ({ ...m, ...patch }));
    setPreview(null);
    setMessage("");
  }
  async function act(work: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      await work();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Review failed");
    } finally {
      setBusy(false);
    }
  }
  async function loadWaste(next: number) {
    await act(async () => {
      setLines(
        await api.getClassifiedWastage(mapping.effectiveFrom, through, next),
      );
      setPage(next);
    });
  }
  return (
    <section
      aria-label="Reporting mapping review"
      className="flex flex-col gap-4"
    >
      <p>
        Review financial mappings separately from browsing categories. Choose
        the exact observed identity and effective date. An earlier date restates
        reporting from that date. Keep uncertain identities, café membership and
        lifecycle unknown.
      </p>
      <div className="grid gap-3 md:grid-cols-3">
        {Object.entries(choices).map(([key, values]) => (
          <label key={key} className="flex flex-col gap-1 text-sm">
            {labels[key as keyof typeof labels]}
            <select
              className="rounded border bg-background p-2"
              value={mapping[key as keyof typeof choices]}
              disabled={busy}
              onChange={(e) => {
                const value = e.target.value;
                change(
                  key === "scope"
                    ? {
                        scope: value as ReportingMapping["scope"],
                        sourceSystem:
                          value === "PRODUCT_NAME" ? "RESOLVED" : "CTB",
                        entityKey: "",
                      }
                    : { [key]: value },
                );
              }}
            >
              {values.map((v) => (
                <option key={v} value={v}>
                  {v.replaceAll("_", " ")}
                </option>
              ))}
            </select>
          </label>
        ))}
        <label className="flex flex-col gap-1 text-sm">
          Exact identity ({mapping.sourceSystem})
          <Input
            value={mapping.entityKey}
            disabled={busy}
            onChange={(e) => change({ entityKey: e.target.value })}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Effective from
          <Input
            type="date"
            value={mapping.effectiveFrom}
            disabled={busy}
            onChange={(e) => change({ effectiveFrom: e.target.value })}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Preview through (up to one year)
          <Input
            type="date"
            value={through}
            disabled={busy}
            onChange={(e) => {
              draftRevision.current++;
              setThrough(e.target.value);
              setPreview(null);
            }}
          />
        </label>
      </div>
      <label className="flex flex-col gap-1 text-sm">
        Review reason and evidence
        <textarea
          className="min-h-24 rounded border bg-background p-2"
          maxLength={4000}
          value={mapping.reason}
          disabled={busy}
          onChange={(e) => change({ reason: e.target.value })}
        />
      </label>
      <div className="flex flex-wrap gap-2">
        <Button
          disabled={busy || !mapping.entityKey.trim() || !mapping.reason.trim()}
          onClick={() =>
            void act(async () => {
              const revision = draftRevision.current;
              setPreview(null);
              const result = await api.previewReportingMapping(
                mapping,
                through,
              );
              if (revision === draftRevision.current) setPreview(result);
            })
          }
        >
          Preview mapping impact
        </Button>
        <Button
          variant="outline"
          disabled={busy}
          onClick={() =>
            void act(async () =>
              setHistory(await api.getReportingMappings(through)),
            )
          }
        >
          Load revision history
        </Button>
        <Button
          variant="outline"
          disabled={busy}
          onClick={() => void loadWaste(0)}
        >
          Load wastage evidence
        </Button>
      </div>
      {error ? <InlineError>{error}</InlineError> : null}
      {message ? <p role="status">{message}</p> : null}
      {preview ? (
        <div className="flex flex-col gap-3 rounded border p-3">
          <p>
            {preview.reviewable
              ? "Identity can be reviewed"
              : "Identity held: do not approve"}
            . {preview.observedRows} affected observations through{" "}
            {preview.through}; known amount{" "}
            {formatNumber(preview.knownAmount, 2)}. {preview.amountBasis}.
          </p>
          <pre className="max-h-80 overflow-auto whitespace-pre-wrap text-sm">
            {preview.evidence}
          </pre>
          <ul>
            {preview.notes.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
          <p>
            Proposed identity: {preview.mapping.sourceSystem} /{" "}
            {preview.mapping.scope} / {preview.mapping.entityKey}. Decision:{" "}
            {preview.mapping.salesNature}, effective{" "}
            {preview.mapping.effectiveFrom}. Model agreement or confidence does
            not approve this decision.
          </p>
          <Button
            disabled={busy || !preview.reviewable}
            onClick={() =>
              void act(async () => {
                await api.saveReportingMapping(preview.mapping);
                setPreview(null);
                setMessage(
                  "Reviewed revision saved. Earlier revisions remain in history.",
                );
                if (history)
                  setHistory(await api.getReportingMappings(through));
              })
            }
          >
            Approve reviewed mapping
          </Button>
        </div>
      ) : null}
      {history ? (
        <div>
          <p>
            {history.length} revisions through {through}. Showing up to 50 for
            the entered identity; clear identity to browse the latest.
          </p>
          <ul>
            {history
              .filter(
                (m) => !mapping.entityKey || m.entityKey === mapping.entityKey,
              )
              .slice(0, 50)
              .map((m, i) => (
                <li key={i} className="border-b py-2">
                  <Button
                    variant="link"
                    disabled={busy}
                    onClick={() => change(m)}
                  >
                    {m.scope}: {m.entityKey} — {m.salesNature} from{" "}
                    {m.effectiveFrom}
                  </Button>
                  <p>{m.reason}</p>
                </li>
              ))}
          </ul>
        </div>
      ) : null}
      {lines ? (
        <div className="overflow-auto">
          <p>
            Recorded wastage, page {page + 1}. Missing values remain visible.
            Choose a recipe or exact line to review.
          </p>
          <table className="w-full text-sm">
            <thead>
              <tr>
                {[
                  "Date / item",
                  "Quantity / unit",
                  "Source unit cost",
                  "Reason",
                  "Classification / evidence",
                  "Review",
                ].map((h) => (
                  <th key={h} className="p-2 text-left">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => (
                <tr key={l.sourceRecordRef} className="border-t">
                  <td className="p-2">
                    {l.date} {l.name}
                    <br />
                    {l.sourceRecordRef}
                  </td>
                  <td className="p-2">
                    {formatNumber(l.quantity, 4)} {l.unit ?? "Unknown unit"}{" "}
                    {l.unitSize}
                  </td>
                  <td className="p-2">{formatNumber(l.sourceUnitCost, 4)}</td>
                  <td className="p-2">{l.reason || "No reason recorded"}</td>
                  <td className="p-2">
                    {l.salesNature}
                    <br />
                    {l.mappingScope} {l.mappingReason}
                  </td>
                  <td className="p-2">
                    <Button
                      variant="link"
                      disabled={busy}
                      onClick={() =>
                        change({
                          scope: "WASTE_LINE",
                          sourceSystem: "CTB",
                          entityKey: l.sourceRecordRef,
                        })
                      }
                    >
                      Exact line
                    </Button>
                    {l.recipeId && l.recipeId > 0 ? (
                      <Button
                        variant="link"
                        disabled={busy}
                        onClick={() =>
                          change({
                            scope: "CTB_RECIPE",
                            sourceSystem: "CTB",
                            entityKey: String(l.recipeId),
                          })
                        }
                      >
                        Recipe {l.recipeId}
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex gap-2">
            <Button
              disabled={busy || page === 0}
              onClick={() => void loadWaste(page - 1)}
            >
              Previous wastage page
            </Button>
            <Button
              disabled={busy || lines.length < 50}
              onClick={() => void loadWaste(page + 1)}
            >
              Next wastage page
            </Button>
          </div>
        </div>
      ) : null}
    </section>
  );
}
