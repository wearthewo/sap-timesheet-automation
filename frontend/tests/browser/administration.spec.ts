import { test, expect, type Page, type Locator } from "@playwright/test";
import { randomBytes } from "node:crypto";
const mailpit = process.env.MAILPIT_API_URL ?? "http://localhost:8025";
type Account = { username: string; email: string; password: string; firstName: string };
function account(role: string): Account { const username = `admin11-${role}-${randomBytes(7).toString("hex")}`; return { username, email: username + "@example.test", password: randomBytes(24).toString("hex"), firstName: role === "employee" ? "Νίκος" : "Zoë" }; }
async function mail(email: string, subject: string) {
 const url = mailpit + "/api/v1/search?query=" + encodeURIComponent(`to:${email} subject:"${subject}"`);
 async function find() { return (await (await fetch(url)).json()).messages as { ID: string; To: { Address: string }[] }[]; }
 await expect.poll(async () => (await find()).length, { timeout: 45000, intervals: [500, 1000, 2000] }).toBe(1);
 const item = (await find())[0]; expect(item.To.map(t => t.Address)).toEqual([email]);
 return (await (await fetch(mailpit + "/api/v1/message/" + item.ID)).json()) as { Text: string };
}
async function safeGoto(page: Page, link: string) { try { await page.goto(link.replaceAll("&amp;", "&")); } catch { throw new Error("Secure email link could not be opened"); } }
async function register(page: Page, a: Account) {
 await page.getByRole("button", { name: "Create an account", exact: true }).click();
 await page.locator("#firstName").fill(a.firstName); await page.locator("#lastName").fill("Administration");
 await page.locator("#email").fill(a.email); await page.locator("#username").fill(a.username);
 await page.getByRole("button", { name: /register/i }).click();
 const message = await mail(a.email, "Verify email"); const link = message.Text.match(/https?:\/\/[^\s<>]+\/login-actions\/[^\s<>]+/)?.[0];
 if (!link) throw new Error("Verification link missing"); await safeGoto(page, link);
 await page.locator("#password-new").fill(a.password); await page.locator("#password-confirm").fill(a.password);
 await page.getByRole("button", { name: /submit/i }).click();
}
async function logout(page: Page) { await page.getByRole("button", { name: "Log out", exact: true }).click(); await page.getByRole("button", { name: /logout/i }).click(); await expect(page).toHaveURL(/\/login$/); }
async function login(page: Page, a: Account) { await page.goto("/login"); await page.getByRole("button", { name: "Sign in with your work account" }).click(); await page.locator("#username").fill(a.username); await page.locator("#password").fill(a.password); await page.locator("#kc-login").click(); await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible(); }
async function choose(form: Locator, label: string, search: string) {
 await form.getByLabel(`Search ${label}`, { exact: true }).fill(search); await form.getByRole("button", { name: `Find ${label}`, exact: true }).click();
 const select = form.getByLabel(label, { exact: true }); const option = select.locator("option").filter({ hasText: search }); await expect(option).toHaveCount(1); await select.selectOption((await option.getAttribute("value"))!);
}
async function createEmployee(page: Page, number: string, name: string, manager?: string) {
 await page.goto("/app/admin/employees"); const form = page.getByRole("form", { name: "Create employee", exact: true });
 await form.getByLabel("Employee number", { exact: true }).fill(number); await form.getByLabel("Employee name", { exact: true }).fill(name);
 if (manager) await choose(form, "Manager", manager);
 await form.getByRole("button", { name: "Create employee", exact: true }).click(); await expect(page).toHaveURL(/\/app\/admin\/employees\/[0-9a-f-]+$/); return page.url();
}
async function invite(page: Page, a: Account, role: string, fromEmployee = false) {
 if (fromEmployee) await page.getByRole("link", { name: "Invite employee", exact: true }).click(); else await page.goto("/app/admin/invitations");
 const form = page.getByRole("form", { name: "Send invitation", exact: true }); await form.getByLabel("Invitation email").fill(a.email); await form.getByLabel("Organization role", { exact: true }).selectOption(role); await form.getByRole("checkbox").check(); await form.getByRole("button", { name: "Send invitation" }).click();
 await expect(form.getByRole("status")).toHaveText("Invitation queued for delivery.");
 const message = await mail(a.email, "Workforce organization invitation"); expect(message.Text.includes(`as ${role}`)).toBe(true);
 const link = message.Text.match(/https?:\/\/[^\s]+\/invitations\/accept#token=[^\s]+/)?.[0]; if (!link) throw new Error("Invitation link missing"); return link;
}
async function acceptNew(page: Page, a: Account, link: string) {
 await safeGoto(page, link); await expect(page).toHaveURL(/\/invitations\/review$/);
 await register(page, a); await expect(page.getByRole("button", { name: "Accept invitation" })).toBeVisible(); await page.getByRole("button", { name: "Accept invitation" }).click(); await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
 await expect(page.getByRole("link", { name: "Administration", exact: true })).toHaveCount(0);
}
test("new organization administers profiles, invitations, roles and assignments through a real employee approval workflow", async ({ page, context }) => {
 test.setTimeout(600000);
 page.setDefaultTimeout(20000);
 const admin = account("admin"), employee = account("employee"), manager = account("manager"), finance = account("finance");
 await page.goto("/login"); await register(page, finance); await expect(page.getByRole("heading", { name: "Create your organization" })).toBeVisible(); await logout(page);
 await page.goto("/login"); await register(page, admin); await expect(page.getByRole("heading", { name: "Create your organization" })).toBeVisible();
 await page.getByLabel("Organization name", { exact: true }).fill("Phase Eleven & Team"); await page.getByLabel("Organization address", { exact: true }).fill(admin.username);
 await page.getByRole("button", { name: "Create organization", exact: true }).click(); await expect(page.getByRole("link", { name: "Administration", exact: true })).toBeVisible();
 await expect(page.getByRole("region", { name: "Organization overview" }).getByText("Active employees", { exact: true }).locator("..")).toContainText("0");
 await expect(page.getByRole("region", { name: "Organization setup" })).toContainText("Invite your team");
 const managerUrl = await createEmployee(page, "M-001", "Zoë Manager"); const managerLink = await invite(page, manager, "MANAGER", true);
 const employeeUrl = await createEmployee(page, "E-001", "Νίκος Employee", "M-001"); const employeeLink = await invite(page, employee, "EMPLOYEE", true);
 const financeLink = await invite(page, finance, "FINANCE");
 await page.goto("/app/admin/projects"); const projectForm = page.getByRole("form", { name: "Create project", exact: true });
 await projectForm.getByLabel("Project code").fill("ADMIN11"); await projectForm.getByLabel("Project name").fill("Administration workflow"); await projectForm.getByRole("button", { name: "Create project" }).click(); await expect(page).toHaveURL(/\/admin\/projects\/[0-9a-f-]+$/); const projectUrl = page.url();
 await page.goto(employeeUrl); const assignment = page.getByRole("form", { name: "Assign project", exact: true }); await choose(assignment, "Project", "ADMIN11"); await assignment.getByLabel("Valid from").fill("2026-01-01"); await assignment.getByRole("button", { name: "Assign project" }).click(); await expect(assignment.getByRole("status")).toHaveText("Changes saved.");
 await page.goto("/app/admin/members"); const own = page.locator("details").filter({ hasText: admin.email }); await own.locator("summary").click(); await own.getByLabel("Organization role", { exact: true }).selectOption("EMPLOYEE"); await own.getByRole("checkbox").check(); await own.getByRole("button", { name: "Update membership" }).click(); await expect(own.getByRole("alert")).toContainText("at least one active administrator");
 await logout(page); await acceptNew(page, manager, managerLink); await logout(page); await acceptNew(page, employee, employeeLink);
 expect((await context.cookies()).some(c => c.name === "workforce-invitation")).toBe(false);
 await page.goto("/app/timesheets/2026/10"); await page.getByRole("button", { name: "Create draft" }).click(); await page.getByLabel("Work date").fill("2026-10-05");
 const projects = page.getByRole("combobox", { name: "Project", exact: true }); const option = projects.locator("option").filter({ hasText: "ADMIN11" }); await expect(option).toHaveCount(1); await projects.selectOption((await option.getAttribute("value"))!);
 await page.getByLabel("Hours", { exact: true }).fill("6.25"); await page.getByLabel("Description").fill("New organization, real staff invitation"); await page.getByRole("button", { name: "Save entry", exact: true }).click(); await expect(page.getByTestId("monthly-total")).toContainText("6.25");
 await page.getByRole("button", { name: "Submit month", exact: true }).click(); await page.getByRole("button", { name: "Confirm submission" }).click(); await expect(page.getByText("PENDING_APPROVAL", { exact: true })).toBeVisible(); await logout(page);
 await login(page, manager); await page.goto("/app/approvals"); const queue = page.getByRole("region", { name: "Pending timesheets" }); await expect(queue.getByRole("link")).toHaveCount(1); await queue.getByRole("link").click(); await expect(page).toHaveURL(/\/app\/approvals\/[0-9a-f-]+$/); const reviewUrl = page.url();
 await page.getByRole("button", { name: "Approve timesheet", exact: true }).click(); await page.getByRole("button", { name: "Confirm approval" }).click(); await expect(page.getByText("APPROVED", { exact: true })).toBeVisible(); await logout(page);
 await safeGoto(page, financeLink); await expect(page).toHaveURL(/\/invitations\/review$/); await page.getByRole("button", { name: "Sign in with your work account" }).click(); await page.locator("#username").fill(finance.username); await page.locator("#password").fill(finance.password); await page.locator("#kc-login").click(); await page.getByRole("button", { name: "Accept invitation" }).click(); await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible(); await page.goto("/app/sap/" + reviewUrl.split("/").pop()); await page.getByRole("button", { name: "Export to SAP", exact: true }).click(); await expect(page.getByText("SUCCEEDED", { exact: true })).toBeVisible({ timeout: 60000 }); await expect(page.getByRole("button", { name: "Download SAP Excel" })).toBeVisible(); await logout(page);
 await login(page, admin); await page.goto(employeeUrl); await expect(page.locator("main")).toContainText(employee.email);
 const deactivate = page.getByRole("form", { name: "Deactivate employee", exact: true }); await deactivate.getByRole("checkbox").check(); await deactivate.getByRole("button", { name: "Deactivate employee" }).click(); await expect(page.getByRole("button", { name: "Activate employee", exact: true })).toBeVisible();
 const reactivate = page.getByRole("form", { name: "Activate employee", exact: true }); await reactivate.getByRole("checkbox").check(); await reactivate.getByRole("button", { name: "Activate employee" }).click(); await expect(page.getByRole("button", { name: "Deactivate employee", exact: true })).toBeVisible();
 await page.goto(projectUrl); await expect(page.locator("summary").filter({ hasText: "E-001" })).toBeVisible(); const archive = page.getByRole("form", { name: "Archive project", exact: true }); await archive.getByRole("checkbox").check(); await archive.getByRole("button", { name: "Archive project" }).click(); await expect(page.getByRole("button", { name: "Activate project", exact: true })).toBeVisible();
 await page.setViewportSize({ width: 375, height: 812 }); await page.goto(employeeUrl); expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true); await page.goto(projectUrl); expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true); await page.goto("/app/admin/employees"); await expect(page.getByRole("link", { name: "Administration", exact: true })).toBeVisible(); expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true); await logout(page);
 await login(page, employee); await page.goto("/app/timesheets/2026/10"); await expect(page.getByText("APPROVED", { exact: true })).toBeVisible(); await expect(page.getByRole("region", { name: "Entry editor" })).toHaveCount(0); await logout(page);
 // An already accepted bearer cannot grant access to an unrelated authenticated account.
 await login(page, manager); await safeGoto(page, employeeLink); await expect(page.getByRole("alert").filter({ hasText: "unavailable" })).toBeVisible(); await expect(page.getByRole("button", { name: "Accept invitation" })).toHaveCount(0); await logout(page);
 // The linked manager profile remains available to the organization administrator.
 await login(page, admin); await page.goto(managerUrl); await expect(page.locator("main")).toContainText(manager.email); await logout(page);
});
