"use server";
import { backendRequest, BackendError } from "@/lib/backend";
import type { ActionResult } from "@/lib/timesheet-view";
import type { ApprovalReview } from "@/lib/approval-view";
import { revalidatePath } from "next/cache";
export async function decideTimesheet(id: string, version: number, decision: "approve" | "reject", comment: string): Promise<ActionResult<ApprovalReview>> {
 if (decision !== "approve" && decision !== "reject") return { error: "Unknown decision." };
 try {
  const data = await backendRequest<ApprovalReview>(`/api/v1/approvals/timesheets/${encodeURIComponent(id)}/${decision}`, {
   method: "POST", headers: { "Content-Type": "application/json", "X-Timesheet-Version": String(version) }, body: JSON.stringify({comment}),
  });
  revalidatePath("/app");
  revalidatePath("/app/approvals");
  revalidatePath("/app/timesheets");
  revalidatePath(`/app/timesheets/${data.timesheet.year}/${data.timesheet.month}`);
  return {data};
 } catch(error) {
  if(error instanceof BackendError) return {error:error.message,code:error.code};
  throw error;
 }
}
