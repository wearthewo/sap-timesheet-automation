import { fixture } from "./fixtures";
import { test, expect, type Page } from "@playwright/test";
import { readFile } from "node:fs/promises";
const env = fixture("reports");
async function login(page: Page, kind: "FINANCE" | "ADMIN" | "EMPLOYEE" | "MANAGER") {
 const username = env[kind === "EMPLOYEE" ? "TEST_USERNAME" : `TEST_${kind}_USERNAME`];
 const password = env[kind === "EMPLOYEE" ? "TEST_PASSWORD" : `TEST_${kind}_PASSWORD`];
 if (!username || !password) throw new Error("Set local role fixture credentials.");
 await page.goto("/login"); await page.getByRole("button", { name: "Sign in with your work account" }).click();
 await page.locator("#username").fill(username); await page.locator("#password").fill(password); await page.locator("#kc-login").click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
}
async function logout(page: Page) {
 await page.getByRole("button", { name: "Log out", exact: true }).click();
 await page.getByRole("button", { name: /logout/i }).click(); await expect(page).toHaveURL(/\/login$/);
}
test("Finance monthly projections, URL filters, approved and SAP downloads, and mobile access", async ({ page }) => {
 test.setTimeout(180000);
 const prefix = env.TEST_REPORT_PREFIX;
 if (!prefix) throw new Error("Seed infra/dev/reporting-fixtures.sql and set TEST_REPORT_PREFIX.");
 await login(page, "FINANCE"); await page.getByRole("link", { name: "Reports", exact: true }).click();
 await expect(page.getByRole("heading", { name: "Monthly Finance reports" })).toBeVisible();
 await page.getByLabel("Year", { exact: true }).fill("2028"); await page.getByLabel("Month", { exact: true }).selectOption("10");
 await page.getByLabel("Employee", { exact: true }).fill(prefix); await page.getByRole("button", { name: "Apply filters" }).click();
 const employees = page.getByRole("region", { name: "Employee reports" });
 const summary = page.getByRole("region", { name: "Monthly summary" });
 await expect(employees.getByRole("row")).toHaveCount(8);
 await expect(employees).toContainText(prefix + "-F"); await expect(employees).toContainText("NO_TIMESHEET");
 await expect(summary.getByText("Active Employees", { exact: true }).locator("..")).toContainText("7");
 await expect(summary.getByText("Total Approved Hours", { exact: true }).locator("..")).toContainText("14.00");
 await expect(summary.getByText("Total SAP Exported Hours", { exact: true }).locator("..")).toContainText("8.25");
 await expect(page.getByRole("region", { name: "Project totals" })).toContainText("10.75 hours");
 await expect(page.getByRole("region", { name: "Employee project breakdown" })).toContainText(prefix + "-A");
 await page.screenshot({ path: test.info().outputPath("reports-desktop.png"), fullPage: true });
 for (const format of ["CSV", "Excel"]) {
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("link", { name: "Download " + format, exact: true }).click(); const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("workforce-approved-hours-2028-10." + (format === "CSV" ? "csv" : "xlsx"));
  const bytes = await readFile((await download.path())!);
  if (format === "CSV") {
   const csv = bytes.toString("utf8"); expect(csv).toContain(prefix + "-A"); expect(csv).toContain(prefix + "-B"); expect(csv).not.toContain(prefix + "-C");
   expect(csv).toContain('""billing, API""');
  } else {
   expect(bytes.subarray(0, 2).toString()).toBe("PK");
   await download.saveAs(test.info().outputPath("report-approved.xlsx")); // Workbook contents also opened and checked by backend POI tests.
  }
 }
 await page.getByLabel("Hours report mode").selectOption("SAP_EXPORTED"); await page.getByRole("button", { name: "Apply filters" }).click();
 await expect(page.getByRole("region", { name: "Project totals" })).toContainText("6.25 hours");
 const sapDownload = page.waitForEvent("download"); await page.getByRole("link", { name: "Download CSV", exact: true }).click();
 const sap = await sapDownload; expect(sap.suggestedFilename()).toBe("workforce-sap-exported-hours-2028-10.csv");
 const sapCsv = await readFile((await sap.path())!, "utf8"); expect(sapCsv).toContain(prefix + "-A"); expect(sapCsv).not.toContain(prefix + "-B"); expect(sapCsv).not.toContain(prefix + "-G");
 await page.route("**/api/reports/csv?**", route => route.fulfill({ status: 413, contentType: "application/json", body: JSON.stringify({ message: "Narrow the report filters before downloading." }) }));
 await page.getByRole("link", { name: "Download CSV", exact: true }).click();
 await expect(page.getByRole("region", { name: "Report downloads" }).getByRole("alert")).toHaveText("Narrow the report filters before downloading.");
 await expect(page.getByRole("heading", { name: "Monthly Finance reports" })).toBeVisible();
 await page.unroute("**/api/reports/csv?**");
 await page.getByLabel("Hours report mode").selectOption("APPROVED");
 await page.getByLabel("Employee", { exact: true }).fill(prefix + "-B"); await page.getByRole("button", { name: "Apply filters" }).click();
 await expect(employees.getByRole("row")).toHaveCount(2); await expect(employees).toContainText(prefix + "-B");
 await page.getByLabel("Employee", { exact: true }).fill(prefix); await page.getByLabel("Project", { exact: true }).fill("REPORT-B");
 await page.getByRole("button", { name: "Apply filters" }).click(); await expect(employees.getByRole("row")).toHaveCount(3);
 await page.getByLabel("Project", { exact: true }).fill(""); await page.getByLabel("Timesheet state").selectOption("NO_TIMESHEET");
 await page.getByRole("button", { name: "Apply filters" }).click(); await expect(employees.getByRole("row")).toHaveCount(2); await expect(employees).toContainText(prefix + "-F");
 await page.getByLabel("Timesheet state").selectOption(""); await page.getByLabel("SAP state").selectOption("FAILED");
 await page.getByRole("button", { name: "Apply filters" }).click(); await expect(employees.getByRole("row")).toHaveCount(2); await expect(employees).toContainText(prefix + "-G");
 await expect(page).toHaveURL(/sapStatus=FAILED/);
 await page.getByLabel("SAP state").selectOption(""); await page.getByLabel("Month", { exact: true }).selectOption("9");
 await page.getByRole("button", { name: "Apply filters" }).click(); await expect(summary.getByText("Missing Timesheets", { exact: true }).locator("..")).toContainText("7");
 await logout(page);
 await page.setViewportSize({ width: 375, height: 812 }); await login(page, "ADMIN");
 await expect(page.getByRole("link", { name: "Reports", exact: true })).toBeVisible();
 await page.goto(`/app/reports/monthly?year=2028&month=10&employee=${prefix}`);
 await expect(employees.getByRole("row")).toHaveCount(8);
 expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
 await page.screenshot({ path: test.info().outputPath("reports-mobile.png"), fullPage: true }); await logout(page);
});
test("employee and manager cannot access reporting or file downloads", async ({ page }) => {
 for (const kind of ["EMPLOYEE", "MANAGER"] as const) {
  await login(page, kind); await expect(page.getByRole("link", { name: "Reports", exact: true })).toHaveCount(0);
  await page.goto("/app/reports/monthly"); await expect(page.getByRole("main").getByRole("alert")).toHaveText("You do not have access to Finance reports.");
  for (const format of ["csv", "xlsx"]) {
   const status = await page.evaluate(async (path) => (await fetch(path)).status, `/api/reports/${format}?year=2028&month=10`);
   expect(status).toBe(403);
  }
  await logout(page);
 }
});
