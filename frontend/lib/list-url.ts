export const LIST_PAGE_SIZES = [10, 25, 50, 100] as const;

/** Invalid URL values have bounded defaults; the server still validates every request. */
export function listPage(params: URLSearchParams) {
  const rawPage = params.get("page");
  const page = rawPage !== null && /^\d+$/.test(rawPage) ? Number(rawPage) : 0;
  const size = Number(params.get("size"));
  return { page: Number.isSafeInteger(page) && page >= 0 && page <= 1_000_000 ? page : 0,
    pageSize: LIST_PAGE_SIZES.some(value => value === size) ? size : 10 };
}

export function listUrl(current: URLSearchParams, patch: Record<string, string | null>) {
  const next = new URLSearchParams(current);
  for (const [key, value] of Object.entries(patch)) {
    if (value === null || value === "") next.delete(key); else next.set(key, value);
  }
  return next.toString();
}
