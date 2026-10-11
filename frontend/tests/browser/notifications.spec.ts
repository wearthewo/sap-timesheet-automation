import { test, expect, type Page } from "@playwright/test";
type Role = "EMPLOYEE" | "MANAGER" | "FINANCE" | "ADMIN";
const username = (role: Role) => process.env[role === "EMPLOYEE" ? "TEST_USERNAME" : `TEST_${role}_USERNAME`]!;
async function login(page: Page, role: Role) {
 const password = process.env[role === "EMPLOYEE" ? "TEST_PASSWORD" : `TEST_${role}_PASSWORD`];
 if (!username(role) || !password) throw new Error("Prepare Phase 7 local role fixtures.");
 await page.goto("/login"); await page.getByRole("button", { name: "Sign in with your work account" }).click();
 await page.locator("#username").fill(username(role)); await page.locator("#password").fill(password); await page.locator("#kc-login").click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
}
async function logout(page: Page) {
 await page.getByRole("button", { name: "Log out", exact: true }).click(); await page.getByRole("button", { name: /logout/i }).click();
 await expect(page).toHaveURL(/\/login$/);
}
const api = process.env.MAILPIT_API_URL ?? (process.env.BROWSER_HOST_MAP ? "http://host.docker.internal:8025" : "http://localhost:8025");
async function messages(role: Role, subject: string) {
 const query = `to:${username(role)}@example.test subject:"${subject}"`;
 const response = await fetch(api + "/api/v1/search?query=" + encodeURIComponent(query));
 if (!response.ok) throw new Error("Local Mailpit search unavailable");
 return (await response.json()).messages as { ID: string; Subject: string; To: { Address: string }[] }[];
}
async function delivered(role: Role, subject: string) {
 await expect.poll(async () => (await messages(role, subject)).length, { timeout: 45000, intervals: [500, 1000, 2000] }).toBe(1);
 const item = (await messages(role, subject))[0];
 expect(item.To.map(to => to.Address)).toContain(username(role) + "@example.test");
 const response = await fetch(api + "/api/v1/message/" + item.ID);
 const message = await response.json() as { Text: string; HTML: string };
 // SMTP uses CRLF; normalize transport line endings without changing the comment text.
 return { ...message, Text: message.Text.replace(/\r\n/g, "\n") };
}
test("Mailpit captures reminders, rejection and approval; history is private and mobile navigation works", async ({ page, context }) => {
 test.setTimeout(240000);
 await login(page, "EMPLOYEE");
 const reminder = await delivered("EMPLOYEE", "Timesheet reminder");
 const reminderMonth = process.env.TEST_REMINDER_MONTH ?? "2026-10";
 const [reminderYear, reminderNumber] = reminderMonth.split("-");
 expect(reminder.Text).toContain(`/app/timesheets/${reminderYear}/${Number(reminderNumber)}`);
 expect(reminder.Text).toContain(process.env.TEST_EMPLOYEE_NAME ?? "Phase Three Employee");
 await page.goto("/app/timesheets/2026/12"); await page.getByRole("button", { name: "Create draft", exact: true }).click();
 await page.getByLabel("Work date").fill("2026-12-05");
 const select = page.getByRole("combobox", { name: "Project", exact: true });
 const option = select.locator("option").filter({ hasText: process.env.TEST_PROJECT_ONE! });
 await select.selectOption((await option.getAttribute("value"))!);
 await page.getByLabel("Hours", { exact: true }).fill("2.50"); await page.getByRole("button", { name: "Save entry", exact: true }).click();
 await page.getByRole("button", { name: "Submit month", exact: true }).click(); await page.getByRole("button", { name: "Confirm submission" }).click();
 await expect(page.getByText("PENDING_APPROVAL", { exact: true })).toBeVisible();
 const digest = await delivered("MANAGER", "waiting for approval — 2026-12");
 expect(digest.Text).toContain("/app/approvals");
 expect(digest.Text).toContain(process.env.TEST_MANAGER_NAME ?? "Phase Three Manager");
 expect(digest.Text).toContain(process.env.TEST_EMPLOYEE_NAME ?? "Phase Three Employee");
 expect(digest.Text).toContain("QA3-E-");
 await logout(page); await login(page, "MANAGER"); await page.getByRole("link", { name: "Approvals", exact: true }).click();
 await page.getByRole("region", { name: "Pending timesheets" }).getByRole("link").filter({ hasText: "2.50 hours" }).click();
 await expect(page.getByRole("region", { name: "Review entries" })).toContainText("2.50");
 const reviewUrl = page.url();
 await page.getByRole("button", { name: "Reject timesheet", exact: true }).click();
 const comment = "Please correct Δοκιμή & \"quoted\"\n<script>alert('unsafe')</script>";
 await page.getByLabel("Rejection comment (required)").fill(comment); await page.getByRole("button", { name: "Confirm rejection" }).click();
 await expect(page.getByText("REJECTED", { exact: true })).toBeVisible();
 const rejection = await delivered("EMPLOYEE", "Timesheet rejected — 2026-12");
 expect(rejection.Text).toContain(process.env.TEST_EMPLOYEE_NAME ?? "Phase Three Employee");
 expect(rejection.Text).toContain(process.env.TEST_MANAGER_NAME ?? "Phase Three Manager");
 expect(rejection.Text).toContain("Europe/Athens");
 expect(rejection.Text).toContain("reopen your timesheet");
 expect(rejection.Text).toContain(comment); expect(rejection.Text).toContain("/app/timesheets/2026/12"); expect(rejection.HTML ?? "").not.toContain("<script>");
 const mailbox = await context.newPage(); await mailbox.goto(api);
 await expect(mailbox.getByText("Timesheet rejected — 2026-12", { exact: true }).first()).toBeVisible();
 await mailbox.getByText("Timesheet rejected — 2026-12", { exact: true }).first().click();
 await expect(mailbox.locator("body")).toContainText("Timesheet rejected");
 await mailbox.screenshot({ path: "test-results/mailpit-rejection.png", fullPage: true }); await mailbox.close();
 await logout(page); await login(page, "EMPLOYEE"); await page.setViewportSize({ width: 375, height: 812 });
 await page.getByRole("link", { name: "Notifications", exact: true }).click();
 const card = page.getByRole("article").filter({ hasText: "Timesheet rejected — 2026-12" });
 await expect(card).toContainText("SENT"); await card.getByText("Message", { exact: true }).click(); await expect(card).toContainText(comment);
 expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
 await page.screenshot({ path: "test-results/notifications-mobile.png", fullPage: true });
 await page.goto("/app/timesheets/2026/12"); await page.getByRole("button", { name: "Reopen draft", exact: true }).click();
 await page.getByRole("button", { name: /^Edit / }).click(); await page.getByLabel("Hours", { exact: true }).fill("3.25"); await page.getByRole("button", { name: "Save changes" }).click();
 await page.getByRole("button", { name: "Submit month", exact: true }).click(); await page.getByRole("button", { name: "Confirm submission" }).click();
 await logout(page); await login(page, "MANAGER"); await page.goto(reviewUrl);
 await page.getByRole("button", { name: "Approve timesheet", exact: true }).click(); await page.getByRole("button", { name: "Confirm approval" }).click();
 await expect(page.getByText("APPROVED", { exact: true })).toBeVisible();
 const approved = await delivered("EMPLOYEE", "Timesheet approved — 2026-12"); expect(approved.Text).toContain("read-only");
 expect(approved.Text).toContain(process.env.TEST_EMPLOYEE_NAME ?? "Phase Three Employee");
 expect(approved.Text).toContain(process.env.TEST_MANAGER_NAME ?? "Phase Three Manager");
 expect(approved.Text).toContain("Europe/Athens");
 await page.getByRole("link", { name: "Notifications", exact: true }).click();
 await expect(page.getByRole("article").filter({ hasText: "Timesheet rejected — 2026-12" })).toHaveCount(0);
 await logout(page); await login(page, "EMPLOYEE"); await page.goto("/app/notifications");
 await expect(page.getByRole("article").filter({ hasText: "Timesheet approved — 2026-12" })).toContainText("SENT");
 // Several evaluator runs happened during the complete workflow, yet each logical digest is delivered once.
 expect(await messages("EMPLOYEE", "Timesheet reminder")).toHaveLength(1);
 expect(await messages("MANAGER", "waiting for approval — 2026-12")).toHaveLength(1);
 await logout(page);
});
test("Finance and Admin receive safe SAP FAILED and UNKNOWN email and own history", async ({ page }) => {
 test.setTimeout(150000);
 const failed = process.env.TEST_NOTIFICATION_FAILURE_ID; const unknown = process.env.TEST_NOTIFICATION_UNKNOWN_ID;
 if (!failed || !unknown) throw new Error("Prepare dedicated Phase 7 SAP fixtures.");
 await login(page, "FINANCE");
 for (const [id, state, month] of [[failed, "FAILED", "2030-01"], [unknown, "UNKNOWN", "2030-02"]]) {
  await page.goto("/app/sap/" + id);
  const panel = page.getByRole("region", { name: "SAP export", exact: true });
  await panel.getByRole("button", { name: "Export to SAP", exact: true }).click();
  await expect(panel.getByRole("status")).toHaveText(state, { timeout: 30000 });
  for (const role of ["FINANCE", "ADMIN"] as const) {
   const message = await delivered(role, `SAP export ${state} — ${month}`);
   expect(message.Text).toContain("SAP_TIMESHEET:" + id);
   expect(message.Text).toContain("Status: " + state);
   expect(message.Text).toContain("/app/sap/" + id); expect(message.Text).not.toMatch(/Bearer|stack trace|api.token/i);
   if (state === "UNKNOWN") expect(message.Text).toContain("Reconciliation is required");
  }
 }
 await page.goto("/app/notifications");
 await expect(page.getByRole("article").filter({ hasText: "SAP export FAILED — 2030-01" })).toContainText("SENT");
 await expect(page.getByRole("article").filter({ hasText: "Timesheet rejected — 2026-12" })).toHaveCount(0);
 await logout(page); await page.setViewportSize({ width: 375, height: 812 }); await login(page, "ADMIN");
 await page.getByRole("link", { name: "Notifications", exact: true }).click();
 await expect(page.getByRole("article").filter({ hasText: "SAP export UNKNOWN — 2030-02" })).toContainText("SENT");
 expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
 await logout(page); await login(page, "EMPLOYEE"); await page.goto("/app/notifications");
 await expect(page.getByRole("article").filter({ hasText: "SAP export FAILED — 2030-01" })).toHaveCount(0);
 await logout(page);
});
