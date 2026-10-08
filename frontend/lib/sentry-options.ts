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
