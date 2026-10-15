import Link from "next/link";
import { BackendError, backendRequest } from "@/lib/backend";
import type { Timesheet } from "@/lib/timesheet-view";
import { label } from "@/lib/display";
export default async function CurrentMonth({ date }: { date: string }) {
 const [year, month] = date.split("-"); const path = `/timesheets/${year}/${Number(month)}`;
 let sheet: Timesheet | undefined; let message = "Not started";
 try { sheet = await backendRequest<Timesheet>("/api/v1"+path); }
 catch (error) { if (error instanceof BackendError) { if (error.code !== "TIMESHEET_NOT_FOUND") message = error.message; } else throw error; }
 return <section className="mt-5 rounded-xl border border-slate-200 bg-white p-6">
  <div className="flex flex-wrap items-center justify-between gap-4"><div><h2 className="font-semibold">Current month</h2>
   <p className="mt-2 text-slate-600">{sheet ? `${label(sheet.status)} · ${sheet.totalHours} hours` : message}</p></div>
   <Link className="button" href={"/app"+path}>Open timesheet</Link>
  </div>
 </section>;
}
