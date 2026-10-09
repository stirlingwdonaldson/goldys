import { isApiError } from "@/lib/api";

/** Shared auth feedback keeps unrecognized exceptions out of user-facing copy. */
export function AuthFeedback({ error, message }: { error: unknown; message?: string }) {
  if (error == null) return null;
  const apiError = isApiError(error) ? error : null;
  return (
    <div className="space-y-1">
      <p role="alert" className="text-sm text-destructive">{message ?? apiError?.message ?? "Something went wrong. Try again."}</p>
      {apiError?.correlationId ? <p className="break-words text-xs text-muted-foreground">Reference: {apiError.correlationId}</p> : null}
    </div>
  );
}
