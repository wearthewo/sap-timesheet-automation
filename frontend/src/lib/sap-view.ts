import type { Timesheet, TimesheetSummary } from "./timesheet-view";
import type { EmployeeView } from "./approval-view";
export type SapJob = { status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"; operation: "EXPORT" | "RECONCILE"; attemptCount: number; executionCount: number; lastAttemptAt: string | null; nextAttemptAt: string | null; retryEligible: boolean; reconcileEligible: boolean };
export type SapAttempt = { id: string; attemptNumber: number; operation: string; startedAt: string; finishedAt: string; outcome: string; safeErrorCode: string | null; durationMs: number; externalReference: string | null };
export type SapExport = { exportId: string; timesheetId: string; status: "QUEUED" | "PROCESSING" | "SUCCEEDED" | "FAILED" | "UNKNOWN"; externalReference: string | null; failureCode: string | null; failureMessage: string | null; exportedAt: string | null; idempotencyKey: string; job: SapJob; attempts: SapAttempt[] };
export type ExportDetail = { employee: EmployeeView; timesheet: Timesheet; export: SapExport | null };
export type ExportPage = { content: { employee: EmployeeView; timesheet: TimesheetSummary; export: SapExport | null }[]; page: number; totalPages: number };
