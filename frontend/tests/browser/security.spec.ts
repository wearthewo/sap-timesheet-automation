import { test, expect } from "@playwright/test";
import { fixture } from "./fixtures";
const env = fixture("organizations");
test("BFF keeps credentials private and rejects cross-origin logout", async ({ page, context }) => {
 await page.goto("/login"); await page.getByRole("button", { name: "Sign in with your work account" }).click();
 await page.locator("#username").fill(env.TEST_FINANCE_USERNAME); await page.locator("#password").fill(env.TEST_FINANCE_PASSWORD); await page.locator("#kc-login").click();
 await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
 const exposesCredentials = await page.evaluate(async () => /accessToken|access_token|refresh_token|id_token/.test(JSON.stringify(await (await fetch("/api/auth/session")).json())));
 expect(exposesCredentials).toBe(false);
 const cookies = await context.cookies();
 const sessions = cookies.filter(c => c.name.includes("session-token"));
 expect(sessions.length).toBeGreaterThan(0);
 for (const cookie of sessions) { expect(cookie.httpOnly).toBe(true); expect(cookie.sameSite).toBe("Lax"); }
 expect(await page.evaluate(() => document.cookie)).not.toMatch(/session-token/);
 const logoutUrl = new URL("/api/logout", page.url());
 if (process.env.BROWSER_HOST_MAP) logoutUrl.hostname = "host.docker.internal";
 const denied = await page.request.post(logoutUrl.toString(), { headers: { Origin: "https://untrusted.example" }, maxRedirects: 0 });
 expect(denied.status()).toBe(403);
 await page.reload(); await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
 await page.setViewportSize({ width: 375, height: 812 });
 await page.getByRole("button", { name: "Log out", exact: true }).click(); await page.getByRole("button", { name: /logout/i }).click();
 await expect(page).toHaveURL(/\/login$/);
 expect(await page.evaluate(async () => Object.keys(await (await fetch("/api/auth/session")).json()))).toEqual([]);
});
