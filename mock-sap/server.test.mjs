import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createMockSap } from "./server.mjs";
const token = "test-only-token";
const payload = (code = "PROJECT-A") => ({ ExternalSystemReference: "SAP_TIMESHEET:" + randomUUID(), PersonnelNumber: "E001", CalendarYear: 2026, CalendarMonth: 10, TimeEntries: [{ ProjectCode: code, WorkDate: "2026-10-05", Hours: "6.25", Description: "<safe text>" }] });
async function start(options = {}) {
 const server = createMockSap({ token, ...options });
 await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
 return { server, base: "http://127.0.0.1:" + server.address().port };
}
const send = (base, body, auth = token) => fetch(base + "/sap/timesheets", { method: "POST", headers: { Authorization: "Bearer " + auth, "Content-Type": "application/json", "Idempotency-Key": body.ExternalSystemReference }, body: JSON.stringify(body) });
test("accepts, retrieves and detects duplicates across service restarts", async () => {
 const directory = mkdtempSync(join(tmpdir(), "mock-sap-")); const storePath = join(directory, "records.json");
 let app = await start({ storePath });
 try {
  const body = payload(); const response = await send(app.base, body); assert.equal(response.status, 201);
  const result = await response.json();
  const lookup = await fetch(app.base + "/sap/timesheets/" + result.externalReference, { headers: { Authorization: "Bearer " + token } });
  assert.deepEqual((await lookup.json()).document, body);
  assert.equal((await send(app.base, body)).status, 409);
  await new Promise(resolve => app.server.close(resolve));
  app = await start({ storePath });
  assert.equal((await send(app.base, body)).status, 409);
 } finally { await new Promise(resolve => app.server.close(resolve)); rmSync(directory, { recursive: true }); }
});
test("validates auth, project, date and decimal hours; simulates failures", async () => {
 const app = await start();
 try {
  assert.equal((await send(app.base, payload(), "wrong")).status, 401);
  for (const [code, status] of [["MOCK-INVALID", 400], ["MOCK-AUTH", 403], ["MOCK-SERVER", 503]]) assert.equal((await send(app.base, payload(code))).status, status);
  for (const hours of ["0", "-1", "1.001", 6.5]) { const body = payload(); body.TimeEntries[0].Hours = hours; assert.equal((await send(app.base, body)).status, 400); }
  const body = payload(); body.TimeEntries[0].WorkDate = "2026-10-32"; assert.equal((await send(app.base, body)).status, 400);
 } finally { await new Promise(resolve => app.server.close(resolve)); }
});
test("response loss can follow acceptance without creating a second document", async () => {
 const app = await start({ timeoutMs: 100 });
 try {
  const body = payload("MOCK-TIMEOUT");
  await assert.rejects(fetch(app.base + "/sap/timesheets", { method: "POST", headers: { Authorization: "Bearer " + token, "Idempotency-Key": body.ExternalSystemReference }, body: JSON.stringify(body), signal: AbortSignal.timeout(30) }));
  assert.equal((await send(app.base, body)).status, 409);
 } finally { app.server.closeAllConnections(); await new Promise(resolve => app.server.close(resolve)); }
});
