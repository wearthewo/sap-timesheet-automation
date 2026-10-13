import "server-only";
import { cache } from "react";
import { headers, cookies } from "next/headers";
import { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";
import { redirect } from "next/navigation";

export class BackendError extends Error {
 constructor(public status: number, public code: string, message: string) { super(message); }
}
export type OrganizationMembership = { id: string; slug: string; name: string; role: string };
const membershipsForToken = cache(async (accessToken: string): Promise<OrganizationMembership[]> => {
 const response = await fetch((process.env.BACKEND_API_URL ?? "http://localhost:8080") + "/api/v1/auth/organizations", {
  headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store", signal: AbortSignal.timeout(10000),
 });
 if (response.status === 401) redirect("/login?expired=1");
 if (!response.ok) throw new BackendError(response.status, "ORGANIZATION_UNAVAILABLE", "Unable to load your organizations. Please try again.");
 return response.json();
});
export async function backendResponse(path: string, init: RequestInit = {}, organizationScoped = true): Promise<Response> {
 const request = new NextRequest(process.env.NEXTAUTH_URL ?? "http://localhost:3000", { headers: await headers() });
 const token = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET });
 if (!token?.accessToken) redirect("/login");
 if ((token.accessTokenExpires ?? 0) <= Date.now() / 1000 + 10) redirect("/login?expired=1");
 let organizationId: string | undefined;
 if (organizationScoped && path !== "/api/v1/auth/organizations") {
  const memberships = await membershipsForToken(token.accessToken);
  const selected = (await cookies()).get("workforce-organization")?.value;
  organizationId = selected ? memberships.find(m => m.id === selected)?.id : memberships.length === 1 ? memberships[0].id : undefined;
  if (!organizationId) redirect(memberships.length === 0 ? "/onboarding" : "/organizations");
 }
 let response: Response;
 try {
  response = await fetch((process.env.BACKEND_API_URL ?? "http://localhost:8080") + path, {
   ...init, headers: { Accept: "application/json", ...init.headers, Authorization: `Bearer ${token.accessToken}`, ...(organizationId ? { "X-Organization-Id": organizationId } : {}) },
   cache: "no-store", signal: init.signal ?? AbortSignal.timeout(10000),
  });
 } catch {
  throw new BackendError(503, "SERVICE_UNAVAILABLE", "The application service is temporarily unavailable. Please try again.");
 }
 if (response.status === 401) redirect("/login?expired=1");
 if (!response.ok) {
  const error = await response.json().catch(() => null);
  if (organizationScoped && error?.code === "EMAIL_VERIFICATION_REQUIRED") redirect("/onboarding");
  throw new BackendError(response.status, error?.code ?? "SERVICE_ERROR",
   response.status < 500 && typeof error?.message === "string" ? error.message : "The application service is temporarily unavailable. Please try again.");
 }
 return response;
}
// Minimal view types, not a second API schema. Generate the future client from backend OpenAPI.
export type CurrentUser = { id: string; displayName: string; email: string; role: string; organizationId: string; organizationName: string; hasEmployeeProfile: boolean };
export type AssignedProject = { id: string; code: string; name: string; description: string | null };
export type ProjectPage = { content: AssignedProject[]; page: number; size: number; totalElements: number; totalPages: number };
export const currentUser = cache(async (): Promise<CurrentUser> => backendRequest("/api/v1/auth/me"));
export type IdentityProfile = { id: string; displayName: string; email: string; emailVerified: boolean };
export const identityProfile = cache(async (): Promise<IdentityProfile> => backendRequest("/api/v1/auth/me", {}, false));
export const assignedProjects = cache(async (page = 0): Promise<ProjectPage> => backendRequest(`/api/v1/employees/me/projects?page=${page}&size=20`));

export async function backendRequest<T>(path: string, init: RequestInit = {}, organizationScoped = true): Promise<T> {
 return (await backendResponse(path, init, organizationScoped)).json() as Promise<T>;
}
