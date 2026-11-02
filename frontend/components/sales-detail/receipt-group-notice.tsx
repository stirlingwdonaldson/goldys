"use client";
import { useQuery } from "@tanstack/react-query";
import type { Api } from "@/lib/api";
const ASSOCIATIONS: Record<string, string> = {
  AMBIGUOUS_TRANSACTION: "Several source transactions share this receipt number. Items have not been assigned to an individual sale or refund.",
  MAPPING_CONFLICT: "Conflicting receipt-number mappings need review. These items must not be treated as one transaction.",
  TRANSACTION_CONFLICT: "Conflicting transaction observations need review.",
  TRANSACTION_LIMIT_EXCEEDED: "The transaction evidence exceeds the display limit.",
  TRANSACTION_IDENTITY_UNVERIFIED: "A receipt number groups observations; its relationship to individual transactions remains unverified.",
};
export function ReceiptGroupNotice({ api, saleNumber }: { api: Api; saleNumber: string }) {
  const query = useQuery({ queryKey: ["receipt-group", saleNumber], queryFn: ({ signal }) => api.getReceiptGroup(saleNumber, "2020-11-23", new Date().toISOString().slice(0, 10), { signal }) });
  return <section aria-label="Receipt association" className="rounded-xl border bg-card p-4 text-sm">
    <h3 className="font-semibold">Receipt-number group</h3>
    {query.isPending ? <p role="status">Checking receipt associations…</p> : query.error ? <p role="alert">{query.error.code === "NOT_PERMITTED" ? "You do not have permission to view transaction evidence." : "Receipt associations could not be checked. Transaction identity remains unverified."}</p> : <>
      <p>{ASSOCIATIONS[query.data.association] ?? "Receipt associations need review."}</p>
      <p className="mt-1 text-muted-foreground">Source coverage is unverified. Amounts elsewhere in this view describe the observed group, not a verified individual transaction.</p>
      {query.data.transactions.length ? <ul className="mt-2">{query.data.transactions.map((t, i) => <li key={`${t.siteNumber}:${t.transactionId}:${i}`}>Site {t.siteNumber} · transaction {t.transactionId}{t.linkedTransactionId ? ` · linked to ${t.linkedTransactionId}` : ""}</li>)}</ul> : null}
    </>}
  </section>;
}
