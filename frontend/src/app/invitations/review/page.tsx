import { cookies, headers } from "next/headers";
import { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";
import Link from "next/link";
import { BackendError, backendRequest } from "@/lib/backend";
import SignIn from "../../login/sign-in";
import AcceptForm from "./accept-form";
async function signedIn() {
 const session = await getToken({ req: new NextRequest(process.env.NEXTAUTH_URL ?? "http://localhost:3000", { headers: await headers() }), secret: process.env.NEXTAUTH_SECRET });
 return Boolean(session?.accessToken && (session.accessTokenExpires ?? 0) > Date.now() / 1000 + 10);
}
export default async function InvitationReview() {
 const token = (await cookies()).get("workforce-invitation")?.value;
 if (!token) return <><h1 className="text-2xl font-bold">Invitation link required</h1><p className="my-5">Open the complete link in your invitation email to continue.</p><Link href="/login">Sign in</Link></>;
 if (!await signedIn()) return <><h1 className="text-2xl font-bold">Join your organization</h1><p className="my-5">Sign in using the invited email address, or create an account and verify that email.</p><div className="space-y-4"><SignIn callbackUrl="/invitations/review" /><SignIn registration callbackUrl="/invitations/review" /></div></>;
 let invitation: { organizationName: string; role: string; status: string } | undefined, error: string | undefined;
 try { invitation = await backendRequest("/api/v1/invitations/preview", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token }) }, false); }
 catch (e) { if (e instanceof BackendError) error = e.message; else throw e; }
 return <><h1 className="text-2xl font-bold">Organization invitation</h1>{error ? <p role="alert" className="my-5 text-red-800">{error}</p> : <><p className="mt-5 break-words">Organization: <strong>{invitation?.organizationName}</strong></p><p className="mt-3">Role: {invitation?.role} · {invitation?.status}</p>{["PENDING", "ACCEPTED"].includes(invitation?.status ?? "") ? <AcceptForm /> : <p className="my-5">This invitation is no longer available. Contact the organization administrator.</p>}</>}
 <form action="/api/logout" method="post" className="mt-6"><button className="rounded-lg border p-3">Log out</button></form></>;
}
