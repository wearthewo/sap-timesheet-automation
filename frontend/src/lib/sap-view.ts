import type { Timesheet, TimesheetSummary } from "./timesheet-view";
import type { EmployeeView } from "./approval-view";
export type SapExport = { exportId: string; timesheetId: string; status: "PENDING" | "SUCCEEDED" | "FAILED" | "UNKNOWN"; externalReference: string | null; failureCode: string | null; failureMessage: string | null; exportedAt: string | null; idempotencyKey: string };
export type ExportDetail = { employee: EmployeeView; timesheet: Timesheet; export: SapExport | null };
export type ExportPage = { content: { employee: EmployeeView; timesheet: TimesheetSummary; export: SapExport | null }[]; page: number; totalPages: number };
