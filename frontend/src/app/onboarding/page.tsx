import { randomUUID } from "node:crypto";
import Link from "next/link";
import { backendRequest, identityProfile, type OrganizationMembership } from "@/lib/backend";
import CreateForm from "./create-form";

export default async function Onboarding() {
 const [user, memberships] = await Promise.all([identityProfile(), backendRequest<OrganizationMembership[]>("/api/v1/auth/organizations")]);
 return <main className="mx-auto max-w-xl px-5 py-12">
  <p className="text-sm font-bold tracking-widest text-blue-800">WORKFORCE</p>
  <section className="mt-6 rounded-2xl border bg-white p-6 shadow-sm sm:p-9">
   <h1 className="text-3xl font-bold">{user.emailVerified ? "Create your organization" : "Verify your email"}</h1>
   {user.emailVerified ? <>
    <p className="mt-4 text-slate-600">Welcome, {user.displayName}. Start with an empty workspace for your team.</p>
    {memberships.length === 0 && <p className="mt-3 text-sm text-slate-600">You have no active organization memberships. If you expected access to an existing organization, contact its administrator. Creating a workspace will not restore suspended access.</p>}
    <CreateForm requestKey={randomUUID()} />
   </> : <div className="mt-5 space-y-4 text-slate-600">
    <p>Verify your email through the identity provider before creating or accessing an organization.</p>
    <p>Sign out, sign in again and follow the email-verification instructions. Check your inbox and spam folder; use the resend option on the verification page if needed.</p>
   </div>}
   {memberships.length > 0 && <Link className="mt-5 inline-block" href="/organizations">Choose an existing organization</Link>}
   <form action="/api/logout" method="post" className="mt-7"><button className="rounded-lg border px-4 py-2 font-semibold">Log out</button></form>
  </section>
 </main>;
}
