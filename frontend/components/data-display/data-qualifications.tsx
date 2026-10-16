/** Qualifications travel with results so partial subtotals cannot look complete. */
export function DataQualifications({ notices }: { notices?: string[] | null }) {
  if (!notices?.length) return null;
  const incomplete = notices.some((notice) => /incomplete|unresolved|partial coverage|missing inputs/i.test(notice));
  return (
    <div role="status" className="rounded-md border p-3 text-sm text-muted-foreground">
      {incomplete ? <strong className="text-foreground">Incomplete — known subtotals only. </strong> : null}
      <ul className="list-inside list-disc">
        {[...new Set(notices)].map((notice) => <li key={notice}>{notice}</li>)}
      </ul>
    </div>
  );
}
