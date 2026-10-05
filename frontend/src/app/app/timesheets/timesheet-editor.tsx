"use client";
import { useEffect, useState, useTransition } from "react";

import type { Entry, ProjectOption, Timesheet } from "@/lib/timesheet-view";
import { availableProjects, changeTimesheet, createMonth } from "./actions";

export default function TimesheetEditor({ initial, year, month }: { initial: Timesheet | null; year: number; month: number }) {
 const [sheet,setSheet] = useState(initial);
 const [error,setError] = useState("");
 const [notice,setNotice] = useState("");
 const [confirm,setConfirm] = useState(false);
 const [pending,startTransition] = useTransition();
 const [editing,setEditing] = useState<string>();
 const prefix = `${String(year).padStart(4,"0")}-${String(month).padStart(2,"0")}`;
 const today = new Date().toISOString().slice(0,10);
 const [date,setDate] = useState(today.startsWith(prefix) ? today : prefix+"-01");
 const [project,setProject] = useState("");
 const [hours,setHours] = useState("");
 const [description,setDescription] = useState("");
 const [options,setOptions] = useState<ProjectOption[]>([]);
 const [loading,setLoading] = useState(false);
 const [projectError,setProjectError] = useState("");

 const editable = sheet?.status === "DRAFT";
 const sheetId = sheet?.id;
 useEffect(() => {
  if (!sheetId || !editable || !date) return;
  let cancelled = false;
  availableProjects(sheetId,date).then(result => {
   if (cancelled) return;
   setLoading(false);
   if (result.error !== undefined) { setOptions([]); setProjectError(result.error); }
   else { setOptions(result.data); setProjectError(""); }
  }).catch(() => { if (!cancelled) { setLoading(false); setProjectError("Could not load projects. Reload and try again."); } });
  return () => { cancelled = true; };
 },[sheetId,editable,date]);
 function reset() { setEditing(undefined); setHours(""); setDescription(""); setProject(""); }
 function edit(entry: Entry) {
  setEditing(entry.id); setDate(entry.workDate); setProject(entry.project.id);
  setHours(entry.hours); setDescription(entry.description ?? ""); setError(""); setNotice("");
  if (date !== entry.workDate) { setOptions([]); setLoading(true); }
 }
 function change(operation: "add" | "edit" | "delete" | "submit", entry?: Entry) {
  if (!sheet) return;
  setError(""); setNotice("");
  startTransition(async () => {
   const result = await changeTimesheet(sheet.id,sheet.version,operation,{
    entryId: entry?.id ?? editing, projectId: project, workDate: date, hours, description,
   });
   if (result.error !== undefined) { setError(result.error); return; }
   setSheet(result.data); setConfirm(false);
   if (operation !== "delete" || entry?.id === editing) reset();
   setNotice(operation === "submit" ? "Timesheet submitted. This month is now read-only." : "Draft saved.");
  });
 }
 if (!sheet) return <section className="rounded-xl border border-slate-200 bg-white p-6">
  <p className="mb-5 text-slate-600">This month has not been started. Create a draft to begin recording time.</p>
  {error && <p role="alert" className="mb-4 text-red-800">{error}</p>}
  <button disabled={pending} className="button" onClick={() => startTransition(async () => {
   const result = await createMonth(year,month);
   if (result.error !== undefined) setError(result.error);
   else { setSheet(result.data); setLoading(true); }
  })}>{pending ? "Creating…" : "Create draft"}</button>
  {error && <button className="ml-3 font-semibold text-blue-700" onClick={() => window.location.reload()}>Reload month</button>}
 </section>;
 return <div className="space-y-7">
  <section className="flex flex-wrap items-center justify-between gap-5 rounded-xl border border-slate-200 bg-white p-6">
   <div><span className="status">{sheet.status}</span><p className="mt-3 text-sm text-slate-600">{editable ? "Each saved entry updates your draft." : `Read-only · Submitted ${new Date(sheet.submittedAt!).toLocaleString("en-GB",{timeZone:"UTC"})} UTC`}</p></div>
   <div><p className="text-sm text-slate-500">Monthly total</p><p className="text-3xl font-bold" data-testid="monthly-total">{sheet.totalHours} <span className="text-lg font-normal">hours</span></p></div>
  </section>
  {error && <div role="alert" className="rounded-lg bg-red-50 p-4 text-red-900">{error} <button className="ml-2 font-semibold underline" onClick={() => window.location.reload()}>Reload latest timesheet</button></div>}
  {notice && <p role="status" className="text-green-800">{notice}</p>}
  {editable && <section className="rounded-xl border border-slate-200 bg-white p-6" aria-label="Entry editor">
   <h2 className="mb-4 text-lg font-semibold">{editing ? "Edit entry" : "Add entry"}</h2>
   <form className="grid gap-4 sm:grid-cols-2" onSubmit={e => { e.preventDefault(); change(editing ? "edit" : "add"); }}>
    <label className="grid min-w-0 gap-2 text-sm font-semibold">Work date<input className="field" type="date" required value={date} min={prefix+"-01"} max={prefix+"-"+new Date(Date.UTC(year === 9999 ? 9999 : year,month,0)).getUTCDate()} onChange={e => {setDate(e.target.value); setProject(""); setOptions([]); setLoading(true);}} /></label>
    <label className="grid min-w-0 gap-2 text-sm font-semibold">Project<select className="field" required value={project} disabled={loading} onChange={e => setProject(e.target.value)}>
     <option value="">{loading ? "Loading projects…" : "Choose assigned project"}</option>
     {options.map(p => <option key={p.id} value={p.id}>{p.code} · {p.name}</option>)}
    </select></label>
    {projectError && <p role="alert" className="text-sm text-red-800 sm:col-span-2">{projectError}</p>}
    {!loading && !projectError && options.length === 0 && <p className="text-sm text-slate-600 sm:col-span-2">No active projects are assigned for this date. Choose another date or contact your administrator.</p>}
    <label className="grid gap-2 text-sm font-semibold">Hours<input className="field" type="number" inputMode="decimal" min="0.01" step="0.01" required value={hours} onChange={e => setHours(e.target.value)} placeholder="e.g. 6.50" /></label>
    <label className="grid min-w-0 gap-2 text-sm font-semibold">Description <span className="sr-only">(optional)</span><input className="field" value={description} maxLength={2000} onChange={e => setDescription(e.target.value)} placeholder="Optional" /></label>
    <div className="flex flex-wrap items-center gap-3 sm:col-span-2"><button className="button" disabled={pending || loading}>{pending ? "Saving…" : editing ? "Save changes" : "Save entry"}</button>
     {editing && <button type="button" disabled={pending} onClick={reset} className="font-semibold text-slate-600">Cancel edit</button>}
     <p className="text-xs text-slate-500">Daily limit: {sheet.maxDailyHours} hours across all projects.</p>
    </div>
   </form>
  </section>}
  <section aria-label="Time entries" className="space-y-5">
   <h2 className="text-lg font-semibold">Time entries</h2>
   {!sheet.entries.length && <p className="text-slate-600">No entries yet.</p>}
   {sheet.dailyTotals.map(day => <section key={day.workDate} className="overflow-hidden rounded-xl border border-slate-200 bg-white">
    <div className="flex flex-wrap justify-between gap-2 bg-slate-100 px-5 py-3 text-sm font-semibold"><h3>{day.workDate}</h3><p data-testid="daily-total">{day.hours} hours total</p></div>
    <ul className="divide-y divide-slate-100">{sheet.entries.filter(e => e.workDate === day.workDate).map(entry => <li key={entry.id} className="flex flex-wrap items-center gap-4 p-5">
     <div className="min-w-0 flex-1 basis-48"><p className="break-words font-semibold">{entry.project.code} · {entry.project.name}</p><p className="mt-1 break-words text-sm text-slate-600">{entry.description || "No description"}</p></div>
     <p className="font-semibold">{entry.hours} h</p>
     {editable && <div className="flex gap-3"><button disabled={pending} className="rounded border border-slate-300 px-3 py-2 text-sm font-semibold" onClick={() => edit(entry)}>Edit<span className="sr-only"> {entry.project.code} {entry.workDate}</span></button>
      <button disabled={pending} className="rounded border border-red-200 px-3 py-2 text-sm font-semibold text-red-800" onClick={() => change("delete",entry)}>Delete<span className="sr-only"> {entry.project.code} {entry.workDate}</span></button></div>}
    </li>)}</ul>
   </section>)}
  </section>
  {editable && <section className="rounded-xl border border-blue-200 bg-blue-50 p-6">
   <h2 className="font-semibold">Ready to submit?</h2><p className="mt-2 mb-4 text-sm text-slate-600">Check your entries and totals. Submission makes this month read-only.</p>
   {confirm ? <div className="flex flex-wrap items-center gap-4"><p className="w-full font-semibold">Submit this month? You will no longer be able to edit it.</p><button className="button" disabled={pending} onClick={() => change("submit")}>Confirm submission</button><button disabled={pending} onClick={() => setConfirm(false)}>Cancel</button></div>
    : <button className="button" disabled={pending || sheet.entries.length === 0} onClick={() => setConfirm(true)}>Submit month</button>}
  </section>}
 </div>;
}
