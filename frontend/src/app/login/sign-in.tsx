"use client";
import { signIn } from "next-auth/react";
import { useState } from "react";
export default function SignIn() {
 const [pending, setPending] = useState(false);
 const [failed, setFailed] = useState(false);
 return <><button className="button w-full disabled:opacity-60" disabled={pending} onClick={async () => {
  setPending(true); setFailed(false);
  try { await signIn("keycloak", { callbackUrl: "/app" }); }
  catch { setFailed(true); setPending(false); }
 }}>{pending ? "Connecting…" : "Sign in with your work account"}</button>
 {failed && <p role="alert" className="mt-3 text-sm text-red-700">Unable to connect. Please try again.</p>}</>;
}
