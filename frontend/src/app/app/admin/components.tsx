import Link from "next/link";
import type { AdminPage } from "@/lib/admin";
export function Panel({ title, children }: { title: string; children: React.ReactNode }) { return <section id={title.toLowerCase().replaceAll(" ", "-")} className="my-6 scroll-mt-5 min-w-0 rounded-xl border bg-white p-5 sm:p-7"><h2 className="mb-5 text-xl font-semibold">{title}</h2>{children}</section>; }
export function Pager({ result, query }: { result: AdminPage<unknown>; query: URLSearchParams }) {
 const link = (page: number) => { const q = new URLSearchParams(query); q.set("page", String(page)); return "?" + q; };
 return <nav aria-label="Pagination" className="mt-4 flex flex-wrap gap-4 text-sm"><span>{result.totalElements} records · Page {result.page + 1} of {Math.max(1, result.totalPages)}</span>{result.page > 0 && <Link href={link(result.page - 1)}>Previous</Link>}{result.page + 1 < result.totalPages && <Link href={link(result.page + 1)}>Next</Link>}</nav>;
}
export function Filters({ query, noun }: { query: URLSearchParams; noun: "employees" | "projects" | "members" }) {
 return <form className="my-5 flex flex-wrap items-end gap-3"><label className="min-w-0 flex-1 text-sm">Search {noun}<input name="search" defaultValue={query.get("search") ?? ""} className="mt-1 block w-full rounded-lg border p-2" /></label>
 {noun === "members" ? <label className="text-sm">Status<select name="status" defaultValue={query.get("status") ?? ""} className="mt-1 block rounded-lg border p-2"><option value="">All</option>{["ACTIVE", "SUSPENDED", "REVOKED"].map(s => <option key={s}>{s}</option>)}</select></label> : <><label className="text-sm">Status<select name="active" defaultValue={query.get("active") ?? ""} className="mt-1 block rounded-lg border p-2"><option value="">All</option><option value="true">Active</option><option value="false">Inactive</option></select></label><label className="text-sm">Sort<select name="sort" defaultValue={query.get("sort") ?? ""} className="mt-1 block rounded-lg border p-2"><option value="">{noun === "employees" ? "Employee number" : "Project code"}</option><option value="name">Name</option><option value={noun === "employees" ? "number-desc" : "code-desc"}>Identifier descending</option></select></label></>}
 <button className="button">Apply filters</button></form>;
}
export const statuses = [{ value: "true", label: "Active" }, { value: "false", label: "Inactive" }];
export const roles = ["EMPLOYEE", "MANAGER", "FINANCE", "ADMIN"].map(value => ({ value, label: value }));
