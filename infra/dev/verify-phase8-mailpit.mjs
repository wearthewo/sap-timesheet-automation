// Compare actual Mailpit delivery with persisted notification intents for QA fixtures only.
// Input is an ignored JSON array exported by the verification runner, never credentials.
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
const intents = JSON.parse(await readFile(process.argv[2], "utf8"));
const base = process.env.MAILPIT_API_URL ?? "http://localhost:8025";
const normalize = value => value.replace(/\r\n/g, "\n").replace(/\n$/, "");
const counts = new Map();
for (const intent of intents) {
 assert.equal(intent.status, "SENT", "Expected delivered intent " + intent.id);
 const query = 'to:' + intent.recipient_email + ' subject:"' + intent.subject + '"';
 const response = await fetch(base + "/api/v1/search?limit=100&query=" + encodeURIComponent(query));
 assert.equal(response.status, 200);
 const candidates = (await response.json()).messages;
 let matches = 0;
 for (const item of candidates) {
  const detail = await (await fetch(base + "/api/v1/message/" + item.ID)).json();
  if (detail.Subject !== intent.subject || normalize(detail.Text) !== normalize(intent.body)) continue;
  assert.deepEqual(detail.To.map(to => to.Address), [intent.recipient_email]);
  assert.equal(detail.HTML ?? "", "", "Current contract is plain-text UTF-8, not HTML");
  matches++;
 }
 assert.equal(matches, 1, "Expected one exact recipient/subject/body match for " + intent.type + " " + intent.id);
 counts.set(intent.type, (counts.get(intent.type) ?? 0) + 1);
}
for (const type of ["TIMESHEET_REMINDER", "APPROVAL_REMINDER", "TIMESHEET_REJECTED", "TIMESHEET_APPROVED", "SAP_EXPORT_FAILED", "SAP_EXPORT_UNKNOWN", "FINANCE_MONTHLY_REMINDER"]) {
 assert.ok(counts.has(type), "Missing business notification type " + type);
 console.log("PASS " + type + ": " + counts.get(type) + " exact delivered messages");
}
console.log("PASS total: " + intents.length + "; recipients, subjects, UTF-8 text, links, comments and ordinary deduplication");
