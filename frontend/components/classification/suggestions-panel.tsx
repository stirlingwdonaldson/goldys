"use client";

import { useState } from "react";
import type { CategorySuggestion } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/states/empty-state";

/**
 * The "Suggestions" review surface: the LLM's draft taxonomy, one row per suggestion with inline
 * name/group editing, plus the controls to add a missed category and apply the whole draft.
 */
export function SuggestionsPanel({
  suggestions,
  applying,
  onUpdate,
  onDelete,
  onAdd,
  onApply,
}: {
  suggestions: CategorySuggestion[];
  applying: boolean;
  onUpdate: (id: string, input: { name?: string | null; groupName?: string | null }) => void;
  onDelete: (id: string) => void;
  onAdd: (name: string, groupName: string | null) => void;
  onApply: () => void;
}) {
  const [draftName, setDraftName] = useState("");
  const [draftGroup, setDraftGroup] = useState("");

  if (suggestions.length === 0) {
    return (
      <EmptyState
        title="No suggestions yet"
        description="Run classification to generate suggestions."
      />
    );
  }

  const add = () => {
    const name = draftName.trim();
    if (!name) return;
    onAdd(name, draftGroup.trim() === "" ? null : draftGroup.trim());
    setDraftName("");
    setDraftGroup("");
  };

  return (
    <div className="flex flex-col gap-4">
      <ul className="flex flex-col gap-3">
        {suggestions.map((suggestion) => (
          <li key={suggestion.id} className="flex flex-col gap-2 rounded-lg border bg-card p-3">
            <div className="flex flex-wrap items-center gap-2">
              <Input
                aria-label={`Name for ${suggestion.name}`}
                defaultValue={suggestion.name}
                className="h-8 w-48"
                onBlur={(event) => {
                  const next = event.target.value.trim();
                  if (next && next !== suggestion.name) onUpdate(suggestion.id, { name: next });
                }}
              />
              <Input
                aria-label={`Group for ${suggestion.name}`}
                defaultValue={suggestion.groupName ?? ""}
                placeholder="Group (optional)"
                className="h-8 w-40"
                onBlur={(event) => {
                  const next = event.target.value.trim();
                  onUpdate(suggestion.id, { groupName: next === "" ? null : next });
                }}
              />
              <Button
                variant="ghost"
                size="sm"
                aria-label={`Delete suggestion ${suggestion.name}`}
                onClick={() => onDelete(suggestion.id)}
              >
                Delete
              </Button>
            </div>
            {suggestion.hint ? (
              <p className="text-xs text-muted-foreground">{suggestion.hint}</p>
            ) : null}
          </li>
        ))}
      </ul>

      <div className="flex flex-wrap items-center gap-2">
        <Input
          aria-label="New category name"
          value={draftName}
          onChange={(event) => setDraftName(event.target.value)}
          placeholder="Add a category the model missed"
          className="h-8 w-56"
        />
        <Input
          aria-label="New category group"
          value={draftGroup}
          onChange={(event) => setDraftGroup(event.target.value)}
          placeholder="Group (optional)"
          className="h-8 w-40"
        />
        <Button variant="outline" size="sm" onClick={add} disabled={draftName.trim() === ""}>
          Add category
        </Button>
      </div>

      <div>
        <Button variant="brand" onClick={onApply} disabled={applying}>
          {applying ? "Applying…" : "Apply taxonomy"}
        </Button>
      </div>
    </div>
  );
}
