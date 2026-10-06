"use client";
import { useState, useTransition } from "react";
import type { SapExport } from "@/lib/sap-view";
import { exportTimesheet, refreshExport } from "./actions";
export default function ExportPanel({ id, version, initial, canExport = false }: { id: string; version: number; initial: SapExport | null; canExport?: boolean }) {
 const [state, setState] = useState(initial);
 const [error, setError] = useState<string>();
 const [pending, startTransition] = useTransition();
 const [attempted, setAttempted] = useState(false);
 function act(exporting: boolean) {
  if (exporting) setAttempted(true);
  setError(undefined);
  startTransition(async () => {
   const result = exporting ? await exportTimesheet(id, version) : await refreshExport(id);
   if (result.data) setState(result.data);
   else setError(result.error);
  });
 }
 return <section aria-label="SAP export" className="mt-8 rounded-xl border border-slate-200 bg-white p-5">
  <h2 className="text-xl font-semibold">SAP export</h2>
  <p className="mt-3 font-semibold" role="status">{state?.status ?? (attempted ? "Checking export outcome" : "Not exported")}</p>
  {state?.externalReference && <p className="mt-2 break-all">SAP reference: <strong>{state.externalReference}</strong></p>}
  {state?.exportedAt && <p className="mt-2 text-sm text-slate-600">Exported <time dateTime={state.exportedAt}>{new Date(state.exportedAt).toISOString().replace("T", " ").replace("Z", " UTC")}</time></p>}
  {state?.failureMessage && <p role="alert" className="mt-3 rounded-lg bg-amber-50 p-3 text-amber-950">{state.failureMessage}</p>}
  {(state?.status === "UNKNOWN" || state?.status === "PENDING") && <p className="mt-3 text-sm">The outcome needs confirmation. Do not send another export. Contact your administrator for reconciliation.</p>}
  {state?.status === "FAILED" && <p className="mt-3 text-sm">This attempt is recorded. Manual retry is not available yet.</p>}
  {error && <p role="alert" className="mt-3 text-red-700">{error}</p>}
  {canExport && <div className="mt-4 flex flex-wrap gap-3">
   {!state && !attempted && <button className="button" disabled={pending} onClick={() => act(true)}>Export to SAP</button>}
   <button className="rounded-lg border border-slate-300 px-4 py-2 font-semibold disabled:opacity-50" disabled={pending} onClick={() => act(false)}>Refresh export status</button>
   {pending && <span className="self-center text-sm">Contacting the application service…</span>}
  </div>}
 </section>;
}
