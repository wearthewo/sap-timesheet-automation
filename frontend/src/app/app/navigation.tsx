"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
export default function Navigation({ role }: { role: string }) {
 const pathname = usePathname();
 const links = [["/app", "Overview"], ["/app/timesheets", "Timesheets"], ["/app/projects", "My projects"]];
 if (role === "MANAGER") links.push(["/app/approvals", "Approvals"]);
 if (["ADMIN", "FINANCE"].includes(role)) links.push(["/app/sap", "SAP exports"], ["/app/reports", "Reports"]);
 if (role === "ADMIN") links.push(["/app/admin/employees", "Administration"]);
 links.push(["/app/notifications", "Notifications"], ["/organizations", "Organization"]);
 return <nav aria-label="Main navigation" className="workspace-nav">{links.map(([href, name]) => {
  const active = href === "/app" ? pathname === href : href.includes("/admin/") ? pathname.startsWith("/app/admin") : pathname.startsWith(href);
  return <Link key={href} href={href} aria-current={active ? "page" : undefined}>{name}</Link>;
 })}</nav>;
}
