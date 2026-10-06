import Link from "next/link";
import { BackendError, backendRequest, currentUser } from "@/lib/backend";
import WorklistRefresh from "./worklist-refresh";
import { utc } from "@/lib/sap-view";
import type { ExportPage } from "@/lib/sap-view";
import { monthLabel } from "@/lib/timesheet-view";
export default async function SapPage({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
 const user = await currentUser();
 if (!["ADMIN", "FINANCE"].includes(user.role)) return <p>You do not have access to SAP export commands.</p>;
 const query = await searchParams;
 const page = Math.max(0, Number.parseInt(query.page ?? "0") || 0);
 let data: ExportPage;
 try { data = await backendRequest<ExportPage>(`/api/v1/sap/timesheets?page=${page}&size=20`); }
 catch (e) { if (e instanceof BackendError) return <p role="alert">{e.message}</p>; throw e; }
 return <>
  <WorklistRefresh active={data.content.some(item => item.export?.job.status === "PENDING" || item.export?.job.status === "PROCESSING")} />
  <h1 className="text-3xl font-bold">SAP exports</h1>
  <p className="mt-3 text-slate-600">Queue approved timesheets for export. The worker processes requests and reconciles uncertain outcomes.</p>
  <div className="mt-7 grid gap-4">
   {data.content.map(item => <Link key={item.timesheet.id} href={`/app/sap/${item.timesheet.id}`} className="rounded-xl border border-slate-200 bg-white p-5 hover:border-blue-500">
    <p className="font-semibold">{item.employee.displayName} · {item.employee.employeeNumber}</p>
    <p className="mt-2">{monthLabel(item.timesheet.year, item.timesheet.month)} · {item.timesheet.totalHours} hours</p>
    <p className="mt-2 text-sm font-semibold text-blue-800">{item.export?.status ?? "Not exported"}</p>
    {item.export && <div className="mt-2 text-sm"><p>Attempts: {item.export.job.attemptCount}</p>{item.export.job.lastAttemptAt && <p>Last: {utc(item.export.job.lastAttemptAt)}</p>}{item.export.job.nextAttemptAt && <p>Next {item.export.job.operation.toLowerCase()}: {utc(item.export.job.nextAttemptAt)}</p>}{item.export.externalReference && <p className="break-all">{item.export.externalReference}</p>}{item.export.failureMessage && <p>{item.export.failureMessage}</p>}</div>}
   </Link>)}
   {!data.content.length && <p>No approved timesheets are available.</p>}
  </div>
  <nav aria-label="Export pages" className="mt-6 flex gap-5">
   {page > 0 && <Link href={`/app/sap?page=${page - 1}`}>Previous</Link>}
   {page + 1 < data.totalPages && <Link href={`/app/sap?page=${page + 1}`}>Next</Link>}
  </nav>
 </>;
}
