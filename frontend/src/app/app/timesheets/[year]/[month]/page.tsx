import Link from "next/link";
import { notFound } from "next/navigation";
import { BackendError, backendRequest } from "@/lib/backend";
import { monthLabel, type Timesheet } from "@/lib/timesheet-view";
import TimesheetEditor from "../../timesheet-editor";
export default async function Month({ params }: { params: Promise<{ year: string; month: string }> }) {
 const route = await params; const year = Number(route.year); const month = Number(route.month);
 if (!Number.isInteger(year) || year < 1 || year > 9999 || !Number.isInteger(month) || month < 1 || month > 12) notFound();
 let sheet: Timesheet | null = null; let error: string | undefined;
 try { sheet = await backendRequest<Timesheet>(`/api/v1/timesheets/${year}/${month}`); }
 catch (e) { if (e instanceof BackendError) { if (e.code !== "TIMESHEET_NOT_FOUND") error = e.message; } else throw e; }
 return <>
  <Link href="/app/timesheets" className="text-sm font-semibold text-blue-700">← All timesheets</Link>
  <h1 className="mt-5 mb-7 text-3xl font-bold">{monthLabel(year,month)}</h1>
  {error ? <p role="alert">{error}</p> : <TimesheetEditor key={sheet?.id ?? `${year}-${month}`} initial={sheet} year={year} month={month} />}
 </>;
}
