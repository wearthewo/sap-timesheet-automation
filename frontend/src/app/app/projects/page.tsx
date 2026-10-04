import Link from "next/link";
import { assignedProjects } from "@/lib/backend";
export default async function Projects({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
 const raw = (await searchParams).page ?? "0";
 const page = /^\d{1,6}$/.test(raw) ? Number(raw) : 0;
 const projects = await assignedProjects(page);
 return <>
  <h1 className="text-3xl font-bold tracking-tight">My projects</h1>
  <p className="mt-3 text-slate-600">{projects.totalElements} current assignments. Contact your manager if a project is missing.</p>
  <div className="mt-8 grid gap-4 sm:grid-cols-2">
   {projects.content.map(project => <article key={project.id} className="min-w-0 rounded-xl border border-slate-200 bg-white p-6">
    <p className="break-all text-xs font-bold tracking-wider text-blue-700">{project.code}</p>
    <h2 className="mt-3 break-words text-xl font-semibold">{project.name}</h2>
    <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-slate-600">{project.description || "No description provided."}</p>
   </article>)}
  </div>
  {projects.content.length === 0 && <div className="mt-5 rounded-xl border border-dashed border-slate-300 bg-white p-10 text-center"><h2 className="font-semibold">No projects on this page</h2><p className="mt-2 text-sm text-slate-500">Your current project assignments will appear here.</p></div>}
  <nav aria-label="Project pagination" className="mt-7 flex flex-wrap items-center gap-4">
   {page > 0 && <Link href={`/app/projects?page=${page - 1}`} className="button">Previous</Link>}
   {page + 1 < projects.totalPages && <Link href={`/app/projects?page=${page + 1}`} className="button">Next</Link>}
  </nav>
 </>;
}
