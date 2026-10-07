import { NextRequest, NextResponse } from "next/server";
import { backendResponse, BackendError } from "@/lib/backend";
export const runtime = "nodejs";
export async function GET(request: NextRequest, { params }: { params: Promise<{ format: string }> }) {
 const { format } = await params;
 if (!["csv", "xlsx"].includes(format)) return NextResponse.json({ message: "Report format not found." }, { status: 404 });
 const query = new URLSearchParams();
 for (const name of ["year", "month", "employee", "project", "timesheetStatus", "sapStatus", "mode"]) {
  const value = request.nextUrl.searchParams.get(name); if (value !== null) query.set(name, value);
 }
 try {
  const response = await backendResponse(`/api/v1/reports/monthly/approved-hours.${format}?${query}`, {
   headers: { Accept: format === "csv" ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" },
   signal: AbortSignal.timeout(30000),
  });
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
