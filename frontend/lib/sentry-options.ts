/**
 * What the Sentry SDK may collect, shared by the browser, server and edge configs.
 *
 * SDK v11 collects a lot by default (request/response bodies, cookies, headers, stack-frame local
 * variables, DB query data). This app shows staff names, wages and sales, and the backend's own
 * Sentry config already refuses bodies and PII, so the frontend matches it: keep what helps debug
 * (URLs and their query params, stack traces, timings) and drop everything that could carry
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
  // Query strings here are filters (dates, domains), not personal data.
  urlQueryParams: true,
  stackFrameVariables: false,
  databaseQueryData: false,
  genAI: { inputs: false, outputs: false },
};

/** Keep local causes intact while LinkedErrors reports no response snippets or field values. */
export const beforeSend: NonNullable<InitOptions["beforeSend"]> = (event, hint) => {
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
