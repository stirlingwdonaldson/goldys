// Node.js-runtime Sentry (server components, route handlers). Loaded from instrumentation.ts.
// Reads runtime env, so the same image can point at different Sentry projects per deployment.
import * as Sentry from "@sentry/nextjs";
import { dataCollection, beforeSend } from "./lib/sentry-options";

const dsn = process.env.SENTRY_DSN;

Sentry.init({
  dsn: dsn || undefined,
  enabled: Boolean(dsn),
  environment: process.env.SENTRY_ENVIRONMENT || "local",
  release: process.env.SENTRY_RELEASE || undefined,
  dataCollection,
  beforeSend,
  tracesSampleRate: 1.0,
  // The browser-event relay would otherwise produce one transaction per relayed event.
  ignoreTransactions: ["/monitoring"],
});
