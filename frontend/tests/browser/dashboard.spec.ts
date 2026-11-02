import { test, expect, type Page } from "@playwright/test";
import { fixture } from "./fixtures";
import { businessDate, calendarDate, eventTime } from "../../src/lib/display";
// Read-only dashboard checks share this namespace without mutating its business fixtures.
const env = fixture("organizations");
async function login(page: Page, role: string) {
 const prefix = role === "EMPLOYEE" ? "TEST" : `TEST_${role}`;
 await page.goto("/login"); await page.getByRole("button", { name: "Sign in with your work account" }).click();
 await page.locator("#username").fill(env[`${prefix}_USERNAME`]); await page.locator("#password").fill(env[`${prefix}_PASSWORD`]); await page.locator("#kc-login").click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
}
test("each role receives its own dashboard with accessible mobile navigation", async ({ page }) => {
 test.setTimeout(180000);
 for (const role of ["EMPLOYEE", "MANAGER", "FINANCE", "ADMIN"]) {
  await login(page, role);
  const skip = page.getByRole("link", { name: "Skip to content", exact: true });
  await skip.focus(); await expect(skip).toBeFocused(); await skip.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  await expect(page.getByRole("region", { name: "Organization overview", exact: true })).toHaveCount(role === "ADMIN" ? 1 : 0);
  await expect(page.getByRole("region", { name: "Finance overview", exact: true })).toHaveCount(["ADMIN", "FINANCE"].includes(role) ? 1 : 0);
  await expect(page.getByRole("region", { name: "Manager overview", exact: true })).toHaveCount(role === "MANAGER" ? 1 : 0);
  if (role === "ADMIN") { await expect(page.getByRole("region", { name: "Organization overview" })).toContainText("Active employees"); await expect(page.getByRole("region", { name: "Organization setup" })).toBeVisible(); }
  if (role === "FINANCE") { await expect(page.getByRole("region", { name: "Finance overview" })).toContainText("Awaiting export"); await expect(page.getByRole("region", { name: "Finance overview" })).toContainText("Mock SAP"); }
  if (role === "EMPLOYEE") await expect(page.getByRole("region", { name: "Your time overview" })).toContainText("Current month");
  await expect(page.getByRole("navigation", { name: "Main navigation" }).getByRole("link", { name: "Overview", exact: true })).toHaveAttribute("aria-current", "page");
  await page.screenshot({ path: test.info().outputPath(`dashboard-${role.toLowerCase()}-desktop.png`), fullPage: true });
  await page.setViewportSize({ width: 375, height: 812 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.getByRole("button", { name: "Log out", exact: true })).toBeVisible();
  await page.screenshot({ path: test.info().outputPath(`dashboard-${role.toLowerCase()}-mobile.png`), fullPage: true });
  await page.getByRole("button", { name: "Log out", exact: true }).click(); await page.getByRole("button", { name: /logout/i }).click(); await expect(page).toHaveURL(/\/login$/);
  await page.setViewportSize({ width: 1280, height: 900 });
 }
});
test("presentation separates calendar dates from Athens event instants", () => {
 for (const [instant, day] of [["2026-01-31T22:30:00Z","2026-02-01"],["2026-02-28T22:30:00Z","2026-03-01"],["2026-04-30T21:30:00Z","2026-05-01"],["2026-03-28T22:30:00Z","2026-03-29"],["2026-03-29T21:30:00Z","2026-03-30"],["2026-10-24T21:30:00Z","2026-10-25"],["2026-10-25T22:30:00Z","2026-10-26"]]) expect(businessDate(new Date(instant))).toBe(day);
 expect(businessDate(new Date("2028-02-29T22:30:00Z"))).toBe("2028-03-01");
 expect(calendarDate("2028-02-29")).toBe("29 Feb 2028");
 expect(eventTime("2026-03-29T00:30:00Z")).toContain("02:30");
 expect(eventTime("2026-03-29T01:30:00Z")).toContain("04:30");
 expect(eventTime("2026-10-25T00:30:00Z")).toContain("03:30");
 expect(eventTime("2026-10-25T01:30:00Z")).toContain("03:30");
});
