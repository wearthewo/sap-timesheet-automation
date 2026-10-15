import { businessDate, eventTime, label } from "@/lib/display";
import Link from "next/link";
import ReportDownloads from "./downloads";
import { backendRequest, BackendError, currentUser } from "@/lib/backend";
import type { FinanceSummary, EmployeeReport, ProjectTotal, EmployeeProject, ReportPage } from "@/lib/report-view";


type Query = { [key: string]: string | string[] | undefined };
const states = ["NO_TIMESHEET", "DRAFT", "PENDING_APPROVAL", "REJECTED", "APPROVED"];
const sapStates = ["NOT_APPLICABLE", "NOT_EXPORTED", "QUEUED", "PROCESSING", "SUCCEEDED", "FAILED", "UNKNOWN"];
function text(query: Query, name: string, fallback = "") { const value = query[name]; return typeof value === "string" ? value : fallback; }
function Pager({ data, name, query }: { data: ReportPage<unknown>; name: string; query: URLSearchParams }) {
 function href(page: number) { const next = new URLSearchParams(query); next.set(name, String(page)); return `/app/reports/monthly?${next}`; }
 return <div className="mt-4 flex flex-wrap items-center gap-5 text-sm">
  <span>{data.totalElements} results · Page {data.page + 1} of {Math.max(1, data.totalPages)}</span>
  {data.page > 0 && <Link className="font-semibold text-blue-700" href={href(data.page - 1)}>Previous</Link>}
  {data.page + 1 < data.totalPages && <Link className="font-semibold text-blue-700" href={href(data.page + 1)}>Next</Link>}
 </div>;
}
export default async function MonthlyReports({ searchParams }: { searchParams: Promise<Query> }) {
 const user = await currentUser();
 if (!["FINANCE", "ADMIN"].includes(user.role)) return <p role="alert">You do not have access to Finance reports.</p>;
 const search = await searchParams;
 const [year, month] = businessDate().split("-");
 const query = new URLSearchParams({ year: text(search, "year", year), month: text(search, "month", String(Number(month))) });
 for (const key of ["employee", "project", "timesheetStatus", "sapStatus", "mode"]) { const value = text(search, key); if (value) query.set(key, value); }
 const employeePage = text(search, "page", "0"), projectPage = text(search, "projectPage", "0"), breakdownPage = text(search, "breakdownPage", "0");
 const links = new URLSearchParams(query);
 links.set("page", employeePage); links.set("projectPage", projectPage); links.set("breakdownPage", breakdownPage);
 let summary: FinanceSummary, employees: ReportPage<EmployeeReport>, projects: ReportPage<ProjectTotal>, breakdown: ReportPage<EmployeeProject>;
 try {
  [summary, employees, projects, breakdown] = await Promise.all([
   backendRequest<FinanceSummary>(`/api/v1/reports/monthly/summary?${query}`),
   backendRequest<ReportPage<EmployeeReport>>(`/api/v1/reports/monthly/employees?${query}&page=${encodeURIComponent(employeePage)}`),
   backendRequest<ReportPage<ProjectTotal>>(`/api/v1/reports/monthly/projects?${query}&page=${encodeURIComponent(projectPage)}`),
   backendRequest<ReportPage<EmployeeProject>>(`/api/v1/reports/monthly/employee-projects?${query}&page=${encodeURIComponent(breakdownPage)}`),
  ]);
 } catch (error) {
  if (error instanceof BackendError) return <div><p role="alert">{error.message}</p><Link className="mt-4 inline-block text-blue-700" href="/app/reports/monthly">Reset report filters</Link></div>;
  throw error;
 }
 const sapMode = query.get("mode") === "SAP_EXPORTED";
 const hoursLabel = sapMode ? "SAP-exported hours" : "Approved hours";
 const cards: [string, string | number, boolean][] = [
  ["Active Employees", summary.activeEmployeeCount, false], ["Missing Timesheets", summary.noTimesheetCount, true],
  ["Draft", summary.draftCount, true], ["Pending Approval", summary.pendingApprovalCount, true], ["Rejected", summary.rejectedCount, true],
  ["Approved", summary.approvedCount, false], ["SAP Not Exported", summary.sapNotExportedCount, true],
  ["SAP Queued", summary.sapQueuedCount, false], ["SAP Processing", summary.sapProcessingCount, false], ["SAP Succeeded", summary.sapSucceededCount, false],
  ["SAP Failed", summary.sapFailedCount, true], ["SAP Unknown", summary.sapUnknownCount, true],
  ["Total Recorded Hours", summary.totalRecordedHours, false], ["Total Approved Hours", summary.totalApprovedHours, false],
  ["Total SAP Exported Hours", summary.totalSapExportedHours, false],
 ];
 return <div className="space-y-8">
  <header><h1 className="text-3xl font-bold">Monthly Finance reports</h1>
   <p className="mt-3 text-slate-600">Operational status for currently active employees. Approved hours and SAP-exported hours are separate views.</p></header>
  <form action="/app/reports/monthly" method="get" className="grid gap-4 rounded-xl border border-slate-200 bg-white p-5 sm:grid-cols-2 lg:grid-cols-4">
   <label className="grid gap-2 text-sm font-semibold">Year<input className="field" type="number" min="1" max="9999" required name="year" defaultValue={query.get("year")!} /></label>
   <label className="grid gap-2 text-sm font-semibold">Month<select aria-label="Month" className="field" name="month" defaultValue={query.get("month")!}>{Array.from({ length: 12 }, (_, i) => <option key={i + 1} value={i + 1}>{new Date(Date.UTC(2026, i, 1)).toLocaleString("en-GB", { month: "long", timeZone: "UTC" })}</option>)}</select></label>
   <label className="grid gap-2 text-sm font-semibold">Employee<input className="field" name="employee" maxLength={100} placeholder="Number, name or email" defaultValue={query.get("employee") ?? ""} /></label>
   <label className="grid gap-2 text-sm font-semibold">Project<input className="field" name="project" maxLength={100} placeholder="Code or name" defaultValue={query.get("project") ?? ""} /></label>
   <label className="grid gap-2 text-sm font-semibold">Timesheet state<select aria-label="Timesheet state" className="field" name="timesheetStatus" defaultValue={query.get("timesheetStatus") ?? ""}><option value="">All states</option>{states.map(s => <option key={s} value={s}>{label(s)}</option>)}</select></label>
   <label className="grid gap-2 text-sm font-semibold">SAP state<select aria-label="SAP state" className="field" name="sapStatus" defaultValue={query.get("sapStatus") ?? ""}><option value="">All states</option>{sapStates.map(s => <option key={s} value={s}>{label(s)}</option>)}</select></label>
   <label className="grid gap-2 text-sm font-semibold">Hours report mode<select aria-label="Hours report mode" className="field" name="mode" defaultValue={query.get("mode") ?? "APPROVED"}><option value="APPROVED">Approved hours</option><option value="SAP_EXPORTED">SAP-exported hours</option></select></label>
   <div className="flex flex-wrap items-end gap-3"><button className="button">Apply filters</button><Link className="py-3 text-sm font-semibold text-blue-700" href="/app/reports/monthly">Reset</Link></div>
  </form>
  <section aria-label="Monthly summary">
   <h2 className="text-xl font-semibold">Operational summary · {summary.year}-{String(summary.month).padStart(2, "0")}</h2>
   <p className="mt-2 text-sm text-slate-600">Counts and whole-timesheet hours follow the employee, project and status filters. The project filter selects employees with recorded entries; hours mode applies to the project breakdown and detailed downloads below.</p>
   <div className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-5">{cards.map(([label, value, exception]) => <div key={label} className={`min-w-0 rounded-xl border p-4 ${exception && Number(value) > 0 ? "border-amber-200 bg-amber-50" : "border-slate-200 bg-white"}`}><p className="text-sm text-slate-600">{label}</p><p className="mt-2 break-words text-2xl font-bold">{value}</p></div>)}</div>
  </section>
  <section aria-label="Employee reports" className="rounded-xl border border-slate-200 bg-white p-5">
   <h2 className="text-xl font-semibold">Employees</h2>
   <div className="mt-4 max-w-full overflow-x-auto" tabIndex={0} role="region" aria-label="Scrollable employee report"><table className="w-full text-left text-sm"><thead><tr>{["Employee", "Manager", "Timesheet Status", "Total Hours", "Submitted", "SAP Status"].map(h => <th className="whitespace-nowrap p-3" key={h}>{h}</th>)}</tr></thead>
    <tbody>{employees.content.map(e => <tr className="border-t border-slate-100" key={e.employeeId}>
     <td className="p-3"><p className="font-semibold">{e.employeeName}</p><p>{e.employeeNumber}</p><p className="text-slate-500">{e.email}</p></td>
     <td className="p-3">{e.managerName ?? "—"}</td><td className="p-3"><p>{e.timesheetState}</p>{e.latestApprovalDecision && <p className="mt-1 text-xs text-slate-500">Last decision: {e.latestApprovalDecision} · {eventTime(e.latestApprovalTimestamp!)}</p>}</td>
     <td className="p-3">{e.totalHours}</td><td className="p-3">{e.submittedAt ? eventTime(e.submittedAt) : "—"}</td><td className="p-3"><p>{e.sapState}</p>{e.sapExternalReference && <p className="mt-1 break-all text-xs">{e.sapExternalReference}</p>}</td>
    </tr>)}</tbody></table></div>
   {!employees.content.length && <p className="mt-4 text-slate-600">No employees match these filters.</p>}
   <Pager data={employees} name="page" query={links} />
  </section>
  <section aria-label="Project totals" className="rounded-xl border border-slate-200 bg-white p-5">
   <h2 className="text-xl font-semibold">Project totals · {hoursLabel}</h2>
   <div className="mt-4 grid gap-3">{projects.content.map(p => <div key={p.projectId} className="flex flex-wrap justify-between gap-3 border-b border-slate-100 py-3"><div><p className="font-semibold">{p.projectCode}</p><p>{p.projectName}</p></div><p>{p.totalHours} hours · {p.employeeCount} employees</p></div>)}</div>
   {!projects.content.length && <p className="mt-4 text-slate-600">No {hoursLabel.toLowerCase()} match these filters.</p>}<Pager data={projects} name="projectPage" query={links} />
  </section>
  <section aria-label="Employee project breakdown" className="rounded-xl border border-slate-200 bg-white p-5">
   <h2 className="text-xl font-semibold">Employee / project breakdown · {hoursLabel}</h2>
   <div className="mt-4 grid gap-3">{breakdown.content.map(p => <div key={p.employeeId + p.projectId} className="flex flex-wrap justify-between gap-3 border-b border-slate-100 py-3"><div><p className="font-semibold">{p.employeeName} · {p.employeeNumber}</p><p>{p.projectCode} · {p.projectName}</p></div><p>{p.totalHours} hours</p></div>)}</div>
   {!breakdown.content.length && <p className="mt-4 text-slate-600">No {hoursLabel.toLowerCase()} match these filters.</p>}<Pager data={breakdown} name="breakdownPage" query={links} />
  </section>
  <section aria-label="Report downloads" className="rounded-xl border border-slate-200 bg-white p-5">
   <h2 className="text-xl font-semibold">Download {hoursLabel}</h2>
   <p className="mt-2 text-sm text-slate-600">CSV contains entry details for the selected hours mode. Excel also includes operational Summary and Employees sheets; only its project and detail sheets follow the hours mode. Downloads include all matching rows within the configured limit, not just this page. UI event times use Europe/Athens. Download timestamps remain UTC; work dates are calendar dates.</p>
   <ReportDownloads query={query.toString()} />
  </section>
  <p className="text-sm text-slate-500">Reports reflect local database state at request time. SAP status may change afterward. No payroll or salary calculation is included.</p>
 </div>;
}
