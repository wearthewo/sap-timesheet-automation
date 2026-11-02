import Link from "next/link";
import { BackendError, backendRequest } from "@/lib/backend";
import { monthLabel, type TimesheetPage } from "@/lib/timesheet-view";
import { businessDate, eventTime } from "@/lib/display";
import MonthPicker from "./month-picker";
export default async function Timesheets({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
 const query = await searchParams;
 const page = Math.max(0, Math.min(100000, Number(query.page) || 0));
 const today = businessDate();
 let data: TimesheetPage | undefined; let error: string | undefined;
 try { data = await backendRequest<TimesheetPage>(`/api/v1/timesheets?page=${Math.floor(page)}&size=12`); }
 catch (e) { if (e instanceof BackendError) error = e.message; else throw e; }
 return <>
  <p className="mb-3 text-sm font-semibold text-blue-700">YOUR TIME</p>
  <h1 className="text-3xl font-bold">Timesheets</h1>
  <p className="mt-3 mb-8 text-slate-600">Save your work as you go. Submit a month when it is complete.</p>
  <MonthPicker current={today.slice(0,7)} />
  {error && <p role="alert" className="mt-6 text-red-800">{error}</p>}
  {data && <section className="mt-9 space-y-3" aria-label="Your monthly timesheets">
   {!data.content.length && <p className="rounded-xl border border-slate-200 bg-white p-6">No timesheets yet. Open a month to create your first draft.</p>}
   {data.content.map(sheet => <Link key={sheet.id} href={`/app/timesheets/${sheet.year}/${sheet.month}`} className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-5 hover:border-blue-400">
    <div><h2 className="font-semibold">{monthLabel(sheet.year,sheet.month)}</h2><p className="mt-1 text-sm text-slate-600">{sheet.submittedAt ? `Submitted ${eventTime(sheet.submittedAt)}` : "Draft · continue editing"}</p></div>
    <div className="flex items-center gap-5"><span className="status" data-state={sheet.status}>{sheet.status}</span><span className="font-semibold">{sheet.totalHours} h</span><span aria-hidden="true">→</span></div>
   </Link>)}
   <nav aria-label="Timesheet pages" className="flex justify-between pt-4">
    {data.page > 0 ? <Link className="button" href={`?page=${data.page-1}`}>Newer months</Link> : <span />}
    {data.page+1 < data.totalPages && <Link className="button" href={`?page=${data.page+1}`}>Older months</Link>}
   </nav>
  </section>}
 </>;
}
