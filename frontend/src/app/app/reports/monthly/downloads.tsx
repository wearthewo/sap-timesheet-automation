"use client";
import { useState } from "react";
export default function ReportDownloads({ query }: { query: string }) {
 const [busy, setBusy] = useState(false);
 const [error, setError] = useState("");
 async function download(format: "csv" | "xlsx") {
  if (busy) return;
  setBusy(true); setError("");
  try {
   const response = await fetch(`/api/reports/${format}?${query}`, { cache: "no-store", signal: AbortSignal.timeout(35000) });
   if (response.redirected) throw new Error("Your session has expired. Sign in again to download.");
   if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(typeof body?.message === "string" ? body.message : "The report could not be downloaded. Please try again.");
   }
   const mime = response.headers.get("content-type")?.split(";")[0];
   if (mime !== (format === "csv" ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) throw new Error("The server did not return the requested report file.");
   const filename = response.headers.get("content-disposition")?.match(/filename="([A-Za-z0-9_-]+\.(?:csv|xlsx))"/)?.[1];
   if (!filename || !filename.endsWith("." + format)) throw new Error("The report filename could not be verified.");
   const url = URL.createObjectURL(await response.blob());
   const link = document.createElement("a"); link.href=url; link.download=filename;
   document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url),1000);
  } catch (failure) {
   setError(failure instanceof Error && failure.name !== "TimeoutError" ? failure.message : "The report download timed out. Narrow your filters and try again.");
  } finally { setBusy(false); }
 }
 return <div className="mt-4"><div className="flex flex-wrap gap-3">{(["csv","xlsx"] as const).map(format => <a key={format} className="button" href={`/api/reports/${format}?${query}`} aria-disabled={busy} onClick={event => { event.preventDefault(); void download(format); }}>Download {format === "csv" ? "CSV" : "Excel"}</a>)}</div>
  {busy && <p role="status" className="mt-3 text-sm">Preparing your report…</p>}{error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-red-800">{error}</p>}
 </div>;
}
