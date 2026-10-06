import Link from "next/link";
import { notFound } from "next/navigation";
import { BackendError, backendRequest, currentUser } from "@/lib/backend";
import type { ExportDetail } from "@/lib/sap-view";
import { monthLabel } from "@/lib/timesheet-view";
import ExportPanel from "../export-panel";
export default async function SapDetail({ params }: { params: Promise<{ id: string }> }) {
 const user = await currentUser();
 if (!["ADMIN", "FINANCE"].includes(user.role)) return <p>You do not have access to SAP export commands.</p>;
 const { id } = await params;
 if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
 let detail: ExportDetail;
 try { detail = await backendRequest<ExportDetail>(`/api/v1/sap/timesheets/${id}`); }
 catch (e) { if (e instanceof BackendError) return <p role="alert">{e.message}</p>; throw e; }
 const sheet = detail.timesheet;
 return <>
  <Link href="/app/sap" className="text-blue-700">Back to SAP exports</Link>
  <h1 className="mt-5 text-3xl font-bold">{monthLabel(sheet.year, sheet.month)}</h1>
  <p className="mt-3">{detail.employee.displayName} · {detail.employee.employeeNumber}</p>
  <p className="mt-3 font-semibold">{sheet.status} · {sheet.totalHours} hours</p>
  <div className="mt-6 grid gap-3">
   {sheet.entries.map(entry => <article key={entry.id} className="rounded-lg border border-slate-200 bg-white p-4">
    <p className="font-semibold">{entry.workDate} · {entry.project.code} · {entry.hours} hours</p>
    <p className="mt-1 break-words">{entry.description}</p>
   </article>)}
  </div>
  <div className="mt-5 flex flex-wrap gap-4">{sheet.dailyTotals.map(day => <p key={day.workDate} className="text-sm">{day.workDate}: <strong>{day.hours} hours</strong></p>)}</div>
  {sheet.status === "APPROVED" && <ExportPanel id={id} version={sheet.version} initial={detail.export} canExport />}
 </>;
}
