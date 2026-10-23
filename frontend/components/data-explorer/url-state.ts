export const EXPLORER_PAGE_SIZES = [10, 25, 50, 100] as const;

/** Invalid URL values have bounded defaults; the server still validates every request. */
export function explorerPage(params: URLSearchParams) {
  const rawPage = params.get("page");
  const page = rawPage !== null && /^\d+$/.test(rawPage) ? Number(rawPage) : 0;
  const size = Number(params.get("size"));
  return { page: Number.isSafeInteger(page) && page >= 0 && page <= 1_000_000 ? page : 0,
    pageSize: EXPLORER_PAGE_SIZES.some(value => value === size) ? size : 10 };
}

export function explorerUrl(current: URLSearchParams, patch: Record<string, string | null>) {
  const next = new URLSearchParams(current);
  for (const [key, value] of Object.entries(patch)) {
    if (value === null || value === "") next.delete(key); else next.set(key, value);
  }
  return next.toString();
}

export function validRawTime(value: string | null) {
  return value && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value)) ? value : "";
}

export function rawScope(params: URLSearchParams) {
  const method = params.get("method") ?? "";
  return { source: params.get("source") ?? "", fetcher: params.get("fetcher") ?? "",
    method: ["API", "FILE_EXPORT", "SCRAPE", "MANUAL"].includes(method) ? method : "",
    from: validRawTime(params.get("from")), to: validRawTime(params.get("to")) };
}

export function validRawPeriod(draft: { from: string; to: string }) {
  if (draft.from && !validRawTime(draft.from)) return false;
  if (draft.to && !validRawTime(draft.to)) return false;
  return !draft.from || !draft.to || Date.parse(draft.from) <= Date.parse(draft.to);
}
