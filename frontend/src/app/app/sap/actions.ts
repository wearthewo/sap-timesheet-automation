"use server";
import { revalidatePath } from "next/cache";
import { BackendError, backendRequest } from "@/lib/backend";
import type { SapExport } from "@/lib/sap-view";
import type { ActionResult } from "@/lib/timesheet-view";
export async function exportTimesheet(id: string, version: number): Promise<ActionResult<SapExport>> {
 if (!/^[0-9a-f-]{36}$/i.test(id)) return { error: "Invalid timesheet." };
 try {
  const timeout = Number(process.env.SAP_EXPORT_REQUEST_TIMEOUT_MS ?? 20000);
  const data = await backendRequest<SapExport>(`/api/v1/sap/timesheets/${id}/export`, { method: "POST", headers: { "X-Timesheet-Version": String(version) }, signal: AbortSignal.timeout(timeout) });
  revalidatePath("/app/sap");
  revalidatePath(`/app/sap/${id}`);
  return { data };
 } catch (e) {
  if (!(e instanceof BackendError)) throw e;
  // The command may have reached SAP even when its response was lost. Read state, never retry POST.
  const state = await refreshExport(id);
  if (state.data) return state;
  return { error: e.message, code: e.code };
 }
}
export async function refreshExport(id: string): Promise<ActionResult<SapExport>> {
 if (!/^[0-9a-f-]{36}$/i.test(id)) return { error: "Invalid timesheet." };
 try { return { data: await backendRequest<SapExport>(`/api/v1/sap/timesheets/${id}/export`) }; }
 catch (e) { if (e instanceof BackendError) return { error: e.message, code: e.code }; throw e; }
}
