"use client";

import { useIncompleteNotices } from "@/lib/incomplete-notices";

/** Result qualifications; incomplete cards follow the Settings display preference. */
export function DataQualifications({ notices }: { notices?: string[] | null }) {
  const { showIncomplete } = useIncompleteNotices();
  if (!notices?.length) return null;
  const incomplete = notices.some((notice) => /incomplete|unresolved|partial coverage|missing inputs/i.test(notice));
  if (incomplete && !showIncomplete) return null;
  return (
    <div role="status" className="rounded-md border p-3 text-sm text-muted-foreground">
      {incomplete ? <strong className="text-foreground">Incomplete — known subtotals only. </strong> : null}
      <ul className="list-inside list-disc">
        {[...new Set(notices)].map((notice) => <li key={notice}>{notice}</li>)}
      </ul>
    </div>
  );
}
