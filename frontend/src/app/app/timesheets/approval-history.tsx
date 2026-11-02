import { eventTime, label } from "@/lib/display";
import type { DecisionView } from "@/lib/timesheet-view";
export default function ApprovalHistory({ history }: { history: DecisionView[] }) {
 if (!history.length) return null;
 return <section aria-label="Decision history" className="rounded-xl border border-slate-200 bg-white p-6">
  <h2 className="mb-4 text-lg font-semibold">Decision history</h2>
  <p className="mb-4 text-sm text-slate-600">Historical decisions are retained after reopening and resubmission.</p><ol className="space-y-4">{history.map(decision => <li key={decision.id} className="border-l-2 border-blue-200 pl-4">
   <p className="text-sm font-semibold">{label(decision.decision)} · {eventTime(decision.decidedAt)}</p>
   <p className="mt-1 whitespace-pre-wrap break-words text-slate-700">{decision.comment || "No comment"}</p>
  </li>)}</ol>
 </section>;
}
