import Link from "next/link";
import { backendRequest, BackendError, currentUser } from "@/lib/backend";
import { monthLabel } from "@/lib/timesheet-view";
import type { PendingPage } from "@/lib/approval-view";
export default async function Approvals({searchParams}: {searchParams: Promise<{page?: string}>}) {
 if ((await currentUser()).role !== "MANAGER") return <p role="alert">Manager access is required.</p>;
 const query=await searchParams;
 const page=Math.floor(Math.max(0,Math.min(100000,Number(query.page)||0)));
 let data: PendingPage;
 try {data=await backendRequest<PendingPage>(`/api/v1/approvals/pending?page=${page}&size=12`);}
 catch(error) {if(error instanceof BackendError) return <p role="alert">{error.message}</p>;throw error;}
 return <>
  <p className="mb-3 text-sm font-semibold text-blue-700">YOUR TEAM</p>
  <h1 className="text-3xl font-bold">Pending approvals</h1>
  <p className="mt-3 mb-8 text-slate-600">{data.totalElements} timesheets awaiting your review. Only your direct reports are shown.</p>
  <section aria-label="Pending timesheets" className="space-y-3">
   {!data.content.length && <p className="rounded-xl border border-slate-200 bg-white p-6">No pending timesheets.</p>}
   {data.content.map(({employee,timesheet:s})=><Link key={s.id} href={`/app/approvals/${s.id}`} className="flex flex-wrap justify-between gap-4 rounded-xl border border-slate-200 bg-white p-5 hover:border-blue-400">
    <div><h2 className="break-words font-semibold">{employee.displayName}</h2><p className="text-sm text-slate-600">{employee.employeeNumber} · {monthLabel(s.year,s.month)}</p>
     <p className="mt-1 text-xs text-slate-500">Submitted {new Date(s.submittedAt!).toLocaleString("en-GB",{timeZone:"UTC"})} UTC</p></div>
    <p className="font-semibold">{s.totalHours} hours →</p>
   </Link>)}
  </section>
  <nav aria-label="Approval pages" className="mt-5 flex justify-between">
   {data.page>0 ? <Link className="button" href={`?page=${data.page-1}`}>Previous</Link> : <span/>}
   {data.page+1<data.totalPages && <Link className="button" href={`?page=${data.page+1}`}>Next</Link>}
  </nav>
 </>;
}
