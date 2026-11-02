import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import { randomBytes } from "node:crypto";

const mailpit = process.env.MAILPIT_API_URL ?? "http://localhost:8025";
async function mail(email: string, subject: string) {
 const query = encodeURIComponent(`to:${email} subject:"${subject}"`);
 async function found() { const r = await fetch(mailpit + "/api/v1/search?query=" + query); if (!r.ok) throw new Error("Mailpit unavailable"); return (await r.json()).messages as { ID: string; To: { Address: string }[] }[]; }
 await expect.poll(async () => (await found()).length, { timeout: 45000, intervals: [500, 1000, 2000] }).toBe(1);
 const item = (await found())[0]; expect(item.To.map(t => t.Address)).toEqual([email]);
 return (await (await fetch(mailpit + "/api/v1/message/" + item.ID)).json()) as { Text: string; HTML: string; Subject: string };
}
async function followIdentityLink(page: Page, text: string) {
 const link = text.match(/https?:\/\/[^\s<>]+\/login-actions\/[^\s<>]+/)?.[0];
 if (!link) throw new Error("Identity action link missing from email");
 // Verification/reset links are secrets: never log them or retain Playwright traces.
 try { await page.goto(link.replaceAll("&amp;", "&")); } catch { throw new Error("Identity action page unavailable"); }
}
async function logout(page: Page) {
 await page.getByRole("button", { name: "Log out", exact: true }).click();
 await page.getByRole("button", { name: /logout/i }).click();
 await expect(page).toHaveURL(/\/login$/);
}
async function register(page: Page, context: BrowserContext, username: string, password: string) {
 const email = username + "@example.test";
 await page.goto("/login");
 await page.getByRole("button", { name: "Create an account", exact: true }).click();
 await page.locator("#firstName").fill("Zoë"); await page.locator("#lastName").fill("Onboarding");
 await page.locator("#email").fill(email); await page.locator("#username").fill(username);
 await page.getByRole("button", { name: /register/i }).click();
 // Keycloak 26.8 verifies the address before collecting the new account password.
 await expect(page.getByText(/verify your email address|verify your email|email verification/i).first()).toBeVisible();
 expect((await context.cookies()).some(c => /next-auth\.session-token/.test(c.name))).toBe(false);
 const message = await mail(email, "Verify email");
 expect(message.Text).toContain("Workforce"); expect(message.HTML).toContain("login-actions");
 await followIdentityLink(page, message.Text);
 await page.locator("#password-new").fill(password); await page.locator("#password-confirm").fill(password);
 await page.getByRole("button", { name: /submit/i }).click();
 await expect(page.getByRole("heading", { name: "Create your organization" })).toBeVisible();
}
test("self-registration verifies email, creates isolated workspace, handles slug conflicts and recovers account", async ({ page, context, browser }) => {
 test.setTimeout(240000);
 const suffix = randomBytes(8).toString("hex"), username = "onboard-" + suffix, email = username + "@example.test";
 const password = randomBytes(24).toString("hex"), slug = "workspace-" + suffix, takenSlug = "taken-" + suffix;
 // This test creates its own collision target through the real UI. It runs
 // independently on an empty realm and never depends on CI seed names/order.
 const ownerContext = await browser.newContext();
 try {
  const owner = await ownerContext.newPage();
  await register(owner, ownerContext, "slug-owner-" + suffix, randomBytes(24).toString("hex"));
  // A customer can type into SSR inputs while application scripts are still
  // downloading. Preserve that name when hydration finishes and they edit slug.
  const scripts = "**/_next/static/**/*.js";
  let release!: () => void;
  const held = new Promise<void>(resolve => { release = resolve; });
  await owner.route(scripts, async route => { await held; await route.continue(); });
  try {
   await owner.reload({ waitUntil: "commit" });
   await owner.getByLabel("Organization name", { exact: true }).fill("Collision fixture");
   await expect(owner.getByLabel("Organization name", { exact: true })).toHaveValue("Collision fixture");
  } finally { release(); }
  await owner.waitForLoadState("load");
  await owner.unroute(scripts);
  await owner.getByLabel("Organization address", { exact: true }).fill(takenSlug);
  await expect(owner.getByLabel("Organization name", { exact: true })).toHaveValue("Collision fixture");
  await owner.getByRole("button", { name: "Create organization", exact: true }).click();
  await expect(owner.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
  await logout(owner);
 } finally { await ownerContext.close(); }
 await register(page, context, username, password);
 // A server-rendered form can be submitted before hydration. Exercise that path
 // deterministically using the real verified session, without storing credentials.
 const scripts = "**/_next/static/**/*.js";
 await page.route(scripts, route => route.abort());
 try {
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.getByLabel("Organization name", { exact: true }).fill("Zoë & Team");
  await page.getByLabel("Organization address", { exact: true }).fill(takenSlug);
  await page.getByRole("button", { name: "Create organization", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "already in use" })).toBeVisible();
  await expect(page.getByLabel("Organization name", { exact: true })).toHaveValue("Zoë & Team");
  await expect(page.getByLabel("Organization address", { exact: true })).toHaveValue(takenSlug);
 } finally { await page.unroute(scripts); }
 await page.reload();
 await page.getByLabel("Organization name", { exact: true }).fill("Zoë & Team");
 await page.getByLabel("Organization address", { exact: true }).fill(takenSlug);
 await page.getByRole("button", { name: "Create organization", exact: true }).click();
 await expect(page.getByRole("alert").filter({ hasText: "already in use" })).toBeVisible();
 await expect(page.getByLabel("Organization name", { exact: true })).toHaveValue("Zoë & Team");
 await page.getByLabel("Organization address", { exact: true }).fill(slug);
 await page.getByRole("button", { name: "Create organization", exact: true }).click();
 await expect(page.getByText("ADMIN · Zoë & Team", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Organization setup" })).toContainText("Set your team up for success");
  await expect(page.getByRole("region", { name: "Organization overview" }).getByText("Active employees", { exact: true }).locator("..")).toContainText("0");
 await expect(page.locator("body")).not.toContainText("QA3-E-");
 const selected = (await context.cookies()).find(c => c.name === "workforce-organization");
 expect(selected?.httpOnly).toBe(true);
 const session = await page.evaluate(async () => (await fetch("/api/auth/session")).json());
 expect(session.accessToken).toBeUndefined();
 await page.getByRole("link", { name: "SAP exports", exact: true }).click();
 await expect(page.getByText("No approved timesheets are available.")).toBeVisible();
 await page.setViewportSize({ width: 375, height: 812 });
 await page.getByRole("link", { name: "Organization", exact: true }).click();
 await expect(page.getByRole("heading", { name: "Choose your organization" })).toBeVisible();
 await expect(page.getByLabel("Organization", { exact: true }).locator("option")).toHaveCount(1);
 await page.getByRole("button", { name: "Continue", exact: true }).click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
 await logout(page);
 expect((await context.cookies()).some(c => c.name === "workforce-organization")).toBe(false);
 // Recovery email belongs to Keycloak, separate from the Workforce notification outbox.
 await page.getByRole("button", { name: "Sign in with your work account" }).click();
 await page.getByRole("link", { name: /forgot password/i }).click();
 await page.locator("#username").fill(username);
 await page.getByRole("button", { name: /submit/i }).click();
 const reset = await mail(email, "Reset password");
 await followIdentityLink(page, reset.Text);
 const replacement = randomBytes(24).toString("hex");
 await page.locator("#password-new").fill(replacement); await page.locator("#password-confirm").fill(replacement);
 await page.getByRole("button", { name: /submit/i }).click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
 await expect(page.getByText("ADMIN · Zoë & Team", { exact: true })).toBeVisible();
 await logout(page);
});
