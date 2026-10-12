import { backendRequest, type OrganizationMembership } from "@/lib/backend";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";

async function selectOrganization(form: FormData) {
 "use server";
 const memberships = await backendRequest<OrganizationMembership[]>("/api/v1/auth/organizations");
 const selected = memberships.find(m => m.id === form.get("organizationId"));
 if (!selected) redirect("/organizations?unavailable=1");
 (await cookies()).set("workforce-organization", selected.id, {
  httpOnly: true, secure: new URL(process.env.NEXTAUTH_URL ?? "http://localhost:3000").protocol === "https:",
  sameSite: "lax", path: "/",
 });
 redirect("/app");
}

export default async function Organizations() {
 const memberships = await backendRequest<OrganizationMembership[]>("/api/v1/auth/organizations");
 return <main className="mx-auto max-w-xl px-5 py-12">
  <h1 className="text-3xl font-bold">Choose your organization</h1>
  {memberships.length === 0 ? <p className="my-6">You have no active organization memberships. Contact your organization administrator.</p> :
   <form action={selectOrganization} className="my-6 grid gap-4">
    <label htmlFor="organization">Organization</label>
    <select id="organization" name="organizationId" required className="w-full rounded-lg border p-3">
     {memberships.map(m => <option key={m.id} value={m.id}>{m.name} — {m.role}</option>)}
    </select>
    <button className="rounded-lg bg-blue-800 px-4 py-3 font-semibold text-white">Continue</button>
    <p className="text-sm text-slate-600">After switching, reload other open application tabs before continuing.</p>
   </form>}
  <form action="/api/logout" method="post"><button className="rounded-lg border px-4 py-2">Log out</button></form>
 </main>;
}
