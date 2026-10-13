"use server";
import { cookies } from "next/headers";
import { BackendError, backendRequest } from "@/lib/backend";
import { selectOrganizationCookie } from "@/lib/organization-cookie";
import { redirect } from "next/navigation";
export async function acceptInvitation(): Promise<{ error?: string }> {
 const token = (await cookies()).get("workforce-invitation")?.value;
 if (!token) return { error: "Open your invitation email link again." };
 let result: { organizationId: string };
 try { result = await backendRequest("/api/v1/invitations/accept", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token }) }, false); }
 catch (error) { if (error instanceof BackendError) return { error: error.message }; throw error; }
 await selectOrganizationCookie(result.organizationId);
 (await cookies()).delete("workforce-invitation");
 redirect("/app");
}
