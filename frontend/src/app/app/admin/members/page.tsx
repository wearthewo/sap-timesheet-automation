import Link from "next/link";
import { adminGet, listQuery, type AdminPage, type Member } from "@/lib/admin";
import AdminForm from "../admin-form";
import { Filters, Pager, roles } from "../components";
export default async function Members({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
 const query = listQuery(await searchParams); const result = await adminGet<AdminPage<Member>>("members?" + query);
 return <><h1 className="text-3xl font-bold">Organization members</h1><p className="mt-3">Membership grants access to this organization. An employee profile is optional and managed separately.</p><Filters query={query} noun="members" />
 <div className="grid gap-4">{result.content.map(m => <details key={m.id} className="rounded-xl border bg-white p-5"><summary className="cursor-pointer break-words font-semibold">{m.displayName} · {m.role} · {m.status}</summary>
 <p className="my-3 break-words">{m.email}</p><p className="mb-4">{m.employeeId ? <Link href={`/app/admin/employees/${m.employeeId}`}>Employee {m.employeeNumber}</Link> : "No employee profile"}</p>
 <AdminForm key={m.version} operation="member-change" id={m.id} submit="Update membership" confirmation="I confirm this role and access change. It takes effect on subsequent requests." fields={[
  { name: "version", label: "Version", type: "hidden", value: String(m.version) }, { name: "role", label: "Organization role", type: "select", value: m.role, options: roles },
  { name: "status", label: "Membership status", type: "select", value: m.status, options: ["ACTIVE", "SUSPENDED", "REVOKED"].map(value => ({ value, label: value })) },
 ]} /></details>)}</div>{result.content.length === 0 && <p>No matching members.</p>}<Pager result={result} query={query} /></>;
}
