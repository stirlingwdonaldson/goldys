"use client";
import { useState } from "react";
import { useApi } from "@/lib/demo-mode";
import type { CategoryNode } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import { InlineError } from "@/components/states/inline-error";
export function CategoryCriteriaEditor({
  category,
  onSaved,
}: {
  category: CategoryNode;
  onSaved: () => void;
}) {
  const api = useApi();
  const [description, setDescription] = useState(category.description ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor="category-criteria">
        Category criteria: inclusions, exclusions and counterexamples
      </label>
      <textarea
        id="category-criteria"
        className="min-h-24 rounded border bg-background p-2"
        value={description}
        onChange={(e) => {
          setDescription(e.target.value);
          setSaved(false);
        }}
      />
      <Button
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          setError(null);
          try {
            await api.renameCategory(category.id, { description });
            setSaved(true);
            onSaved();
          } catch (e) {
            setError(
              e instanceof Error ? e.message : "Could not save criteria",
            );
          } finally {
            setBusy(false);
          }
        }}
      >
        Save criteria
      </Button>
      {error ? <InlineError>{error}</InlineError> : null}
      {saved ? (
        <p role="status">
          Criteria saved. Financial reporting mappings are reviewed separately.
        </p>
      ) : null}
    </div>
  );
}
