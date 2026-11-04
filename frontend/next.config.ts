import type { NextConfig } from "next";

// Server-side only. NOTE: rewrite destinations are resolved at build time in
// the standalone build, so this value is baked into the production image.
// Production traffic does not rely on it: the reverse proxy (Caddy) routes
// /api/* to the backend. The rewrites make local development single-origin.
const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      // Actuator is not exposed through the production proxy; dev only.
      ...(process.env.NODE_ENV !== "production"
        ? [
            {
              source: "/actuator/health",
              destination: `${backend}/actuator/health`,
            },
          ]
        : []),
    ];
  },
};

export default nextConfig;
