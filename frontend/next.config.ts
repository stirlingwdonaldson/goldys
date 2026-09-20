import type { NextConfig } from "next";

const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  reactStrictMode: true,
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

export default nextConfig;
