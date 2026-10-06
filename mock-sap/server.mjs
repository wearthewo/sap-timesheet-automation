import http from "node:http";
import { timingSafeEqual, createHash } from "node:crypto";
import { existsSync, readFileSync, mkdirSync, writeFileSync, renameSync } from "node:fs";
import { dirname } from "node:path";
import { pathToFileURL } from "node:url";

export function createMockSap({ token, storePath, timeoutMs = 10000 } = {}) {
 if (!token) throw new Error("SAP_API_TOKEN is required");
 const records = new Map(storePath && existsSync(storePath) ? JSON.parse(readFileSync(storePath, "utf8")) : []);
 const timers = new Set();
 function save() {
  if (!storePath) return;
  mkdirSync(dirname(storePath), { recursive: true });
  writeFileSync(storePath + ".tmp", JSON.stringify([...records]));
  renameSync(storePath + ".tmp", storePath);
 }
 function reply(res, status, body) { res.writeHead(status, { "Content-Type": "application/json" }); res.end(JSON.stringify(body)); }
 const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") return reply(res, 200, { status: "UP" });
  const supplied = Buffer.from(req.headers.authorization ?? "");
  const expected = Buffer.from("Bearer " + token);
  if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) return reply(res, 401, { code: "UNAUTHORIZED", message: "Invalid credentials" });
  if (req.method === "GET" && /^\/sap\/timesheets\/SAP-TS-[A-F0-9]+$/.test(req.url)) {
   const record = [...records.values()].find(r => "/sap/timesheets/" + r.externalReference === req.url);
   return reply(res, record ? 200 : 404, record ? { success: true, externalReference: record.externalReference, document: record.document } : { code: "NOT_FOUND" });
  }
  if (req.method !== "POST" || req.url !== "/sap/timesheets") return reply(res, 404, { code: "NOT_FOUND" });
  try {
   const chunks = []; let length = 0;
   for await (const chunk of req) {
    length += chunk.length;
    if (length > 65536) { reply(res, 413, { code: "PAYLOAD_TOO_LARGE" }); return; }
    chunks.push(chunk);
   }
   const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
   const key = req.headers["idempotency-key"];
   const entries = body.TimeEntries;
   const validDate = date => typeof date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(date) && Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date && +date.slice(0, 4) === body.CalendarYear && +date.slice(5, 7) === body.CalendarMonth;
   if (typeof key !== "string" || !/^SAP_TIMESHEET:[0-9a-f-]{36}$/.test(key) || body.ExternalSystemReference !== key ||
       typeof body.PersonnelNumber !== "string" || !body.PersonnelNumber.trim() || body.PersonnelNumber.length > 100 ||
       !Number.isInteger(body.CalendarYear) || body.CalendarYear < 1 || body.CalendarYear > 9999 ||
       !Number.isInteger(body.CalendarMonth) || body.CalendarMonth < 1 || body.CalendarMonth > 12 ||
       !Array.isArray(entries) || !entries.length || entries.some(e => typeof e.ProjectCode !== "string" || !e.ProjectCode.trim() || e.ProjectCode.length > 100 || !validDate(e.WorkDate) ||
        typeof e.Hours !== "string" || !/^\d{1,5}(\.\d{1,2})?$/.test(e.Hours) || BigInt(e.Hours.replace(".", "")) <= 0n ||
        (e.Description != null && (typeof e.Description !== "string" || e.Description.length > 2000))))
    return reply(res, 400, { code: "INVALID_TIMESHEET", message: "Invalid timesheet fields" });
   if (records.has(key)) return reply(res, 409, { code: "DUPLICATE_TIMESHEET", message: "Timesheet already exists" });
   const scenarios = new Set(entries.map(e => e.ProjectCode));
   if (scenarios.has("MOCK-INVALID")) return reply(res, 400, { code: "INVALID_PROJECT", message: "Project code is invalid" });
   if (scenarios.has("MOCK-AUTH")) return reply(res, 403, { code: "FORBIDDEN", message: "Personnel not authorized" });
   if (scenarios.has("MOCK-SERVER")) return reply(res, 503, { code: "SERVICE_UNAVAILABLE", message: "SAP unavailable" });
   const externalReference = "SAP-TS-" + createHash("sha256").update(key).digest("hex").slice(0, 24).toUpperCase();
   records.set(key, { externalReference, document: body });
   try { save(); } catch { records.delete(key); return reply(res, 503, { code: "STORAGE_UNAVAILABLE", message: "SAP unavailable" }); }
   const accepted = { success: true, externalReference, message: "Timesheet accepted" };
   if (scenarios.has("MOCK-TIMEOUT")) {
    const timer = setTimeout(() => { timers.delete(timer); reply(res, 201, accepted); }, timeoutMs);
    timers.add(timer);
    return;
   }
   return reply(res, 201, accepted);
  } catch {
   return reply(res, 400, { code: "INVALID_REQUEST", message: "Request could not be processed" });
  }
 });
 server.on("close", () => { for (const timer of timers) clearTimeout(timer); });
 return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
 const server = createMockSap({ token: process.env.SAP_API_TOKEN, storePath: process.env.SAP_STORE_PATH ?? "/data/timesheets.json" });
 server.listen(Number(process.env.PORT ?? 8090), "0.0.0.0", () => console.log("Mock SAP listening"));
}
