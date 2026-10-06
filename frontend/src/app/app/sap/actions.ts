"use server";
import { revalidatePath } from "next/cache";
import { BackendError, backendRequest } from "@/lib/backend";
import type { SapExport } from "@/lib/sap-view";
import type { ActionResult } from "@/lib/timesheet-view";
async function command(id: string, action: "" | "/retry" | "/reconcile", version?: number): Promise<ActionResult<SapExport>> {
 if (!/^[0-9a-f-]{36}$/i.test(id)) return { error: "Invalid timesheet." };
 try {
  const data = await backendRequest<SapExport>(`/api/v1/sap/timesheets/${id}/export${action}`, { method: "POST", headers: version === undefined ? {} : { "X-Timesheet-Version": String(version) } });
  revalidatePath("/app/sap");
  revalidatePath(`/app/sap/${id}`);
  return { data };
 } catch (e) {
  if (!(e instanceof BackendError)) throw e;
  return { error: e.message, code: e.code };
 }
}
export async function exportTimesheet(id: string, version: number) { return command(id, "", version); }
export async function retryExport(id: string) { return command(id, "/retry"); }
export async function reconcileExport(id: string) { return command(id, "/reconcile"); }
export async function refreshExport(id: string): Promise<ActionResult<SapExport>> {
 if (!/^[0-9a-f-]{36}$/i.test(id)) return { error: "Invalid timesheet." };
 try { return { data: await backendRequest<SapExport>(`/api/v1/sap/timesheets/${id}/export`) }; }
 catch (e) { if (e instanceof BackendError) return { error: e.message, code: e.code }; throw e; }
}
