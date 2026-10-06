import "server-only";
import { cache } from "react";
import { headers } from "next/headers";
import { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";
import { redirect } from "next/navigation";

export class BackendError extends Error {
 constructor(public status: number, public code: string, message: string) { super(message); }
}
export async function backendRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
 const request = new NextRequest(process.env.NEXTAUTH_URL ?? "http://localhost:3000", { headers: await headers() });
 const token = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET });
 if (!token?.accessToken) redirect("/login");
 if ((token.accessTokenExpires ?? 0) <= Date.now() / 1000 + 10) redirect("/login?expired=1");
 let response: Response;
 try {
  response = await fetch((process.env.BACKEND_API_URL ?? "http://localhost:8080") + path, {
   ...init, headers: { ...init.headers, Authorization: `Bearer ${token.accessToken}`, Accept: "application/json" },
   cache: "no-store", signal: init.signal ?? AbortSignal.timeout(10000),
  });
 } catch {
  throw new BackendError(503, "SERVICE_UNAVAILABLE", "The application service is temporarily unavailable. Please try again.");
 }
 if (response.status === 401) redirect("/login?expired=1");
 if (!response.ok) {
  const error = await response.json().catch(() => null);
  throw new BackendError(response.status, error?.code ?? "SERVICE_ERROR",
   response.status < 500 && typeof error?.message === "string" ? error.message : "The application service is temporarily unavailable. Please try again.");
 }
 return response.json() as Promise<T>;
}
// Minimal view types, not a second API schema. Generate the future client from backend OpenAPI.
export type CurrentUser = { id: string; displayName: string; email: string; role: string };
export type AssignedProject = { id: string; code: string; name: string; description: string | null };
export type ProjectPage = { content: AssignedProject[]; page: number; size: number; totalElements: number; totalPages: number };
export const currentUser = cache(async (): Promise<CurrentUser> => backendRequest("/api/v1/auth/me"));
export const assignedProjects = cache(async (page = 0): Promise<ProjectPage> => backendRequest(`/api/v1/employees/me/projects?page=${page}&size=20`));
