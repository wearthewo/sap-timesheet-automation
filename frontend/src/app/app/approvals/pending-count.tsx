import Link from "next/link";
import { backendRequest,BackendError } from "@/lib/backend";
import type { PendingPage } from "@/lib/approval-view";
export default async function PendingCount() {
 let data: PendingPage;
 try {data=await backendRequest<PendingPage>("/api/v1/approvals/pending?size=1");}
 catch(error) {if(error instanceof BackendError) return <p role="alert" className="mt-5">{error.message}</p>;throw error;}
 return <section className="mt-5 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-6">
  <div><h2 className="font-semibold">Pending approvals</h2><p className="mt-2 text-3xl font-bold">{data.totalElements}</p></div>
  <Link href="/app/approvals" className="button">Review team timesheets</Link>
 </section>;
}
