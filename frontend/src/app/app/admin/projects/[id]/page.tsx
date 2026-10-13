import Link from "next/link";
import { backendRequest } from "@/lib/backend";
import { adminGet, listQuery, type Project, type Assignment, type AdminPage } from "@/lib/admin";
import AdminForm from "../../admin-form";
import { Panel, statuses } from "../../components";
import Assignments from "../../assignments";
export default async function ProjectDetail({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<Record<string, string | string[] | undefined>> }) {
 const { id } = await params; const query = listQuery(await searchParams); const [p, assignments] = await Promise.all([backendRequest<Project>("/api/v1/projects/" + id), adminGet<AdminPage<Assignment>>(`projects/${id}/assignments?${query}`)]);
 return <><Link href="/app/admin/projects">← Projects</Link><h1 className="mt-4 break-words text-3xl font-bold">{p.code} · {p.name}</h1><p className="mt-3 break-words">{p.active ? "ACTIVE" : "ARCHIVED"}</p>
 <Panel title="Project details"><AdminForm operation="project-edit" id={id} submit="Save project" fields={[{ name: "active", label: "Active", type: "hidden", value: String(p.active) }, { name: "code", label: "Project code", value: p.code, required: true, maxLength: 50 }, { name: "name", label: "Project name", value: p.name, required: true, maxLength: 200 }, { name: "description", label: "Description", type: "textarea", value: p.description ?? "", maxLength: 2000 }]} /></Panel>
 <Panel title="Project lifecycle"><AdminForm key={String(p.active)} operation="project-status" id={id} submit={p.active ? "Archive project" : "Activate project"} confirmation="I confirm this lifecycle change. Historical entries and exports are retained." fields={[{ name: "active", label: "Active", type: "hidden", value: String(!p.active) }]} /></Panel>
 <Assignments result={assignments} query={query} /><Panel title="Assign employee"><AdminForm operation="assignment-create" submit="Assign employee" fields={[{ name: "projectId", type: "hidden", label: "Project", value: id }, { name: "employeeId", label: "Employee", lookup: "employees", required: true }, { name: "validFrom", label: "Valid from", type: "date", required: true }, { name: "validTo", label: "Valid to", type: "date" }, { name: "active", label: "Assignment status", type: "select", options: statuses, value: "true" }]} /></Panel></>;
}
