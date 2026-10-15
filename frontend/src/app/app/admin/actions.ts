"use server";
import { BackendError, backendResponse } from "@/lib/backend";
import { requireAdmin } from "@/lib/admin";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
export type AdminState = { error?: string; success?: string };
export async function administer(operation: string, id: string | null, _state: AdminState, form: FormData): Promise<AdminState> {
 await requireAdmin();
 if (id && !/^[0-9a-f-]{36}$/i.test(id)) return { error: "Invalid record. Reload the page." };
 const value = (key: string) => String(form.get(key) ?? "");
 const optional = (key: string) => value(key) || null;
 const version = Number(value("version"));
 let path: string, method = "POST", body: object | undefined, destination: string | undefined;
 switch (operation) {
  case "employee-create": path = "/admin/employees"; body = { employeeNumber: value("employeeNumber"), displayName: value("displayName"), managerId: optional("managerId"), userId: optional("userId"), active: value("active") === "true" }; destination = "/app/admin/employees/"; break;
  case "employee-edit": path = `/admin/employees/${id}`; method = "PUT"; body = { employeeNumber: value("employeeNumber"), displayName: value("displayName"), managerId: optional("managerId"), version }; break;
  case "employee-status": path = `/admin/employees/${id}/status`; body = { active: value("active") === "true", version }; break;
  case "employee-link": path = `/admin/employees/${id}/link`; body = { userId: value("userId"), version }; break;
  case "member-change": path = `/admin/members/${id}`; method = "PUT"; body = { role: value("role"), status: value("status"), version }; break;
  case "invite-create": path = "/admin/invitations"; body = { email: value("email"), employeeId: optional("employeeId"), role: value("role") }; break;
  case "invite-revoke": path = `/admin/invitations/${id}/revoke`; break;
  case "project-create": path = "/projects"; body = { code: value("code"), name: value("name"), description: optional("description"), active: true }; destination = "/app/admin/projects/"; break;
  case "project-edit": path = `/projects/${id}`; method = "PUT"; body = { code: value("code"), name: value("name"), description: optional("description"), active: value("active") === "true" }; break;
  case "project-status": path = `/admin/projects/${id}/status`; body = { active: value("active") === "true" }; break;
  case "assignment-create": case "assignment-edit": path = "/project-assignments" + (operation === "assignment-edit" ? `/${id}` : ""); method = operation === "assignment-edit" ? "PUT" : "POST"; body = { employeeId: value("employeeId"), projectId: value("projectId"), validFrom: value("validFrom"), validTo: optional("validTo"), active: value("active") === "true" }; break;
  default: return { error: "Unsupported administration action." };
 }
 let createdId: string | undefined;
 try { const r = await backendResponse("/api/v1" + path, { method, headers: { "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined }); if (destination) createdId = (await r.json()).id; }
 catch (e) { if (e instanceof BackendError) return { error: e.status === 409 ? e.message + " Review the latest saved record before retrying. Your form values have been kept." : e.message }; throw e; }
 revalidatePath("/app", "layout");
 if (destination && createdId) redirect(destination + createdId);
 return { success: operation === "invite-create" ? "Invitation queued for delivery." : "Changes saved." };
}
