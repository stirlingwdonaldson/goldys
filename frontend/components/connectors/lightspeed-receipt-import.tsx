"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useApi } from "@/lib/demo-mode";
import { isApiError } from "@/lib/api";
import { invalidateBusinessReads } from "@/lib/query/invalidation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export function LightspeedReceiptImport() {
  const api = useApi();
  const client = useQueryClient();
  const [dataset, setDataset] = useState("transactions");
  const [message, setMessage] = useState<string | null>(null);
  const mutation = useMutation({ mutationFn: async (values: FormData) => {
    const file = values.get("file") as File;
    if (dataset === "financial") {
      await api.uploadConnectorCsv("lightspeed-sale-items", file);
      return "Financial CSV processed. Receipt mappings and source completeness remain separately qualified.";
    }
    const result = await api.uploadLightspeedReceiptCsv(dataset as "mapping" | "transactions", file, {
      siteNumber: Number(values.get("siteNumber")), siteName: String(values.get("siteName")),
      from: String(values.get("from")), to: String(values.get("to")),
    });
    return `Imported ${result.distinctObservations} distinct observations. Source coverage and transaction identity remain unverified.`;
  }});
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(null);
    try {
      const result = await mutation.mutateAsync(new FormData(event.currentTarget));
      setMessage(result);
      await invalidateBusinessReads(client);
    } catch { /* The mutation error is rendered below. */ }
  }
  return <section aria-labelledby="lightspeed-import-title" className="rounded-xl border bg-card p-5">
    <h2 id="lightspeed-import-title" className="font-semibold">Import Lightspeed CSV evidence</h2>
    <p className="mt-1 text-sm text-muted-foreground">Import merchandise, receipt mappings and Sales Feed transactions separately. Imports do not change Lightspeed reports or schedules. Receipt numbers identify groups; individual transaction associations remain unverified.</p>
    <form onSubmit={submit} className="mt-4 grid gap-3 sm:grid-cols-2">
      <label className="text-sm">Dataset
        <select name="dataset" value={dataset} onChange={e => { setDataset(e.target.value); setMessage(null); }} disabled={mutation.isPending} className="mt-1 block w-full rounded-md border bg-background p-2">
          <option value="transactions">Sales Feed transactions</option>
          <option value="mapping">Receipt-number mapping</option>
          <option value="financial">Merchandise financial lines</option>
        </select>
      </label>
      <label className="text-sm">CSV file<Input className="mt-1" name="file" type="file" accept=".csv,text/csv" required disabled={mutation.isPending} /></label>
      {dataset !== "financial" ? <>
        <label className="text-sm">Selected site ID<Input className="mt-1" name="siteNumber" type="number" min="1" step="1" required disabled={mutation.isPending} /></label>
        <label className="text-sm">Exact site name from export<Input className="mt-1" name="siteName" maxLength={255} required disabled={mutation.isPending} /></label>
        <label className="text-sm">Export start date<Input className="mt-1" name="from" type="date" required disabled={mutation.isPending} /></label>
        <label className="text-sm">Export end date<Input className="mt-1" name="to" type="date" required disabled={mutation.isPending} /></label>
      </> : null}
      <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? "Importing…" : "Import CSV"}</Button>
    </form>
    {message ? <p role="status" className="mt-3 text-sm">{message}</p> : null}
    {mutation.error ? <p role="alert" className="mt-3 text-sm text-destructive">{isApiError(mutation.error) && mutation.error.code === "NOT_PERMITTED" ? "You do not have permission to import connector data." : "Import failed. Check the CSV format, declared site and date range, and ingestion health before retrying."}</p> : null}
  </section>;
}
