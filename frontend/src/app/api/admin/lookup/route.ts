import { NextRequest, NextResponse } from "next/server";
import { BackendError, currentUser } from "@/lib/backend";
import { adminGet, type AdminPage, type Employee, type Member, type Project } from "@/lib/admin";
export async function GET(request: NextRequest) {
 const user = await currentUser(); if (user.role !== "ADMIN") return NextResponse.json({ message: "Administrator access required." }, { status: 403 });
 const q = encodeURIComponent((request.nextUrl.searchParams.get("search") ?? "").slice(0, 200));
 try {
  let options: { value: string; label: string }[];
  switch (request.nextUrl.searchParams.get("kind")) {
   case "employees": options = (await adminGet<AdminPage<Employee>>(`employees?active=true&size=100&search=${q}`)).content.map(e => ({ value: e.id, label: `${e.employeeNumber} · ${e.displayName}` })); break;
   case "members": options = (await adminGet<AdminPage<Member>>(`members?status=ACTIVE&size=100&search=${q}`)).content.filter(m => !m.employeeId).map(m => ({ value: m.userId, label: `${m.displayName} · ${m.email}` })); break;
   case "projects": options = (await adminGet<AdminPage<Project>>(`projects?active=true&size=100&search=${q}`)).content.map(p => ({ value: p.id, label: `${p.code} · ${p.name}` })); break;
   default: return NextResponse.json({ message: "Invalid lookup." }, { status: 400 });
  }
  return NextResponse.json(options, { headers: { "Cache-Control": "no-store" } });
 } catch (e) { if (e instanceof BackendError) return NextResponse.json({ message: e.message }, { status: e.status }); throw e; }
}
