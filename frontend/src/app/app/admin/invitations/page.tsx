import { adminGet, listQuery, businessTime, type AdminPage, type Invitation, type Employee } from "@/lib/admin";
import AdminForm from "../admin-form";
import { Panel, Pager, roles } from "../components";
export default async function Invitations({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
 const params = await searchParams; const query = listQuery(params); const result = await adminGet<AdminPage<Invitation>>("invitations?" + query);
 const employee = typeof params.employeeId === "string" ? await adminGet<Employee>("employees/" + params.employeeId) : null;
 return <><h1 className="text-3xl font-bold">Invitations</h1><p className="mt-3">Invite a verified account to this organization. The recipient can sign in or create an account before accepting. A registered account has no organization access until the invitation is accepted. Email acceptance by the server does not prove receipt or acceptance of the invitation.</p>
 <Panel title="Invite a colleague"><AdminForm operation="invite-create" submit="Send invitation" confirmation="I confirm this email address and organization role are correct." fields={[
  { name: "email", label: "Invitation email", type: "email", required: true, maxLength: 320 }, { name: "role", label: "Organization role", type: "select", value: "EMPLOYEE", options: roles },
  { name: "employeeId", label: "Employee profile (optional)", lookup: "employees", value: employee?.id ?? "", options: employee ? [{ value: employee.id, label: `${employee.employeeNumber} · ${employee.displayName}` }] : [] },
 ]} /><p className="mt-4 text-sm text-slate-600">Choose an unlinked employee profile, or leave empty for membership only. Existing memberships must be changed from Members.</p></Panel>
 <form className="mb-5 flex flex-wrap gap-3"><label>Status<select className="ml-3 rounded-lg border p-2" name="status" defaultValue={query.get("status") ?? ""}><option value="">All</option>{["PENDING", "ACCEPTED", "REVOKED", "EXPIRED"].map(s => <option key={s}>{s}</option>)}</select></label><button className="button">Apply filters</button></form>
 <div className="grid gap-4">{result.content.map(i => <article key={i.id} className="rounded-xl border bg-white p-5"><h2 className="break-words font-semibold">{i.email}</h2><p className="mt-2">{i.role} · {i.status}</p><p className="my-3 text-sm">Expires {businessTime(i.expiresAt)} (Athens) · Delivery: {i.deliveryStatus}</p>
 {i.status === "PENDING" && <AdminForm operation="invite-revoke" id={i.id} submit="Revoke invitation" confirmation="I confirm the invitation should no longer be accepted." fields={[]} />}</article>)}</div>{result.content.length === 0 && <p>No invitations yet.</p>}<Pager result={result} query={query} /></>;
}
