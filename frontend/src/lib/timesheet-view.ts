// View models only; the backend OpenAPI remains the API contract.
export type DecisionView = { id: string; managerEmployeeId: string; decision: "APPROVED" | "REJECTED"; comment: string | null; decidedAt: string };
export type ProjectOption = { id: string; code: string; name: string };
export type Entry = { id: string; project: ProjectOption; workDate: string; hours: string; description: string | null };
export type TimesheetSummary = { id: string; year: number; month: number; status: "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED"; totalHours: string; submittedAt: string | null; version: number };
export type Timesheet = TimesheetSummary & { entries: Entry[]; dailyTotals: { workDate: string; hours: string }[]; maxDailyHours: string; approvalHistory: DecisionView[] };
export type TimesheetPage = { content: TimesheetSummary[]; page: number; totalPages: number };
export type ActionResult<T> = { data: T; error?: never } | { error: string; code?: string; data?: never };
export function monthLabel(year: number, month: number) {
 return new Intl.DateTimeFormat("en", { month: "long", year: "numeric", timeZone: "UTC" }).format(new Date(`${String(year).padStart(4,"0")}-${String(month).padStart(2,"0")}-01T00:00:00Z`));
}
