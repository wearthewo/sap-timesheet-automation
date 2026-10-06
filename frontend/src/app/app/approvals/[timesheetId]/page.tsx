import { backendRequest, BackendError, currentUser } from "@/lib/backend";
import type { ApprovalReview } from "@/lib/approval-view";
import Review from "../review";
import ReadOnlyExport from "../../sap/read-only-export";
export default async function ApprovalDetail({params}: {params: Promise<{timesheetId:string}>}) {
 if((await currentUser()).role!=="MANAGER") return <p role="alert">Manager access is required.</p>;
 const {timesheetId}=await params;
 let data: ApprovalReview;
 try {data=await backendRequest<ApprovalReview>(`/api/v1/approvals/timesheets/${encodeURIComponent(timesheetId)}`);}
 catch(error) {if(error instanceof BackendError) return <p role="alert">{error.message}</p>;throw error;}
 return <><Review initial={data}/>{data.timesheet.status === "APPROVED" && <ReadOnlyExport id={data.timesheet.id} version={data.timesheet.version} />}</>;
}
