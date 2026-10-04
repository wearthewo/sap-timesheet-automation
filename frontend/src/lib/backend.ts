import "server-only";
import { cache } from "react";
import { headers } from "next/headers";
import { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";
import { redirect } from "next/navigation";

async function api(path: string) {
 const request = new NextRequest(process.env.NEXTAUTH_URL ?? "http://localhost:3000", { headers: await headers() });
 const token = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET });
 if (!token?.accessToken) redirect("/login");
 if ((token.accessTokenExpires ?? 0) <= Date.now() / 1000 + 10) redirect("/login?expired=1");
 const response = await fetch((process.env.BACKEND_API_URL ?? "http://localhost:8080") + path, {
  headers: { Authorization: `Bearer ${token.accessToken}`, Accept: "application/json" },
  cache: "no-store",
  signal: AbortSignal.timeout(10000),
 });
 if (response.status === 401) redirect("/login?expired=1");
 if (!response.ok) throw new Error("The application service is temporarily unavailable.");
 return response.json();
}
// Minimal view types, not a second API schema. Generate the future client from backend OpenAPI.
export type CurrentUser = { id: string; displayName: string; email: string; role: string };
export type AssignedProject = { id: string; code: string; name: string; description: string | null };
export type ProjectPage = { content: AssignedProject[]; page: number; size: number; totalElements: number; totalPages: number };
export const currentUser = cache(async (): Promise<CurrentUser> => api("/api/v1/auth/me"));
export const assignedProjects = cache(async (page = 0): Promise<ProjectPage> => api(`/api/v1/employees/me/projects?page=${page}&size=20`));
