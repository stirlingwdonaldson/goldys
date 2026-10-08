// Edge-runtime Sentry. Nothing runs on the edge runtime today; kept so a future middleware is
// covered without remembering to add this. Loaded from instrumentation.ts.
import * as Sentry from "@sentry/nextjs";
import { dataCollection } from "./lib/sentry-options";

const dsn = process.env.SENTRY_DSN;

Sentry.init({
  dsn: dsn || undefined,
  enabled: Boolean(dsn),
  environment: process.env.SENTRY_ENVIRONMENT || "local",
  release: process.env.SENTRY_RELEASE || undefined,
  dataCollection,
  tracesSampleRate: 1.0,
});
