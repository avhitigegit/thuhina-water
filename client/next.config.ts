import type { NextConfig } from "next";

/*
 * The browser always calls the API on the same site at /api (design 7.1), so the login cookie
 * (SameSite=Strict) works. In Docker, Nginx sends /api to the API. For `npm run dev` without Nginx,
 * Next.js forwards /api to the API on this computer (API_PROXY_URL, default http://localhost:8080).
 */
const apiProxyUrl = process.env.API_PROXY_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiProxyUrl}/api/:path*` }];
  },
};

export default nextConfig;
