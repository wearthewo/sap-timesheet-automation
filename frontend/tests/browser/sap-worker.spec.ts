import { test, expect, type Page } from "@playwright/test";
async function login(page: Page, kind: "FINANCE" | "ADMIN") {
 const username = process.env[`TEST_${kind}_USERNAME`]; const password = process.env[`TEST_${kind}_PASSWORD`];
 if (!username || !password) throw new Error("Set local FINANCE/ADMIN fixture credentials.");
 await page.goto("/login"); await page.getByRole("button", { name: "Sign in with your work account" }).click();
 await page.locator("#username").fill(username); await page.locator("#password").fill(password); await page.locator("#kc-login").click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
}
async function logout(page: Page) {
 await page.getByRole("button", { name: "Log out", exact: true }).click(); await page.getByRole("button", { name: /logout/i }).click();
 await expect(page).toHaveURL(/\/login$/);
}
test("durable worker reconciles transient failures, retries, and recovers timeout-after-create", async ({ page }) => {
 test.setTimeout(240000);
 const transient = process.env.TEST_SAP_TRANSIENT_ID; const unknown = process.env.TEST_SAP_UNKNOWN_ID;
 if (!transient || !unknown) throw new Error("Prepare fresh approved MOCK-TRANSIENT-503 and MOCK-TIMEOUT fixtures.");
 await login(page, "FINANCE");
 await page.goto(`/app/sap/${transient}`);
 const panel = page.getByRole("region", { name: "SAP export", exact: true });
 await panel.getByRole("button", { name: "Export to SAP", exact: true }).click();
 await expect(panel.getByRole("status")).toHaveText("QUEUED");
 await expect(panel.getByTestId("next-sap-attempt")).toBeVisible();
 await expect(panel.getByRole("status")).toHaveText("UNKNOWN", { timeout: 30000 });
 await expect(panel.getByRole("alert")).toContainText("SAP is unavailable");
 await expect(panel.getByTestId("export-attempt-count")).toHaveText("1");
 await expect(panel.getByTestId("next-sap-attempt")).toContainText("Next reconciliation");
 await expect(panel.getByRole("status")).toHaveText("SUCCEEDED", { timeout: 120000 });
 await expect(panel.getByTestId("export-attempt-count")).toHaveText("3");
 await expect(page.getByRole("region", { name: "Export history" })).toContainText("NOT_FOUND");
 await expect(panel.getByRole("button", { name: "Retry export", exact: true })).toHaveCount(0);
 await page.goto(`/app/sap/${unknown}`);
 await panel.getByRole("button", { name: "Export to SAP", exact: true }).click();
 await expect(panel.getByRole("status")).toHaveText("UNKNOWN", { timeout: 30000 });
 await expect(panel.getByRole("button", { name: "Retry export", exact: true })).toHaveCount(0);
 await panel.getByRole("button", { name: "Reconcile export", exact: true }).click();
 await expect(panel.getByRole("status")).toHaveText("SUCCEEDED", { timeout: 30000 });
 await expect(panel.getByTestId("export-attempt-count")).toHaveText("1");
 await expect(panel).toContainText(/SAP-TS-[A-F0-9]+/);
 await page.screenshot({ path: "test-results/sap-reconciled-desktop.png", fullPage: true });
 await logout(page);
});
test("admin manually requeues exhausted transient export and preserves history on mobile", async ({ page }) => {
 test.setTimeout(240000);
 const id = process.env.TEST_SAP_RETRY_ID; if (!id) throw new Error("Prepare a fresh approved MOCK-RETRY fixture with max attempts 4.");
 await page.setViewportSize({ width: 375, height: 812 });
 await login(page, "ADMIN"); await page.goto(`/app/sap/${id}`);
 const panel = page.getByRole("region", { name: "SAP export", exact: true });
 await panel.getByRole("button", { name: "Export to SAP", exact: true }).click();
 await expect(panel.getByRole("status")).toHaveText("FAILED", { timeout: 160000 });
 await expect(panel.getByTestId("export-attempt-count")).toHaveText("4");
 await expect(panel.getByRole("alert")).toContainText("Retry limit reached");
 await page.screenshot({ path: "test-results/sap-retry-mobile.png", fullPage: true });
 await panel.getByRole("button", { name: "Retry export", exact: true }).click();
 await expect(panel.getByRole("status")).toHaveText("SUCCEEDED", { timeout: 30000 });
 await expect(panel.getByTestId("export-attempt-count")).toHaveText("5");
 await expect(page.getByRole("region", { name: "Export history" }).getByRole("listitem")).toHaveCount(9);
 expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
 await expect(page.getByRole("button", { name: "Log out", exact: true })).toBeVisible();
 await logout(page);
});
