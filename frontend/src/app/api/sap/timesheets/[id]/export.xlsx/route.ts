import { NextRequest, NextResponse } from "next/server";
import { backendResponse, BackendError } from "@/lib/backend";
export const runtime = "nodejs";
const xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
export async function GET(_request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
 const { id } = await params;
 if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))
  return NextResponse.json({ code: "VALIDATION_ERROR", message: "Invalid timesheet identifier." }, { status: 400 });
 try {
  const response = await backendResponse(`/api/v1/sap/timesheets/${id}/export.xlsx`, {
   headers: { Accept: xlsx }, signal: AbortSignal.timeout(30000),
  });
  if (response.headers.get("content-type") !== xlsx || !response.headers.get("content-disposition"))
   return NextResponse.json({ code: "DOWNLOAD_UNAVAILABLE", message: "The export download could not be verified." }, { status: 502 });
  return new Response(response.body, { headers: {
   "Content-Type": response.headers.get("content-type")!,
   "Content-Disposition": response.headers.get("content-disposition")!,
   "Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff",
  } });
 } catch (error) {
  if (error instanceof BackendError) return NextResponse.json({ code: error.code, message: error.message }, { status: error.status, headers: { "Cache-Control": "no-store" } });
  throw error;
 }
}
