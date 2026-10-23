/**
 * What the Sentry SDK may collect, shared by the browser, server and edge configs.
 *
 * SDK v11 collects a lot by default (request/response bodies, cookies, headers, stack-frame local
 * variables, DB query data). This app shows staff names, wages and sales, and the backend's own
 * Sentry config already refuses bodies and PII, so the frontend matches it: keep what helps debug
 * (sanitised URLs, stack traces, timings) and drop everything that could carry
 * personal or financial data.
 */
import type * as Sentry from "@sentry/nextjs";
import { isApiError } from "./api/errors";

type InitOptions = NonNullable<Parameters<typeof Sentry.init>[0]>;

export const dataCollection: InitOptions["dataCollection"] = {
  userInfo: false,
  cookies: false,
  // Header *names* aren't the risk; cookie, auth and CSRF values are.
  httpHeaders: { request: { deny: ["cookie", "authorization", "x-xsrf-token"] }, response: false },
  httpBodies: [],
  // Free-text filters may contain personal information.
  urlQueryParams: false,
  stackFrameVariables: false,
  databaseQueryData: false,
  genAI: { inputs: false, outputs: false },
};

/** Keep bounded navigation metadata; free-text filters and unknown query fields are private. */
export function scrubTokenQuery(value: string | undefined): string | undefined {
  if (!value) return value;
  const queryStart = value.indexOf("?");
  if (queryStart < 0) return value;
  const base = value.slice(0, queryStart);
  const query = new URLSearchParams(value.slice(queryStart + 1).split("#")[0]);
  const safe = new URLSearchParams();
  for (const [key, entry] of query) {
    if ((["from", "to"].includes(key) && /^\d{4}-\d{2}-\d{2}(T[0-9:.+Z-]+)?$/.test(entry)) ||
        (["page", "size"].includes(key) && /^\d{1,7}$/.test(entry)) ||
        (["layer", "domain"].includes(key) && /^[a-z_]{1,40}$/.test(entry))) safe.append(key, entry);
  }
  return safe.size ? `${base}?${safe}` : base;
}

/** Scrub the {@code token} query parameter from an event's request URL and query string. */
function scrubRequest(event: { request?: { url?: string; queryString?: string; query_string?: unknown } }) {
  const request = event.request;
  if (!request) return;
  delete request.query_string;
  if (typeof request.url === "string") request.url = scrubTokenQuery(request.url);
  if (typeof request.queryString === "string") {
    request.queryString = scrubTokenQuery(request.queryString);
  }
}

/** Keep local causes intact while LinkedErrors reports no response snippets or field values. */
export const beforeSend: NonNullable<InitOptions["beforeSend"]> = (event, hint) => {
  scrubRequest(event);
  const error = hint.originalException;
  if (!isApiError(error)) return event;
  const tags = { ...event.tags, api_code: /^[A-Z][A-Z0-9_]{0,63}$/.test(error.code) ? error.code : "UNRECOGNIZED" };
  if (error.status !== undefined && Number.isInteger(error.status) && error.status >= 100 && error.status <= 599) {
    Object.assign(tags, { http_status: String(error.status) });
  }
  if (error.correlationId && /^[A-Za-z0-9._-]{1,64}$/.test(error.correlationId)) {
    Object.assign(tags, { correlation_id: error.correlationId });
  }
  return {
    ...event,
    tags,
    exception: event.exception ? {
      ...event.exception,
      values: event.exception.values?.map(exception => ({
        ...exception,
        value: exception.type === "ApiError" ? error.message : "API failure cause details withheld.",
      })),
    } : undefined,
  };
};

/**
 * Scrub the {@code token} query parameter from transaction events and their spans. The URL (with
 * query string) is recorded on the transaction's request context and on HTTP span attributes
 * ({@code url.full} / {@code url}), both of which would otherwise carry the webhook secret.
 *
 * @deprecated only in the sense that {@code beforeSendTransaction} is removed in SDK v12; when this
 * app upgrades, move this scrubbing into {@code beforeSend} (which will then receive transactions).
 */
export const beforeSendTransaction: NonNullable<InitOptions["beforeSendTransaction"]> = (event) => {
  scrubRequest(event);
  for (const span of event.spans ?? []) {
    if (typeof span.description === "string") {
      span.description = scrubTokenQuery(span.description);
    }
    if (span.data) {
      for (const [key, value] of Object.entries(span.data)) {
        if (typeof value === "string") span.data[key] = scrubTokenQuery(value);
      }
    }
  }
  return event;
};

/** Scrub the {@code token} query parameter from breadcrumb data (HTTP crumbs store {@code url}). */
export const beforeBreadcrumb: NonNullable<InitOptions["beforeBreadcrumb"]> = (breadcrumb) => {
  const data = breadcrumb.data;
  if (data && typeof data.url === "string") {
    data.url = scrubTokenQuery(data.url);
  }
  return breadcrumb;
};
