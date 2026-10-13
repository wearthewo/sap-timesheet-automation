import type { Metadata } from "next";
// Same-origin form navigation must retain Origin for the existing logout CSRF check.
// Invitation bearers live in a fragment/HttpOnly cookie, never a referrer URL.
export const metadata: Metadata = { referrer: "same-origin", robots: { index: false, follow: false } };
export default function InvitationLayout({ children }: { children: React.ReactNode }) { return <main className="mx-auto max-w-xl px-5 py-12"><p className="mb-6 text-sm font-bold tracking-widest text-blue-800">WORKFORCE</p><section className="rounded-xl border bg-white p-6 sm:p-9">{children}</section></main>; }
