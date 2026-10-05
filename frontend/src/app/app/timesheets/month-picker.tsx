"use client";
import { useRouter } from "next/navigation";
export default function MonthPicker({ current }: { current: string }) {
 const router = useRouter();
 return <form className="flex flex-wrap items-end gap-3" onSubmit={event => {
  event.preventDefault();
  const value = String(new FormData(event.currentTarget).get("month"));
  const [year,month] = value.split("-").map(Number);
  if (year >= 1 && year <= 9999 && month >= 1 && month <= 12) router.push(`/app/timesheets/${year}/${month}`);
 }}>
  <label className="grid gap-2 text-sm font-semibold">Choose a month<input className="field" type="month" name="month" defaultValue={current} min="0001-01" max="9999-12" required /></label>
  <button className="button">Open month</button>
 </form>;
}
