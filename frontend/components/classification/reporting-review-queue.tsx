"use client";
import type {
  ReportingReviewItem,
  ReportingReviewQueue,
} from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { formatNumber } from "@/lib/format";

export function ReportingReviewQueuePanel({
  data,
  busy,
  from,
  to,
  domain,
  page,
  onRange,
  onDomain,
  onLoad,
  onSelect,
}: {
  data: ReportingReviewQueue | null;
  busy: boolean;
  from: string;
  to: string;
  domain: "SALES" | "SALES_BASIS" | "WASTAGE";
  page: number;
  onRange: (from: string, to: string) => void;
  onDomain: (domain: "SALES" | "SALES_BASIS" | "WASTAGE") => void;
  onLoad: (page: number) => void;
  onSelect: (item: ReportingReviewItem) => void;
}) {
  return (
    <section
      aria-label="Ongoing reporting review queue"
      className="flex flex-col gap-3 rounded border p-3 min-w-0"
    >
      <h3 className="font-semibold">Ongoing reporting review queue</h3>
      <p>
        Review up to 92 days. Sales rank by absolute unclassified ex-GST
        exposure; missing-tax gross and unresolved observations are separate.
        Wastage ranks by absolute recorded quantity × historical cost.
        Zero-value unknowns remain visible. No automatic approvals or
        notifications.
      </p>
      <div className="flex flex-wrap gap-3">
        <label>
          Review from
          <Input
            type="date"
            value={from}
            disabled={busy}
            onChange={(e) => onRange(e.target.value, to)}
          />
        </label>
        <label>
          Review to
          <Input
            type="date"
            value={to}
            disabled={busy}
            onChange={(e) => onRange(from, e.target.value)}
          />
        </label>
        <label>
          Review domain
          <select
            className="block rounded border bg-background p-2"
            value={domain}
            disabled={busy}
            onChange={(e) =>
              onDomain(e.target.value as "SALES" | "SALES_BASIS" | "WASTAGE")
            }
          >
            <option value="SALES">Sales — unclassified ex-GST</option>
            <option value="SALES_BASIS">
              Sales — missing tax or unresolved
            </option>
            <option value="WASTAGE">Wastage</option>
          </select>
        </label>
        <Button disabled={busy} onClick={() => onLoad(0)}>
          Refresh review queue
        </Button>
      </div>
      {busy ? <p role="status">Loading or reviewing…</p> : null}
      {data ? (
        <>
          <p>
            Wastage classification coverage (observations):{" "}
            {data.wastage.classifiedLines}/{data.wastage.lines}. Reason
            coverage: {data.wastage.reasonLines}/{data.wastage.lines}. Missing
            quantity/cost: {data.wastage.missingCost}; undated:{" "}
            {data.wastage.missingDate}. Known recorded value:{" "}
            {formatNumber(data.wastage.knownValue, 4)}; unclassified:{" "}
            {formatNumber(data.wastage.unknownValue, 4)}. Items without a
            recorded date need source review in Data health; no dates are
            inferred. Missing days and costs are not zero.
          </p>
          <details
            open={data.days.some(
              (d) => d.belowThreshold || d.missingTax || d.unresolved,
            )}
          >
            <summary>Daily sales classification coverage and alerts</summary>
            <ul>
              {data.days.map((d) => (
                <li key={d.date}>
                  {d.date}:{" "}
                  {formatNumber(
                    d.salesCoverage === null ? null : d.salesCoverage * 100,
                    4,
                  )}
                  %; unclassified ex-GST {formatNumber(d.unclassifiedSales, 4)};
                  missing-tax observations {d.missingTax}; unresolved{" "}
                  {d.unresolved}.{" "}
                  {d.belowThreshold
                    ? "Below 95% — reporting held"
                    : d.salesCoverage === null
                      ? "No measurable sales coverage"
                      : "At or above 95%"}
                </li>
              ))}
            </ul>
          </details>
          <p>
            Page {page + 1}. {data.items.length} identities. Current policies
            below apply to observations within this range; different effective
            revisions remain visible.
          </p>
          {data.items.length === 0 ? (
            <p>No unmapped or held identities on this page.</p>
          ) : null}
          <ul className="flex flex-col gap-3">
            {data.items.map((item) => (
              <li
                key={`${item.scope}/${item.sourceSystem}/${item.entityKey}`}
                className="rounded border p-3 min-w-0 break-words"
              >
                <p className="font-medium">{item.names}</p>
                <p>
                  {item.sourceSystem} / {item.scope} / {item.entityKey}
                </p>
                <p>
                  First/last in range: {item.firstDate ?? "Date unknown"} /{" "}
                  {item.lastDate ?? "Date unknown"}; observations:{" "}
                  {item.observations}.
                </p>
                <p>
                  Known absolute{" "}
                  {domain !== "WASTAGE" ? "unclassified ex-GST" : "recorded"}{" "}
                  exposure: {formatNumber(item.knownExposure, 4)}. Missing
                  basis: {item.missingBasis}; known gross awaiting basis:{" "}
                  {formatNumber(item.missingBasisGross, 4)}; unresolved/undated:{" "}
                  {item.unresolved}.
                </p>
                <p>Held: {item.holds}</p>
                <pre className="whitespace-pre-wrap break-words text-sm">
                  {item.policies}
                </pre>
                <details>
                  <summary>Source evidence and qualifications</summary>
                  <pre className="max-h-80 overflow-auto whitespace-pre-wrap break-words text-sm">
                    {item.evidence}
                  </pre>
                </details>
                <Button
                  variant="outline"
                  disabled={busy}
                  onClick={() => onSelect(item)}
                >
                  Review {item.entityKey}
                </Button>
                {item.recipeId && item.recipeId > 0 ? (
                  <Button
                    variant="outline"
                    disabled={busy}
                    onClick={() =>
                      onSelect({
                        ...item,
                        scope: "CTB_RECIPE",
                        entityKey: String(item.recipeId),
                      })
                    }
                  >
                    Review recipe {item.recipeId}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
          <div className="flex gap-2">
            <Button
              disabled={busy || page === 0}
              onClick={() => onLoad(page - 1)}
            >
              Previous review page
            </Button>
            <Button
              disabled={busy || !data.hasMore}
              onClick={() => onLoad(page + 1)}
            >
              Next review page
            </Button>
          </div>
        </>
      ) : (
        <p>
          Choose a range and refresh to find new identities and dates needing
          attention.
        </p>
      )}
    </section>
  );
}
