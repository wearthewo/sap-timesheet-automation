import Link from "next/link";
import { backendRequest } from "@/lib/backend";

type Notification = { id: string; type: string; subject: string; body: string; status: string; createdAt: string; sentAt: string | null };
type History = { content: Notification[]; totalElements: number; totalPages: number };
const timestamp = (value: string) => new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "short", timeZone: "UTC" }).format(new Date(value)) + " UTC";

export default async function Notifications({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
 const raw = (await searchParams).page ?? "0";
 const page = /^\d{1,6}$/.test(raw) ? Number(raw) : 0;
 const history = await backendRequest<History>(`/api/v1/notifications?page=${page}&size=20`);
 return <>
  <h1 className="text-3xl font-bold tracking-tight">Notifications</h1>
  <p className="mt-3 text-slate-600">Your email notification history. Sent means accepted by the email server; it does not mean read.</p>
  <div className="mt-8 space-y-4">
   {history.content.map(item => <article key={item.id} className="min-w-0 rounded-xl border border-slate-200 bg-white p-5 sm:p-6">
    <div className="flex flex-wrap items-start justify-between gap-3">
     <h2 className="min-w-0 break-words text-lg font-semibold">{item.subject}</h2>
     <span className="rounded bg-slate-100 px-3 py-1 text-xs font-semibold">{item.status}</span>
    </div>
    <p className="mt-2 text-xs text-slate-500">{item.type.replaceAll("_", " ")} · Created {timestamp(item.createdAt)}{item.sentAt && ` · Sent ${timestamp(item.sentAt)}`}</p>
    {item.status === "CANCELLED" && <p className="mt-3 text-sm text-slate-600">This reminder was no longer needed when delivery was due.</p>}
    <details className="mt-4">
     <summary className="cursor-pointer font-medium text-blue-700">Message</summary>
     <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-slate-700">{item.body}</p>
    </details>
   </article>)}
  </div>
  {history.content.length === 0 && <p className="mt-8 rounded-xl border border-dashed border-slate-300 bg-white p-8">No notifications on this page.</p>}
  <nav aria-label="Notification pagination" className="mt-7 flex flex-wrap gap-4">
   {page > 0 && <Link className="button" href={`/app/notifications?page=${page - 1}`}>Previous</Link>}
   {page + 1 < history.totalPages && <Link className="button" href={`/app/notifications?page=${page + 1}`}>Next</Link>}
  </nav>
 </>;
}
