"use client";
import { useActionState, useState } from "react";
import { createOrganization } from "./actions";
export default function CreateForm({ requestKey }: { requestKey: string }) {
 const [state, action, pending] = useActionState(createOrganization, {});
 // Read both live fields: one may have been typed before hydration, when React
 // had no change handler. Updating only the other field would erase that text.
 // Keep controlled values so action resets cannot undo a subsequent correction.
 const [values, setValues] = useState(state.values ?? { name: "", slug: "" });
 function syncValues(form: HTMLFormElement | null) {
  if (!form) return;
  const fields = new FormData(form);
  setValues({ name: String(fields.get("name") ?? ""), slug: String(fields.get("slug") ?? "") });
 }
 return <form action={action} onSubmit={event => syncValues(event.currentTarget)} className="mt-7 grid gap-5">
  <input type="hidden" name="requestKey" value={requestKey} />
  <div><label htmlFor="name" className="block font-semibold">Organization name</label>
   <input id="name" name="name" value={values.name} onChange={event => syncValues(event.currentTarget.form)} required maxLength={200} autoComplete="organization" className="mt-2 w-full rounded-lg border p-3" placeholder="Example Company" /></div>
  <div><label htmlFor="slug" className="block font-semibold">Organization address</label>
   <input id="slug" name="slug" value={values.slug} onChange={event => syncValues(event.currentTarget.form)} required minLength={3} maxLength={63} autoCapitalize="none" spellCheck={false} aria-describedby="slug-help" className="mt-2 w-full rounded-lg border p-3" placeholder="example-company" />
   <p id="slug-help" className="mt-2 text-sm text-slate-600">A unique name using 3–63 letters, numbers or hyphens, for example example-company.</p></div>
  {state.error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-red-800">{state.error}</p>}
  <button className="button w-full disabled:opacity-60" disabled={pending}>{pending ? "Creating organization…" : "Create organization"}</button>
  <p aria-live="polite" className="text-sm text-slate-600">{pending ? "Setting up your workspace. Please wait." : "You will be the ADMIN of this new organization. Existing organizations and memberships will not change."}</p>
 </form>;
}
