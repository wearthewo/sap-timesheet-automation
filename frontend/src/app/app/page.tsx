import PendingCount from "./approvals/pending-count";
import CurrentMonth from "./timesheets/current-month";
import Link from "next/link";
import { currentUser, assignedProjects } from "@/lib/backend";
export default async function Dashboard() {
 const [user, projects] = await Promise.all([currentUser(), assignedProjects()]);
 return <>
  <p className="mb-3 text-sm font-semibold text-blue-700">YOUR WORKSPACE</p>
  <h1 className="break-words text-3xl font-bold tracking-tight sm:text-4xl">Welcome, {user.displayName}</h1>
  <p className="mt-3 text-slate-600">A clear view of your profile and current project access.</p>
  <div className="mt-9 grid gap-5 md:grid-cols-3">
   <section className="min-w-0 rounded-xl border border-slate-200 bg-white p-6 md:col-span-2">
    <h2 className="font-semibold">Employee profile</h2>
    <dl className="mt-5 space-y-4"><div><dt className="text-xs text-slate-500">Name</dt><dd className="break-words">{user.displayName}</dd></div>
     <div><dt className="text-xs text-slate-500">Email</dt><dd className="break-all">{user.email || "Not provided"}</dd></div>
     <div><dt className="text-xs text-slate-500">Application role</dt><dd className="mt-1 inline-block rounded bg-blue-50 px-2 py-1 text-xs font-bold text-blue-900">{user.role}</dd></div></dl>
   </section>
   <section className="rounded-xl border border-slate-200 bg-white p-6">
    <h2 className="font-semibold">Assigned projects</h2><p className="my-5 text-5xl font-bold">{projects.totalElements}</p>
    <p className="mb-6 text-sm text-slate-500">Projects currently available to you.</p>
    <Link href="/app/projects" className="button">View my projects</Link>
   </section>
  </div>
  <CurrentMonth />
  {user.role === "MANAGER" && <PendingCount />}
 </>;
}
