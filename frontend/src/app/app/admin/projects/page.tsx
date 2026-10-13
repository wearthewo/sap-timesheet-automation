import Link from "next/link";
import { adminGet, listQuery, type AdminPage, type Project } from "@/lib/admin";
import AdminForm from "../admin-form";
import { Panel, Pager, Filters } from "../components";
export default async function Projects({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
 const query = listQuery(await searchParams); const result = await adminGet<AdminPage<Project>>("projects?" + query);
 return <><h1 className="text-3xl font-bold">Project administration</h1><Filters query={query} noun="projects" /><div className="overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left text-sm"><thead><tr><th className="p-4">Code</th><th className="p-4">Name</th><th className="p-4">Status</th></tr></thead><tbody>{result.content.map(p => <tr key={p.id} className="border-t"><td className="p-4"><Link href={`/app/admin/projects/${p.id}`}>{p.code}</Link></td><td className="p-4">{p.name}</td><td className="p-4">{p.active ? "ACTIVE" : "ARCHIVED"}</td></tr>)}</tbody></table></div>{result.content.length === 0 && <p className="mt-4">No matching projects. Create your first project below.</p>}<Pager result={result} query={query} />
 <Panel title="Create project"><AdminForm operation="project-create" submit="Create project" fields={[{ name: "code", label: "Project code", required: true, maxLength: 50 }, { name: "name", label: "Project name", required: true, maxLength: 200 }, { name: "description", label: "Description", type: "textarea", maxLength: 2000 }]} /></Panel></>;
}
