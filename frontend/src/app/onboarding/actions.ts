"use server";
import { BackendError, backendRequest } from "@/lib/backend";
import { selectOrganizationCookie } from "@/lib/organization-cookie";
import { redirect } from "next/navigation";
export type CreateState = { error?: string; values?: { name: string; slug: string } };
export async function createOrganization(_state: CreateState, form: FormData): Promise<CreateState> {
 const values = { name: String(form.get("name") ?? ""), slug: String(form.get("slug") ?? "") };
 let created: { id: string };
 try {
  created = await backendRequest("/api/v1/organizations", {
   method: "POST", headers: { "Content-Type": "application/json", "Idempotency-Key": String(form.get("requestKey") ?? "") },
   body: JSON.stringify(values),
  }, false);
 } catch (error) {
  if (error instanceof BackendError) return { error: error.message, values };
  throw error;
 }
 await selectOrganizationCookie(created.id);
 redirect("/app");
}
