export { LIST_PAGE_SIZES as EXPLORER_PAGE_SIZES, listPage as explorerPage, listUrl as explorerUrl } from "@/lib/list-url";

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
