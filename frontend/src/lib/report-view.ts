// Rendering projections only; the backend OpenAPI contract remains authoritative.
export type ReportPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type FinanceSummary = {
 year: number; month: number; activeEmployeeCount: number; noTimesheetCount: number; draftCount: number;
 pendingApprovalCount: number; rejectedCount: number; approvedCount: number;
 sapNotExportedCount: number; sapQueuedCount: number; sapProcessingCount: number; sapSucceededCount: number;
 sapFailedCount: number; sapUnknownCount: number; totalRecordedHours: string; totalApprovedHours: string; totalSapExportedHours: string;
};
export type EmployeeReport = {
 employeeId: string; employeeNumber: string; employeeName: string; email: string; managerName: string | null;
 timesheetId: string | null; timesheetState: string; totalHours: string; submittedAt: string | null;
 latestApprovalDecision: string | null; latestApprovalTimestamp: string | null; sapState: string; sapExternalReference: string | null;
};
export type ProjectTotal = { projectId: string; projectCode: string; projectName: string; totalHours: string; employeeCount: number };
export type EmployeeProject = { employeeId: string; employeeNumber: string; employeeName: string; projectId: string; projectCode: string; projectName: string; totalHours: string };
