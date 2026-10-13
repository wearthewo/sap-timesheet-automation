import type { AdminPage, Assignment } from "@/lib/admin";
import AdminForm from "./admin-form";
import { Panel, Pager, statuses } from "./components";
export default function Assignments({ result, query = new URLSearchParams() }: { result: AdminPage<Assignment>; query?: URLSearchParams }) {
 return <Panel title="Project assignments">{result.content.length === 0 && <p>No project assignments yet.</p>}
  <div className="grid gap-4">{result.content.map(a => <details key={a.id} className="rounded-lg border p-4"><summary className="cursor-pointer break-words font-semibold">{a.employeeNumber} · {a.employeeName} — {a.projectCode} · {a.projectName} ({a.active ? "Active" : "Inactive"})</summary>
   <p className="my-3 text-sm">{a.validFrom} to {a.validTo ?? "No end date"}</p><AdminForm key={`${a.validFrom}-${a.validTo}-${a.active}`} operation="assignment-edit" id={a.id} submit="Save assignment" fields={[
    { name: "employeeId", label: "Employee", type: "hidden", value: a.employeeId }, { name: "projectId", label: "Project", type: "hidden", value: a.projectId },
    { name: "validFrom", label: "Valid from", type: "date", required: true, value: a.validFrom }, { name: "validTo", label: "Valid to", type: "date", value: a.validTo ?? "" },
    { name: "active", label: "Assignment status", type: "select", value: String(a.active), options: statuses },
   ]} /></details>)}</div><Pager result={result} query={query} />
 </Panel>;
}
