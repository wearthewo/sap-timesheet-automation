import { NextRequest, NextResponse } from "next/server";
export const runtime = "nodejs";
export async function POST(request: NextRequest) {
 const app = process.env.NEXTAUTH_URL;
 if (!app) return NextResponse.json({ message: "Application unavailable." }, { status: 503 });
 if (request.headers.get("origin") !== new URL(app).origin) return NextResponse.json({ message: "Forbidden." }, { status: 403 });
 const body = await request.json().catch(() => null);
 if (typeof body?.token !== "string" || !/^[a-f0-9-]{36}\.[A-Za-z0-9_-]{43}$/.test(body.token)) return NextResponse.json({ message: "Invalid invitation link." }, { status: 400 });
 const response = NextResponse.json({ ready: true }, { headers: { "Cache-Control": "no-store" } });
 response.cookies.set("workforce-invitation", body.token, { httpOnly: true, secure: new URL(app).protocol === "https:", sameSite: "lax", path: "/", maxAge: 3600 });
 return response;
}
