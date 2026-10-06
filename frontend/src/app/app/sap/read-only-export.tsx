import { BackendError, backendRequest } from "@/lib/backend";
import type { SapExport } from "@/lib/sap-view";
import ExportPanel from "./export-panel";
export default async function ReadOnlyExport({ id, version }: { id: string; version: number }) {
 let state: SapExport | null = null;
 try { state = await backendRequest<SapExport>(`/api/v1/sap/timesheets/${id}/export`); }
 catch (e) { if (!(e instanceof BackendError)) throw e; if (e.code !== "SAP_EXPORT_NOT_FOUND") return <p className="mt-5">SAP export status is currently unavailable.</p>; }
 return <ExportPanel id={id} version={version} initial={state} />;
}
