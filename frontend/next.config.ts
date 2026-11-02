import type { NextConfig } from "next";
const config: NextConfig = {
 output: "standalone",
 poweredByHeader: false,
 experimental: { serverActions: { bodySizeLimit: "64kb" } },
 async headers() {
  return [{ source: "/(.*)", headers: [
   { key: "X-Content-Type-Options", value: "nosniff" },
   { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
   { key: "X-Frame-Options", value: "DENY" },
   { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
   { key: "Content-Security-Policy", value: "base-uri 'self'; object-src 'none'; frame-ancestors 'none'" },
  ] }];
 },
};
export default config;
