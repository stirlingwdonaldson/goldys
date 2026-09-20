import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Local development proxies API calls to the Spring Boot backend so the
  // browser's same-origin session cookies reach it. Production routes the
  // frontend and /api through one origin and does not use this rewrite.
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8080/api/:path*",
      },
    ];
  },
};

export default nextConfig;
