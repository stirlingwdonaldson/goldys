"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { WorkspaceApi, WorkspaceFile } from "@/lib/api/workspace";
import { Button } from "@/components/ui/button";
import { InlineError } from "@/components/states/inline-error";

export function WorkspaceFiles({ api, jobId }: { api: WorkspaceApi; jobId: string }) {
  const client = useQueryClient();
  const key = ["workspace", "files", jobId];
  const files = useQuery({ queryKey: key, queryFn: ({ signal }) => api.listWorkspaceFiles(jobId, { signal }), staleTime: 0, refetchOnMount: "always", refetchInterval: 10000, retry: false });
  const action = useMutation({ mutationFn: (work: () => Promise<unknown>) => work(), retry: false,
    onSettled: () => client.invalidateQueries({ queryKey: key }) });
  const download = (file: WorkspaceFile) => action.mutate(async () => {
    const url = await api.workspaceFileDownloadUrl(jobId, file.id);
    const link = document.createElement("a"); link.href = url; link.download = file.name; link.rel = "noopener"; link.click();
  });
  return <section className="space-y-3 border-t pt-4" aria-label="Private investigation files">
    <h3 className="text-sm font-medium">Private files</h3>
    <p className="text-xs text-muted-foreground">Up to five files, 25 MB each and 100 MB total. Files expire after 30 days unless kept. Upload, then ask a follow-up to analyse the file. Original uploads are untrusted; extraction may be partial.</p>
    {files.error ? <InlineError>Files are unavailable or access has changed. Saved filenames are hidden.</InlineError> : !files.isFetchedAfterMount ? <p role="status">Checking file access…</p> : <>
      <label className="block text-sm">Upload a file<input className="mt-2 block w-full text-xs" type="file" accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx,.csv,.tsv,.txt" disabled={action.isPending} onChange={event => {
        const file = event.currentTarget.files?.[0]; event.currentTarget.value = "";
        if (file) action.mutate(() => api.uploadWorkspaceFile(jobId, file));
      }} /></label>
      {(files.data ?? []).map(file => <div key={file.id} className="space-y-1 rounded-md border p-3 text-sm">
        <p className="break-all font-medium">{file.name}</p>
        <p className="text-xs tabular-nums text-muted-foreground">{file.kind === "UPLOAD" ? "Original upload" : "Generated artifact"} · {Math.ceil(file.byteLength / 1024)} KB · {!file.available ? "Expired or deleted" : file.kept ? "Kept" : `Expires ${new Date(file.expiresAt).toLocaleDateString("en-AU")}`}</p>
        {file.available ? <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" disabled={action.isPending} onClick={() => download(file)}>Download</Button>
          <Button size="sm" variant="ghost" disabled={action.isPending} onClick={() => action.mutate(() => api.keepWorkspaceFile(jobId, file.id, !file.kept))}>{file.kept ? "Allow expiry" : "Keep"}</Button>
          <Button size="sm" variant="ghost" disabled={action.isPending} onClick={() => { if (window.confirm(`Delete ${file.name}? This removes its stored bytes.`)) action.mutate(() => api.deleteWorkspaceFile(jobId, file.id)); }}>Delete</Button>
        </div> : null}
      </div>)}
    </>}
    {action.error ? <InlineError>{action.error.message}</InlineError> : null}
  </section>;
}
