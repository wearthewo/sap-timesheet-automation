"use client";
import Link from "next/link";
import { useState,useTransition } from "react";
import type { ApprovalReview } from "@/lib/approval-view";
import { monthLabel } from "@/lib/timesheet-view";
import { calendarDate, eventTime, label } from "@/lib/display";
import ApprovalHistory from "../timesheets/approval-history";
import { decideTimesheet } from "./actions";
export default function Review({initial}: {initial:ApprovalReview}) {
 const [review,setReview]=useState(initial);
 const [decision,setDecision]=useState<"approve"|"reject">();
 const [comment,setComment]=useState("");
 const [error,setError]=useState("");
 const [pending,startTransition]=useTransition();
 const sheet=review.timesheet;
 return <div className="space-y-6">
  <Link href="/app/approvals" className="font-semibold text-blue-700">← Pending approvals</Link>
  <div><h1 className="text-3xl font-bold">{monthLabel(sheet.year,sheet.month)}</h1><p className="mt-2 break-words text-lg">{review.employee.displayName} · {review.employee.employeeNumber}</p></div>
  <section className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-6">
   <div><span className="status" data-state={sheet.status}>{sheet.status}</span><p className="mt-2 font-semibold">{label(sheet.status)}</p>{sheet.submittedAt && <p className="mt-2 text-sm text-slate-500">Submitted {eventTime(sheet.submittedAt)}</p>}</div>
   <p className="text-2xl font-bold">{sheet.totalHours} hours</p>
  </section>
  {error && <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-900">{error}<button className="ml-3 rounded border px-3 py-2 font-semibold" onClick={()=>window.location.reload()}>Reload review</button></div>}
  <section aria-label="Review entries" className="space-y-4">
   <h2 className="text-lg font-semibold">Time entries</h2>
   {sheet.dailyTotals.map(day=><section key={day.workDate} className="overflow-hidden rounded-xl border border-slate-200 bg-white">
    <div className="flex flex-wrap justify-between gap-3 bg-slate-100 p-4 font-semibold"><h3><time dateTime={day.workDate}>{calendarDate(day.workDate)}</time></h3><p>{day.hours} hours total</p></div>
    <ul className="divide-y divide-slate-100">{sheet.entries.filter(e=>e.workDate===day.workDate).map(entry=><li key={entry.id} className="flex flex-wrap justify-between gap-4 p-5">
     <div className="min-w-0 flex-1 basis-48"><p className="break-words font-semibold">{entry.project.code} · {entry.project.name}</p><p className="mt-1 break-words text-sm text-slate-600">{entry.description || "No description"}</p></div><p>{entry.hours} h</p>
    </li>)}</ul>
   </section>)}
  </section>
  {sheet.status==="PENDING_APPROVAL" && <section aria-label="Manager decision" className="rounded-xl border border-blue-200 bg-blue-50 p-6">
   <h2 className="mb-4 text-lg font-semibold">Review decision</h2>
   {!decision ? <div className="flex flex-wrap gap-4"><button className="button" onClick={()=>setDecision("approve")}>Approve timesheet</button><button className="rounded-lg border border-red-300 bg-white px-4 py-3 font-semibold text-red-800" onClick={()=>setDecision("reject")}>Reject timesheet</button></div>
    : <form className="space-y-4" onSubmit={event=>{
     event.preventDefault();setError("");
     startTransition(async()=>{
      const result=await decideTimesheet(sheet.id,sheet.version,decision,comment);
      if(result.error!==undefined) {setError(result.code === "CONCURRENT_MODIFICATION" ? result.error + " Copy your comment before reloading. No decision was overwritten." : result.error);return;}
      setReview(result.data);setDecision(undefined);setComment("");
     });
    }}>
     <p>{decision==="approve" ? "Approve this timesheet? It will remain read-only for the employee." : "Explain what the employee needs to correct."}</p>
     <label className="grid gap-2 text-sm font-semibold">{decision==="reject" ? "Rejection comment (required)" : "Approval comment (optional)"}<textarea className="field" rows={3} required={decision==="reject"} maxLength={2000} value={comment} onChange={event=>setComment(event.target.value)} /></label>
     <div className="flex flex-wrap gap-4"><button className="button" disabled={pending}>{pending ? "Saving…" : decision==="approve" ? "Confirm approval" : "Confirm rejection"}</button><button type="button" disabled={pending} onClick={()=>setDecision(undefined)}>Cancel</button></div>
    </form>}
  </section>}
  <ApprovalHistory history={sheet.approvalHistory}/>
 </div>;
}
