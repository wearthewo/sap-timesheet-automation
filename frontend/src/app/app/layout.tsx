import Link from "next/link";
import { currentUser } from "@/lib/backend";
export default async function AppLayout({ children }: { children: React.ReactNode }) {
 const user = await currentUser();
 return <div className="min-h-screen">
  <a href="#main" className="sr-only focus:not-sr-only">Skip to content</a>
  <header className="border-b border-slate-200 bg-white">
   <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-8 gap-y-4 px-5 py-5">
    <Link href="/app" className="text-lg font-bold tracking-widest text-blue-900">WORKFORCE</Link>
    <nav aria-label="Main navigation" className="flex flex-wrap gap-5 text-sm font-semibold">
     <Link href="/app" className="rounded px-1 py-2 hover:text-blue-700">Overview</Link>
     <Link href="/app/timesheets" className="rounded px-1 py-2 hover:text-blue-700">Timesheets</Link>
     <Link href="/app/projects" className="rounded px-1 py-2 hover:text-blue-700">My projects</Link>
     {user.role === "MANAGER" && <Link href="/app/approvals" className="rounded px-1 py-2 hover:text-blue-700">Approvals</Link>}
     {["ADMIN", "FINANCE"].includes(user.role) && <Link href="/app/sap" className="rounded px-1 py-2 hover:text-blue-700">SAP exports</Link>}
    </nav>
    <div className="flex w-full min-w-0 items-center justify-between gap-4 sm:ml-auto sm:w-auto">
     <div className="min-w-0 text-sm"><p className="break-words font-semibold">{user.displayName}</p><p className="text-xs text-slate-500">{user.role}</p></div>
     <form action="/api/logout" method="post"><button className="shrink-0 rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold hover:bg-slate-100">Log out</button></form>
    </div>
   </div>
  </header>
  <main id="main" className="mx-auto max-w-6xl px-5 py-10 sm:py-14">{children}</main>
 </div>;
}
