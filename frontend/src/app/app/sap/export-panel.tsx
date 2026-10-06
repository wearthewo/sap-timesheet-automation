"use client";
import { useEffect, useState, useTransition } from "react";
import { utc, type SapExport } from "@/lib/sap-view";
import { exportTimesheet, refreshExport, retryExport, reconcileExport } from "./actions";
export default function ExportPanel({ id, version, initial, canExport = false }: { id: string; version: number; initial: SapExport | null; canExport?: boolean }) {
 const [state, setState] = useState(initial);
 const [error, setError] = useState<string>();
 const [pending, startTransition] = useTransition();
 const [attempted, setAttempted] = useState(false);
 const active = state?.job.status === "PENDING" || state?.job.status === "PROCESSING";
 useEffect(() => {
  if (!active || pending) return;
  let cancelled = false; let timer: ReturnType<typeof setTimeout>; let failures = 0;
  async function poll() {
   if (cancelled) return;
   if (document.hidden) { timer = setTimeout(poll, 15000); return; }
   try {
    const result = await refreshExport(id);
    if (cancelled) return;
    if (result.data) {
     setState(result.data); setError(undefined); failures = 0;
     if (result.data.job.status === "FAILED" || result.data.job.status === "COMPLETED") return;
    } else { failures++; setError(result.error); }
   } catch { if (cancelled) return; failures++; setError("Status could not be refreshed. Use Refresh export status."); }
   if (!cancelled && failures < 3) timer = setTimeout(poll, 5000);
  }
  timer = setTimeout(poll, 5000);
  return () => { cancelled = true; clearTimeout(timer); };
 }, [id, active, pending]);
 function act(action: "export" | "retry" | "reconcile" | "refresh") {
  if (action === "export") setAttempted(true);
  setError(undefined);
  startTransition(async () => {
   const result = action === "export" ? await exportTimesheet(id, version) : action === "retry" ? await retryExport(id) : action === "reconcile" ? await reconcileExport(id) : await refreshExport(id);
   if (result.data) setState(result.data);
   else setError(result.error);
  });
 }
 return <section aria-label="SAP export" className="mt-8 rounded-xl border border-slate-200 bg-white p-5">
  <h2 className="text-xl font-semibold">SAP export</h2>
  <p className="mt-3 font-semibold" role="status">{state?.status ?? (attempted ? "Check queue status" : "Not exported")}</p>
  {state?.externalReference && <p className="mt-2 break-all">SAP reference: <strong>{state.externalReference}</strong></p>}
  {state?.exportedAt && <p className="mt-2 text-sm text-slate-600">Exported <time dateTime={state.exportedAt}>{utc(state.exportedAt)}</time></p>}
  {state && <div className="mt-3 space-y-2 text-sm">
   <p>Export attempts: <strong data-testid="export-attempt-count">{state.job.attemptCount}</strong></p>
   {state.job.lastAttemptAt && <p>Last operation: {utc(state.job.lastAttemptAt)}</p>}
   {state.job.nextAttemptAt && <p data-testid="next-sap-attempt">{state.job.operation === "RECONCILE" ? "Next reconciliation" : "Next export attempt"}: {utc(state.job.nextAttemptAt)}</p>}
   {state.job.status === "PROCESSING" && <p>{state.job.operation === "RECONCILE" ? "Checking SAP for the existing record…" : "Export in progress…"}</p>}
  </div>}
  {state?.failureMessage && <p role="alert" className="mt-3 rounded-lg bg-amber-50 p-3 text-amber-950">{state.failureMessage}</p>}
  {state?.status === "UNKNOWN" && <p className="mt-3 text-sm">SAP may have accepted this timesheet. Reconciliation checks the existing identity before another export.</p>}
  {state?.status === "FAILED" && !state.job.retryEligible && <p className="mt-3 text-sm">This failure is not eligible for retry.</p>}
  {error && <p role="alert" className="mt-3 text-red-700">{error}</p>}
  {canExport && <div className="mt-4 flex flex-wrap gap-3">
   {!state && !attempted && <button className="button" disabled={pending} onClick={() => act("export")}>Export to SAP</button>}
   {state?.job.retryEligible && <button className="button" disabled={pending} onClick={() => act("retry")}>Retry export</button>}
   {state?.job.reconcileEligible && <button className="button" disabled={pending} onClick={() => act("reconcile")}>Reconcile export</button>}
   <button className="rounded-lg border border-slate-300 px-4 py-2 font-semibold disabled:opacity-50" disabled={pending} onClick={() => act("refresh")}>Refresh export status</button>
   {pending && <span className="self-center text-sm">Updating export queue…</span>}
  </div>}
  {!!state?.attempts.length && <section aria-label="Export history" className="mt-6 border-t border-slate-200 pt-4">
   <h3 className="font-semibold">Recent operation history</h3>
   <ol className="mt-3 grid gap-3">{state.attempts.map(attempt => <li key={attempt.id} className="rounded-lg bg-slate-50 p-3 text-sm">
    <p className="font-semibold">#{attempt.attemptNumber} {attempt.operation} · {attempt.outcome}</p>
    <p>{utc(attempt.finishedAt)} · {attempt.durationMs} ms</p>
    {attempt.safeErrorCode && <p>{attempt.safeErrorCode}</p>}
   </li>)}</ol>
   {state.job.executionCount > 20 && <p className="mt-2 text-sm">Showing the latest 20 completed operations.</p>}
  </section>}
 </section>;
}
