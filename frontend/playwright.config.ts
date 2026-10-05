import { defineConfig } from "@playwright/test";
export default defineConfig({
 testDir: "./tests/browser",
 fullyParallel: false,
 workers: 1,
 retries: 0,
 timeout: 90000,
 expect: { timeout: 15000 },
 reporter: "list",
 use: {
  baseURL: process.env.BROWSER_BASE_URL ?? "http://localhost:3000",
  viewport: { width: 1280, height: 900 },
  trace: "off", // Login credentials/tokens must not be captured in traces.
  screenshot: "off",
  launchOptions: { args: process.env.BROWSER_HOST_MAP ? ["--host-resolver-rules=MAP localhost host.docker.internal", "--disable-features=AsyncDns"] : [] },
 },
});
