import { withSentryConfig } from "@sentry/nextjs/config";
import type { NextConfig } from "next";

const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // The diagnostics moved from /connectors to /data-health; keep old links working.
  async redirects() {
    return [{ source: "/connectors", destination: "/data-health", permanent: true }];
  },
  // Standalone output produces a self-contained `server.js` the Docker runtime
  // image runs under Node — no dev server, no Bun needed at runtime.
  output: "standalone",
  // This package is self-contained. Parent/worktree lockfiles must not widen server traces.
  outputFileTracingRoot: __dirname,
  // Proxy API calls and the OIDC login/callback flows to the Spring Boot
  // backend so the browser's same-origin session cookies reach it. Defaults to
  // local dev; set BACKEND_ORIGIN when frontend and backend aren't co-located
  // behind one origin, or drop these rewrites in a deployment that already
  // routes /api and /oauth2 at the edge.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendOrigin}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${backendOrigin}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${backendOrigin}/login/oauth2/:path*` },
    ];
  },
};

// Build-time Sentry: source map upload + release creation against the self-hosted instance.
// Everything keys off SENTRY_AUTH_TOKEN (a BuildKit secret in the Docker build): without it the
// build behaves exactly as before. The browser tunnel lives in app/monitoring/route.ts, because
// the `tunnelRoute` option here does not support self-hosted Sentry.
const sentryAuthToken = process.env.SENTRY_AUTH_TOKEN;

export default withSentryConfig(nextConfig, {
  sentryUrl: process.env.SENTRY_URL || undefined,
  org: process.env.SENTRY_ORG || undefined,
  project: process.env.SENTRY_PROJECT || undefined,
  authToken: sentryAuthToken,
  release: {
    name: process.env.SENTRY_RELEASE || undefined,
    create: Boolean(sentryAuthToken),
  },
  sourcemaps: { disable: !sentryAuthToken },
  widenClientFileUpload: true,
  // Keep build-plugin telemetry off: the point of self-hosting is that nothing goes to sentry.io.
  telemetry: false,
  silent: !process.env.CI,
});
