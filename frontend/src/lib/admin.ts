import "server-only";
import { backendRequest, currentUser } from "./backend";
import { notFound } from "next/navigation";
export async function requireAdmin() { const user = await currentUser(); if (user.role !== "ADMIN") notFound(); return user; }
export type AdminPage<T> = { content: T[]; page: number; size: number; totalPages: number; totalElements: number };
export type Employee = { id: string; employeeNumber: string; displayName: string; active: boolean; managerId: string | null; managerName: string | null; userId: string | null; email: string | null; version: number; invitationStatus: string | null };
export type Member = { id: string; userId: string; displayName: string; email: string; role: string; status: string; employeeId: string | null; employeeNumber: string | null; version: number };
export type Project = { id: string; code: string; name: string; description: string | null; active: boolean };
export type Assignment = { id: string; employeeId: string; employeeNumber: string; employeeName: string; projectId: string; projectCode: string; projectName: string; validFrom: string; validTo: string | null; active: boolean };
export type Invitation = { id: string; email: string; role: string; employeeId: string | null; status: string; expiresAt: string; createdAt: string; deliveryStatus: string };
export const adminGet = <T,>(path: string) => backendRequest<T>("/api/v1/admin/" + path);
export function listQuery(params: Record<string, string | string[] | undefined>) {
 const q = new URLSearchParams(); for (const key of ["search", "active", "status", "sort"]) { const v = params[key]; if (typeof v === "string" && v) q.set(key, v); }
 q.set("page", String(Math.max(0, Number(params.page) || 0))); q.set("size", "20"); return q;
}
export function businessTime(value: string) { return new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/Athens", dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
