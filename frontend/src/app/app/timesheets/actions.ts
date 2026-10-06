"use server";
import { BackendError, backendRequest } from "@/lib/backend";
import type { ActionResult, ProjectOption, Timesheet } from "@/lib/timesheet-view";
import { revalidatePath } from "next/cache";

async function result<T>(operation: () => Promise<T>): Promise<ActionResult<T>> {
 try { return { data: await operation() }; }
 catch (error) {
  if (error instanceof BackendError) return { error: error.message, code: error.code };
  throw error; // Preserve Next.js authentication redirects.
 }
}
export async function createMonth(year: number, month: number) {
 return result(async () => {
  const sheet = await backendRequest<Timesheet>("/api/v1/timesheets", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ year, month }) });
  revalidatePath("/app");
  revalidatePath("/app/timesheets");
  return sheet;
 });
}
export async function availableProjects(id: string, date: string) {
 return result(() => backendRequest<ProjectOption[]>(`/api/v1/timesheets/${encodeURIComponent(id)}/available-projects?workDate=${encodeURIComponent(date)}`));
}
export async function changeTimesheet(id: string, version: number, operation: "add" | "edit" | "delete" | "submit" | "reopen", form?: { entryId?: string; projectId: string; workDate: string; hours: string; description: string }) {
 // Fixed operations only: never accept an arbitrary backend URL or HTTP method.
 if (!["add","edit","delete","submit","reopen"].includes(operation)) return { error: "Unknown operation." } as ActionResult<Timesheet>;
 let suffix = operation === "reopen" ? "/reopen" : "/submit";
 let method = "POST";
 if (operation !== "submit" && operation !== "reopen") {
  suffix = "/entries";
  if (operation !== "add") {
   if (!form?.entryId) return { error: "An entry is required." } as ActionResult<Timesheet>;
   suffix += "/" + encodeURIComponent(form.entryId);
   method = operation === "edit" ? "PUT" : "DELETE";
  }
 }
 return result(async () => {
  const sheet = await backendRequest<Timesheet>(`/api/v1/timesheets/${encodeURIComponent(id)}${suffix}`, {
   method, headers: { "Content-Type": "application/json", "X-Timesheet-Version": String(version) },
   body: operation === "add" || operation === "edit" ? JSON.stringify(form) : undefined,
  });
  revalidatePath("/app");
  revalidatePath("/app/timesheets");
  revalidatePath(`/app/timesheets/${sheet.year}/${sheet.month}`);
  return sheet;
 });
}
