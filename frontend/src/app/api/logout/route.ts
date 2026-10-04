import { NextRequest, NextResponse } from "next/server";
export const runtime = "nodejs";
export async function POST(request: NextRequest) {
 const appUrl = process.env.NEXTAUTH_URL;
 if (!appUrl) return new NextResponse("Application URL is not configured", { status: 503 });
 if (request.headers.get("origin") !== new URL(appUrl).origin) return new NextResponse("Forbidden", { status: 403 });
 const issuer = process.env.KEYCLOAK_ISSUER_URI ?? "http://localhost:8180/realms/workforce";
 const logout = new URL(issuer + "/protocol/openid-connect/logout");
 logout.searchParams.set("client_id", process.env.KEYCLOAK_CLIENT_ID ?? "workforce-web");
 logout.searchParams.set("post_logout_redirect_uri", new URL("/login", appUrl).toString());
 const response = NextResponse.redirect(logout, 303);
 for (const cookie of request.cookies.getAll()) {
  if (cookie.name.startsWith("next-auth.") || cookie.name.startsWith("__Secure-next-auth.") || cookie.name.startsWith("__Host-next-auth.")) {
   response.cookies.set(cookie.name, "", { httpOnly: true, secure: cookie.name.startsWith("__"), sameSite: "lax", path: "/", maxAge: 0 });
  }
 }
 return response;
}
