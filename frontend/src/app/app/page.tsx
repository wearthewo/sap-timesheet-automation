import CurrentMonth from "./timesheets/current-month";
import Link from "next/link";
import { currentUser, assignedProjects, backendRequest } from "@/lib/backend";
import { monthLabel } from "@/lib/timesheet-view";
import { eventTime, label } from "@/lib/display";
type Summary = { businessDate: string; timezone: string;
 administration: { activeEmployees: number; activeProjects: number; pendingInvitations: number } | null;
 finance: { awaitingExport: number; queued: number; processing: number; succeeded: number; failed: number; unknown: number } | null;
 management: { pendingApprovals: number; recentDecisions: { timesheetId: string; year: number; month: number; decision: string; decidedAt: string }[] } | null;
 rejectedMonths: { year: number; month: number }[];
};
function Metric({ title, value, href, action }: { title: string; value: number; href: string; action: string }) {
 return <section className="metric-card"><h2>{title}</h2><p className="metric-value">{value}</p><Link href={href}>{action} <span aria-hidden="true">→</span></Link></section>;
}
export default async function Dashboard() {
 const [user, data] = await Promise.all([currentUser(), backendRequest<Summary>("/api/v1/dashboard")]);
 const projects = user.hasEmployeeProfile ? await assignedProjects() : null;
 return <>
  <p className="eyebrow">{user.organizationName} · {label(user.role)} workspace</p>
  <h1 className="break-words text-3xl font-bold tracking-tight sm:text-4xl">Welcome, {user.displayName}</h1>
  <p className="mt-3 text-slate-600">Your next actions, in one place. Data is limited to your access in this organization.</p>
  {data.administration && <>
   <div className="metric-grid" aria-label="Organization overview" role="region">
    <Metric title="Active employees" value={data.administration.activeEmployees} href="/app/admin/employees?active=true" action="Manage employees" />
    <Metric title="Active projects" value={data.administration.activeProjects} href="/app/admin/projects?active=true" action="Manage projects" />
    <Metric title="Pending invitations" value={data.administration.pendingInvitations} href="/app/admin/invitations?status=PENDING" action="Review invitations" />
   </div>
   <section className="surface mt-5" aria-label="Organization setup"><h2 className="text-xl font-semibold">Set your team up for success</h2><p className="mt-2 text-slate-600">Employee profiles, account access and project assignments are separate steps. Start here for a new workspace.</p>
    <ol className="setup-steps"><li><Link href="/app/admin/employees">1. Create employee profiles</Link><p>Add employee numbers and manager relationships.</p></li><li><Link href="/app/admin/invitations">2. Invite your team</Link><p>Choose each member’s role. Access begins when they accept.</p></li><li><Link href="/app/admin/projects">3. Assign projects</Link><p>Open a project and assign employees for the correct dates.</p></li></ol>
   </section>
  </>}
  {user.role === "MANAGER" && <section className="surface mt-7" aria-label="Manager overview"><h2 className="text-xl font-semibold">Your team’s review queue</h2>
   {data.management ? <><p className="metric-value">{data.management.pendingApprovals}</p><p className="mb-4 text-slate-600">Pending approvals from your direct reports.</p><Link className="button" href="/app/approvals">Review team timesheets</Link>
    <h3 className="mt-6 font-semibold">Your recent decisions</h3>{!data.management.recentDecisions.length && <p className="mt-2 text-slate-600">No decisions for your current direct reports yet.</p>}
    <ul className="mt-3 space-y-3">{data.management.recentDecisions.map((a,i) => <li key={a.timesheetId + i}><Link href={`/app/approvals/${a.timesheetId}`}>{monthLabel(a.year,a.month)} · {label(a.decision)}</Link><p className="text-xs text-slate-500">{eventTime(a.decidedAt)}</p></li>)}</ul>
   </> : <p className="mt-3 text-slate-600">Ask your administrator to link an active employee profile to your manager account and assign your direct reports.</p>}
  </section>}
  {data.finance && <section className="mt-8" aria-label="Finance overview"><div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-semibold">SAP operations</h2><span className="simulation-label">Simulated · Mock SAP</span></div><p className="mt-2 text-slate-600">Approved timesheets across all periods in this organization. Approval does not automatically queue an export.</p>
   <div className="metric-grid"><Metric title="Awaiting export" value={data.finance.awaitingExport} href="/app/sap" action="Open export worklist" /><Metric title="In progress" value={data.finance.queued + data.finance.processing} href="/app/sap" action="View queue" /><Metric title="Needs attention" value={data.finance.failed + data.finance.unknown} href="/app/sap" action="Review outcomes" /></div>
   <p className="mt-3 text-sm text-slate-600">Queued: {data.finance.queued} · Processing: {data.finance.processing} · Completed: {data.finance.succeeded} · Failed: {data.finance.failed} · Needs reconciliation: {data.finance.unknown}</p>
   <div className="surface mt-5"><h3 className="font-semibold">Monthly reporting</h3><p className="mt-2 mb-4 text-slate-600">Review missing submissions and compare approved hours with successfully exported hours.</p><Link className="button-secondary" href="/app/reports/monthly">Open monthly reports</Link></div>
  </section>}
  {user.hasEmployeeProfile ? <section className="mt-8" aria-label="Your time overview"><h2 className="text-xl font-semibold">Your time</h2><CurrentMonth date={data.businessDate} />
   {!!data.rejectedMonths.length && <section className="surface mt-5 border-amber-200" aria-label="Timesheets needing changes"><h3 className="font-semibold">Changes requested</h3><p className="mt-2 text-sm text-slate-600">Open a rejected month, read the manager’s comments, then reopen the draft. Showing up to five recent months.</p><ul className="mt-3 space-y-2">{data.rejectedMonths.map(m => <li key={`${m.year}-${m.month}`}><Link href={`/app/timesheets/${m.year}/${m.month}`}>{monthLabel(m.year,m.month)} · Review changes</Link></li>)}</ul></section>}
   <div className="surface mt-5"><h3 className="font-semibold">Assigned projects</h3><p className="mt-2 mb-4 text-slate-600">{projects?.totalElements ?? 0} projects currently available. Date-specific choices appear in your timesheet.</p><Link href="/app/projects">View my projects →</Link></div>
  </section> : <section className="surface mt-7"><h2 className="font-semibold">Personal timesheets are not set up</h2><p className="mt-2 text-slate-600">You do not have an active employee profile linked to this account. Your organization role still provides the tools shown above. Contact an administrator if you also need to record time.</p></section>}
 </>;
}