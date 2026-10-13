import Link from "next/link";
import { requireAdmin } from "@/lib/admin";
export default async function AdminLayout({ children }: { children: React.ReactNode }) {
 await requireAdmin();
 return <><p className="text-sm font-semibold tracking-widest text-blue-800">ORGANIZATION ADMINISTRATION</p><nav aria-label="Administration" className="my-5 flex flex-wrap gap-4">{["Employees", "Members", "Invitations", "Projects"].map(label => <Link className="rounded border bg-white px-3 py-2" key={label} href={`/app/admin/${label.toLowerCase()}`}>{label}</Link>)}</nav>{children}</>;
}
