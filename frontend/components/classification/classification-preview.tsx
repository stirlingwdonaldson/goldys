"use client";

import { useState } from "react";
import { z } from "zod";
import { useApi } from "@/lib/demo-mode";
import { Button } from "@/components/ui/button";
import { InlineError } from "@/components/states/inline-error";

const proposalsSchema = z.array(z.object({
  product: z.string(),
  categoryId: z.string().nullable().optional(),
  status: z.string(),
}));
const contextSchema = z.object({
  businessContext: z.string(),
  categoryOptions: z.array(z.object({ categoryId: z.string(), name: z.string() })),
  candidates: z.record(z.string(), z.object({ text: z.string(), ambiguous: z.boolean() })).optional(),
});
type Review = {
  context: z.infer<typeof contextSchema>;
  proposals: z.infer<typeof proposalsSchema>;
};

/** Load potentially large evidence only when the reviewer asks to see it. */
export function ClassificationPreviewPanel({ runId }: { runId: string }) {
  const api = useApi();
  const [review, setReview] = useState<Review | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const snapshot = await api.getClassificationPreview(runId);
      if (!snapshot.contextSnapshot) {
        throw new Error("This historical run has no saved review preview.");
      }
      setReview({
        context: contextSchema.parse(JSON.parse(snapshot.contextSnapshot)),
        proposals: proposalsSchema.parse(JSON.parse(snapshot.proposalSnapshot ?? "[]")),
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load review preview");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section aria-label="Classification review preview" className="flex flex-col gap-3">
      <p className="text-sm text-muted-foreground">
        New runs propose changes for review. Existing assignments stay in place.
        Reporting mappings are reviewed separately from menu categories.
      </p>
      <Button variant="outline" disabled={loading} onClick={() => void load()}>
        {loading ? "Loading preview…" : "View review preview"}
      </Button>
      {error ? <InlineError>{error}</InlineError> : null}
      {review ? (
        <>
          <p>
            {review.proposals.length} products in this saved preview. Review the evidence
            before recording an approved manual move in Categories.
          </p>
          <details>
            <summary>Business context used</summary>
            <p className="whitespace-pre-wrap text-sm">{review.context.businessContext}</p>
          </details>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <caption className="sr-only">Proposed categories, awaiting review</caption>
              <thead>
                <tr>
                  <th scope="col" className="text-left">Product</th>
                  <th scope="col" className="text-left">Proposal</th>
                  <th scope="col" className="text-left">Review state</th>
                </tr>
              </thead>
              <tbody>
                {review.proposals.slice(page * 50, (page + 1) * 50).map((proposal) => (
                  <tr key={proposal.product}>
                    <td>
                      <details>
                        <summary>{proposal.product}</summary>
                        <p className="max-w-xl whitespace-pre-wrap">
                          {review.context.candidates?.[proposal.product]?.text
                            ?? "Item evidence was not saved for this historical preview."}
                        </p>
                      </details>
                    </td>
                    <td>
                      {review.context.categoryOptions.find((category) => category.categoryId === proposal.categoryId)?.name
                        ?? "No category proposed"}
                    </td>
                    <td>{proposal.status.toLowerCase().replaceAll("_", " ")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </Button>
            <span>Page {page + 1} of {Math.max(1, Math.ceil(review.proposals.length / 50))}</span>
            <Button
              variant="outline"
              disabled={(page + 1) * 50 >= review.proposals.length}
              onClick={() => setPage(page + 1)}
            >
              Next
            </Button>
          </div>
        </>
      ) : null}
    </section>
  );
}
