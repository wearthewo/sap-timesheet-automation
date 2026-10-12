import { test, expect } from "@playwright/test";

test("one identity selects organization-specific roles and logs out on mobile", async ({ page, context }) => {
 const username = process.env.TEST_TENANT_USERNAME, password = process.env.TEST_TENANT_PASSWORD;
 const alpha = process.env.TEST_ORGANIZATION_A, beta = process.env.TEST_ORGANIZATION_B;
 if (!username || !password || !alpha || !beta) throw new Error("Set the two-organization CI fixture variables");
 await page.goto("/login");
 await page.getByRole("button", { name: "Sign in with your work account" }).click();
 await page.locator("#username").fill(username); await page.locator("#password").fill(password); await page.locator("#kc-login").click();
 await expect(page.getByRole("heading", { name: "Choose your organization" })).toBeVisible();
 await page.getByLabel("Organization", { exact: true }).selectOption(alpha);
 await page.getByRole("button", { name: "Continue", exact: true }).click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
 await expect(page.getByRole("link", { name: "SAP exports", exact: true })).toHaveCount(0);
 await expect(page.getByText("EMPLOYEE · CI Alpha", { exact: true })).toBeVisible();
 await page.getByRole("link", { name: "Organization", exact: true }).click();
 await page.setViewportSize({ width: 375, height: 812 });
 await page.getByLabel("Organization", { exact: true }).selectOption(beta);
 await page.getByRole("button", { name: "Continue", exact: true }).click();
 await expect(page.getByText("FINANCE · CI Beta", { exact: true })).toBeVisible();
 await page.getByRole("link", { name: "SAP exports", exact: true }).click();
 await expect(page.getByText("No approved timesheets are available.")).toBeVisible();
 await expect(page.locator("body")).not.toContainText("QA3-E-");
 await page.getByRole("button", { name: "Log out", exact: true }).click();
 await page.getByRole("button", { name: /logout/i }).click();
 await expect(page).toHaveURL(/\/login$/);
 expect((await context.cookies()).some(c => c.name === "workforce-organization")).toBe(false);
});
