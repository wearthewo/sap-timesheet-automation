// Compare actual Mailpit delivery with persisted notification intents for QA fixtures only.
// Input is an ignored JSON array exported by the verification runner, never credentials.
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";
import { notificationReadiness } from "../ci/notification-verification.mjs";
const normalize = value => value.replace(/\r\n/g, "\n").replace(/\n$/, "");
export async function verifyMailpit(intents, { base=process.env.MAILPIT_API_URL ?? "http://localhost:8025", request=fetch, log=console.log } = {}) {
assert.equal(notificationReadiness(intents), true, "Unresolved notification delivery");
const counts = new Map();
let cancelled = 0;
for (const intent of intents) {
 const query = 'to:' + intent.recipient_email + ' subject:"' + intent.subject + '"';
 const response = await request(base + "/api/v1/search?limit=100&query=" + encodeURIComponent(query));
 assert.equal(response.status, 200);
 const candidates = (await response.json()).messages;
 assert.ok(Array.isArray(candidates) && candidates.length < 100, "Mailpit search is incomplete; cannot prove delivery/deduplication");
 let matches = 0;
 for (const item of candidates) {
  const message = await request(base + "/api/v1/message/" + item.ID);
  assert.equal(message.status, 200);
  const detail = await message.json();
  if (detail.Subject !== intent.subject || normalize(detail.Text) !== normalize(intent.body)) continue;
  assert.deepEqual(detail.To.map(to => to.Address), [intent.recipient_email]);
  assert.equal(detail.HTML ?? "", "", "Current contract is plain-text UTF-8, not HTML");
  matches++;
 }
 if (intent.status === "CANCELLED") {
  assert.equal(matches, 0, "Cancelled notification was delivered " + intent.id);
  cancelled++;
  continue;
 }
 assert.equal(matches, 1, "Expected one exact recipient/subject/body match for " + intent.type + " " + intent.id);
 counts.set(intent.type, (counts.get(intent.type) ?? 0) + 1);
}
for (const type of ["TIMESHEET_REMINDER", "APPROVAL_REMINDER", "TIMESHEET_REJECTED", "TIMESHEET_APPROVED", "SAP_EXPORT_FAILED", "SAP_EXPORT_UNKNOWN", "FINANCE_MONTHLY_REMINDER"]) {
 assert.ok(counts.has(type), "Missing business notification type " + type);
 log("PASS " + type + ": " + counts.get(type) + " exact delivered messages");
}
log("PASS total: " + (intents.length - cancelled) + "; recipients, subjects, UTF-8 text, links, comments and ordinary deduplication");
log("PASS cancellations: " + cancelled + "; independent business evidence and no matching SMTP delivery");
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
 const intents = JSON.parse(await readFile(process.argv[2], "utf8"));
 await verifyMailpit(intents);
}
