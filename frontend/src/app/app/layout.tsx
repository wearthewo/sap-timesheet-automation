import Link from "next/link";
import { currentUser } from "@/lib/backend";
import Navigation from "./navigation";
export default async function AppLayout({ children }: { children: React.ReactNode }) {
 const user = await currentUser();
 return <div className="workspace">
  <a href="#main" className="skip-link">Skip to content</a>
  <header className="workspace-header">
   <Link href="/app" className="wordmark">WORKFORCE</Link>
   <div className="workspace-identity"><span className="text-xs text-slate-500">Active organization</span><p className="font-semibold break-words">{user.organizationName}</p></div>
   <div className="workspace-account"><div className="min-w-0"><p className="break-words text-sm font-semibold">{user.displayName}</p><p className="text-xs text-slate-500">{user.role} · {user.organizationName}</p></div>
    <form action="/api/logout" method="post"><button className="button-secondary whitespace-nowrap">Log out</button></form>
   </div>
  </header>
  <aside className="workspace-sidebar"><p className="nav-caption">Workspace</p><Navigation role={user.role} /><p className="sidebar-note">Work dates stay as entered.<br />Event times shown in Athens.</p></aside>
  <main id="main" className="workspace-main" tabIndex={-1}>{children}</main>
 </div>;
}
