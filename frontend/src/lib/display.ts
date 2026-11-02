export const businessTimezone = "Europe/Athens";
export function businessDate(value = new Date()) {
 const parts = new Intl.DateTimeFormat("en-CA", { timeZone: businessTimezone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(value);
 const part = (name: string) => parts.find(p => p.type === name)!.value;
 return `${part("year")}-${part("month")}-${part("day")}`;
}
export function eventTime(value: string) {
 return new Intl.DateTimeFormat("en-GB", { timeZone: businessTimezone, dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) + " · Athens";
}
export function calendarDate(value: string) {
 return new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", dateStyle: "medium" }).format(new Date(value + "T00:00:00Z"));
}
export function label(value: string) {
 const labels: Record<string,string> = { PENDING_APPROVAL: "Awaiting manager review", UNKNOWN: "Needs reconciliation", SUCCEEDED: "Export complete", NOT_EXPORTED: "Awaiting export", NO_TIMESHEET: "Not started", NOT_APPLICABLE: "Not applicable", SENT: "Accepted by email server" };
 return labels[value] ?? value.toLowerCase().replaceAll("_", " ").replace(/^./, c => c.toUpperCase());
}
