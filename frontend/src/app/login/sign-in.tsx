"use client";
import { signIn } from "next-auth/react";
import { useState } from "react";
export default function SignIn({ registration = false, callbackUrl }: { registration?: boolean; callbackUrl?: "/invitations/review" }) {
 const [pending, setPending] = useState(false);
 const [failed, setFailed] = useState(false);
 return <><button className="button w-full disabled:opacity-60" disabled={pending} onClick={async () => {
  setPending(true); setFailed(false);
  try { await signIn(registration ? "keycloak-register" : "keycloak", { callbackUrl: callbackUrl ?? (registration ? "/onboarding" : "/app") }); }
  catch { setFailed(true); setPending(false); }
 }}>{pending ? "Connecting…" : registration ? "Create an account" : "Sign in with your work account"}</button>
 {failed && <p role="alert" className="mt-3 text-sm text-red-700">Unable to connect. Please try again.</p>}</>;
}
