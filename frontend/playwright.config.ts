import { defineConfig } from "@playwright/test";
import { join } from "node:path";
const outputDir = process.env.PLAYWRIGHT_OUTPUT_DIR ?? "test-results";
export default defineConfig({
 testDir: "./tests/browser",
 fullyParallel: true,
 workers: Number(process.env.PLAYWRIGHT_WORKERS ?? "2"),
 outputDir,
 retries: 0,
 timeout: 90000,
 expect: { timeout: 15000 },
 reporter: process.env.CI ? [["list"], ["junit", { outputFile: join(outputDir, "browser.xml") }]] : "list",
 use: {
  baseURL: process.env.BROWSER_BASE_URL ?? "http://localhost:3000",
  viewport: { width: 1280, height: 900 },
  trace: "off", // Login credentials/tokens must not be captured in traces.
  screenshot: "off",
  launchOptions: { args: process.env.BROWSER_HOST_MAP ? ["--host-resolver-rules=MAP localhost host.docker.internal", "--disable-features=AsyncDns"] : [] },
 },
});
