/** Qualifications remain visible wherever their result is shown. */
export function DataQualifications({ notices }: { notices?: string[] | null }) {
  if (!notices?.length) return null;
  return (
    <div role="status" className="rounded-md border p-3 text-sm text-muted-foreground">
      <ul className="list-inside list-disc">
        {[...new Set(notices)].map((notice) => <li key={notice}>{notice}</li>)}
      </ul>
    </div>
  );
}
