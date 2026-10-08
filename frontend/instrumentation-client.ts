// Browser-side Sentry. Inert unless NEXT_PUBLIC_SENTRY_DSN was set at build time.
import * as Sentry from "@sentry/nextjs";
import { dataCollection } from "./lib/sentry-options";

const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

Sentry.init({
  dsn: dsn || undefined,
  enabled: Boolean(dsn),
  environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT || "local",
  release: process.env.NEXT_PUBLIC_SENTRY_RELEASE || undefined,
  // Events go to our own origin and are relayed server-side (app/monitoring/route.ts). The
  // self-hosted Sentry is plain HTTP, which an HTTPS page may not call directly (mixed content),
  // and a same-origin path also dodges ad blockers. Next's built-in tunnelRoute option does not
  // support self-hosted Sentry, hence the hand-written relay.
  tunnel: "/monitoring",
  // No bodies, cookies, user details or local variables: this app shows staff and wage data.
  dataCollection,
  // Low traffic: trace every page load and navigation. Relative /api calls get trace headers by
  // default, so a slow dashboard links straight through to the Spring Boot transaction behind it.
  tracesSampleRate: 1.0,
  integrations:
    // Optional "Report a problem" button; off until NEXT_PUBLIC_SENTRY_FEEDBACK=true so it is a
    // deliberate UI change, not a side effect of turning monitoring on.
    process.env.NEXT_PUBLIC_SENTRY_FEEDBACK === "true"
      ? [Sentry.feedbackIntegration({ colorScheme: "system", showBranding: false })]
      : [],
});

// Ties App Router navigations to traces.
export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
