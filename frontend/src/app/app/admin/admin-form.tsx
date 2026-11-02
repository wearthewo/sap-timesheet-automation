"use client";
import { useActionState, useId, useState } from "react";
import { administer } from "./actions";
export type Field = { name: string; label: string; type?: "text" | "email" | "date" | "textarea" | "select" | "hidden"; value?: string; required?: boolean; maxLength?: number; options?: { value: string; label: string }[]; lookup?: "employees" | "members" | "projects" };
export default function AdminForm({ operation, id = null, fields, submit, confirmation, destructive }: { operation: string; id?: string | null; fields: Field[]; submit: string; confirmation?: string; destructive?: string }) {
 const [state, action, pending] = useActionState(administer.bind(null, operation, id), {});
 const [values, setValues] = useState<Record<string, string>>(() => Object.fromEntries(fields.map(f => [f.name, f.value ?? ""])));
 const formId = useId();
 return <form action={action} onSubmit={event => { const prompt = destructive ?? (operation === "member-change" && values.status === "REVOKED" ? "Remove this member from the organization? Access will be revoked; historical records are retained. Reassign any direct reports." : undefined); if (prompt && !window.confirm(prompt)) event.preventDefault(); }} className="grid min-w-0 gap-4" aria-label={submit}>
  {fields.map(f => f.type === "hidden" ? <input key={f.name} type="hidden" name={f.name} value={f.value ?? ""} /> : <div key={f.name} className="min-w-0">
   <label htmlFor={formId + f.name} className="block text-sm font-semibold">{f.label}</label>
   {f.lookup ? <Lookup field={f} id={formId + f.name} value={values[f.name]} change={value => setValues(v => ({ ...v, [f.name]: value }))} /> : f.type === "select" ?
    <select id={formId + f.name} name={f.name} required={f.required} value={values[f.name]} onChange={e => setValues(v => ({ ...v, [f.name]: e.target.value }))} className="mt-1 w-full rounded-lg border p-3">{f.options?.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select> : f.type === "textarea" ?
    <textarea id={formId + f.name} name={f.name} maxLength={f.maxLength} value={values[f.name]} onChange={e => setValues(v => ({ ...v, [f.name]: e.target.value }))} className="mt-1 w-full rounded-lg border p-3" /> :
    <input id={formId + f.name} name={f.name} type={f.type ?? "text"} required={f.required} maxLength={f.maxLength} value={values[f.name]} onChange={e => setValues(v => ({ ...v, [f.name]: e.target.value }))} className="mt-1 w-full rounded-lg border p-3" />}
  </div>)}
  {confirmation && <label className="flex items-start gap-3 rounded-lg bg-amber-50 p-3 text-sm"><input type="checkbox" required className="mt-1" />{confirmation}</label>}
  {state.error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-red-800">{state.error}<button type="button" className="button-secondary mt-3 block" onClick={() => { if (window.confirm("Reload the saved record? Unsaved form values will be lost.")) window.location.reload(); }}>Reload saved record</button></p>}
  {state.success && <p role="status" className="rounded-lg bg-green-50 p-3 text-green-800">{state.success}</p>}
  <button className="button w-fit max-w-full disabled:opacity-60" disabled={pending}>{pending ? "Saving…" : submit}</button>
 </form>;
}
function Lookup({ field, id, value, change }: { field: Field; id: string; value: string; change: (value: string) => void }) {
 const [query, setQuery] = useState(""); const [options, setOptions] = useState(field.options ?? []); const [error, setError] = useState(""); const [loading, setLoading] = useState(false);
 async function search() { setLoading(true); setError(""); try { const r = await fetch(`/api/admin/lookup?kind=${field.lookup}&search=${encodeURIComponent(query)}`); if (!r.ok) throw new Error(); setOptions(await r.json()); } catch { setError("Unable to load choices. Try again."); } finally { setLoading(false); } }
 return <div className="mt-1 grid gap-2">
  <div className="flex flex-wrap gap-2"><input aria-label={`Search ${field.label}`} value={query} onChange={e => setQuery(e.target.value)} className="min-w-0 flex-1 rounded-lg border p-2" placeholder="Search by name or number" /><button type="button" onClick={search} disabled={loading} className="rounded-lg border px-3 py-2 text-sm">{loading ? "Searching…" : `Find ${field.label}`}</button></div>
  <select id={id} name={field.name} value={value} onChange={e => change(e.target.value)} required={field.required} className="w-full rounded-lg border p-3"><option value="">{field.required ? "Choose a record" : "None"}</option>{options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
  <p className="text-xs text-slate-500">Search to load up to 100 matching active records. Refine the search for more specific results.</p>{error && <p role="alert">{error}</p>}
 </div>;
}
