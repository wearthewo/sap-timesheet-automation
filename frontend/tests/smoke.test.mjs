import test from "node:test";
import assert from "node:assert/strict";

const base = process.env.SMOKE_BASE_URL ?? "http://localhost:3000";
test("frontend health is reachable", async () => {
 const response = await fetch(base + "/api/health");
 assert.equal(response.status, 200);
 assert.deepEqual(await response.json(), { status: "UP" });
});
test("login renders a work-account sign-in control", async () => {
 const response = await fetch(base + "/login");
 assert.equal(response.status, 200);
 assert.match(await response.text(), /Sign in with your work account/);
});
test("anonymous session contains no user or tokens", async () => {
 const response = await fetch(base + "/api/auth/session");
 assert.equal(response.status, 200);
 assert.deepEqual(await response.json(), {});
});
test("anonymous root redirects to login", async () => {
 const response = await fetch(base + "/", { redirect: "manual" });
 assert.equal(response.status, 307);
 assert.equal(new URL(response.headers.get("location"), base).pathname, "/login");
});
test("logout rejects a cross-origin request", async () => {
 const response = await fetch(base + "/api/logout", { method: "POST", headers: { Origin: "https://untrusted.example" }, redirect: "manual" });
 assert.equal(response.status, 403);
});
test("logout rejects requests with no Origin", async () => {
 const response = await fetch(base + "/api/logout", { method: "POST", redirect: "manual" });
 assert.equal(response.status, 403);
});
test("logout is not allowed through GET", async () => {
 const response = await fetch(base + "/api/logout", { redirect: "manual" });
 assert.equal(response.status, 405);
});
for (const path of ["/app/timesheets", "/app/timesheets/2026/10"]) {
 test(`anonymous ${path} cannot access employee content`, async () => {
  const response = await fetch(base + path);
  assert.equal(new URL(response.url).pathname, "/login");
  assert.match(await response.text(), /Sign in with your work account/);
 });
}
