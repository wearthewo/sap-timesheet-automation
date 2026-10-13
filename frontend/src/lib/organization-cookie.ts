import "server-only";
import { cookies } from "next/headers";

export async function selectOrganizationCookie(id: string) {
 (await cookies()).set("workforce-organization", id, {
  httpOnly: true, secure: new URL(process.env.NEXTAUTH_URL ?? "http://localhost:3000").protocol === "https:",
  sameSite: "lax", path: "/",
 });
}
