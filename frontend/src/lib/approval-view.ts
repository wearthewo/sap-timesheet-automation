import type { Timesheet, TimesheetSummary } from "./timesheet-view";
export type EmployeeView = { id: string; employeeNumber: string; displayName: string };
export type ApprovalReview = { employee: EmployeeView; timesheet: Timesheet };
export type PendingPage = { content: { employee: EmployeeView; timesheet: TimesheetSummary }[]; page: number; totalPages: number; totalElements: number };
