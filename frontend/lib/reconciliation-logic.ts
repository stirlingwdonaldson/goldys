import type {
  ReconciliationException,
  ReconciliationField,
  ReconciliationRecord,
} from "@/lib/api";

/** A field needs a decision when its sources disagree or a source has no data. */
export function needsDecision(field: ReconciliationField): boolean {
  if (field.overridden) return false;
  // A single source has nothing to compare against — never a conflict.
  if (field.sources.length < 2) return false;
  const values = field.sources.map((s) => s.value);
  const anyMissing = field.sources.some((s) => s.value === null);
  const allEqual = values.every((v) => v === values[0]);
  return anyMissing || !allEqual;
}

/** Derive open reconciliation exceptions from records, skipping overridden fields. */
export function deriveExceptions(
  records: Record<string, ReconciliationRecord>,
): ReconciliationException[] {
  const result: ReconciliationException[] = [];
  for (const record of Object.values(records)) {
    for (const field of record.fields) {
      if (!needsDecision(field)) continue;
      const anyMissing = field.sources.some((s) => s.value === null);
      result.push({
        id: `${record.id}:${field.name}`,
        recordId: record.id,
        entity: record.entity,
        field: field.name,
        sources: field.sources,
        status: anyMissing ? "missing" : "conflict",
      });
    }
  }
  return result;
}
