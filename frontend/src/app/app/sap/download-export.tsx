"use client";
import { useState } from "react";
const xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
export default function DownloadExport({ id }: { id: string }) {
 const [busy, setBusy] = useState(false);
 const [error, setError] = useState<string>();
 async function download() {
  if (busy) return;
  setBusy(true); setError(undefined);
  try {
   const response = await fetch(`/api/sap/timesheets/${id}/export.xlsx`, { cache: "no-store", signal: AbortSignal.timeout(35000) });
   if (response.redirected) throw new Error("Your session has expired. Sign in again to download.");
   if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(typeof body?.message === "string" ? body.message : "The SAP export download is unavailable. Please try again.");
   }
   if (response.headers.get("content-type") !== xlsx) throw new Error("The server did not return an Excel workbook.");
   const filename = response.headers.get("content-disposition")?.match(/filename="([A-Za-z0-9_-]+\.xlsx)"/)?.[1];
   if (!filename) throw new Error("The export filename could not be verified.");
   const url = URL.createObjectURL(await response.blob());
   const link = document.createElement("a"); link.href = url; link.download = filename;
   document.body.appendChild(link); link.click(); link.remove();
   setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (failure) {
   setError(failure instanceof Error && failure.name !== "TimeoutError" ? failure.message : "The download timed out. Please try again.");
  } finally { setBusy(false); }
 }
 return <div className="mt-4">
  <button className="button" disabled={busy} onClick={download}>{busy ? "Preparing Excel…" : "Download SAP Excel"}</button>
  {error && <p role="alert" className="mt-3 text-red-700">{error}</p>}
 </div>;
}
