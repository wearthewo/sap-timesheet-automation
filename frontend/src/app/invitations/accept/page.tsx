"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
export default function InvitationLink() {
 const [error, setError] = useState("");
 useEffect(() => {
  const token = new URLSearchParams(window.location.hash.slice(1)).get("token");
  if (!token) { window.location.replace("/invitations/review"); return; }
  window.history.replaceState(null, "", "/invitations/accept");
  void fetch("/api/invitations/context", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token }) })
   .then(r => { if (!r.ok) throw new Error(); window.location.replace("/invitations/review"); })
   .catch(() => setError("Unable to open this invitation. Try the email link again."));
 }, []);
 return <><h1 className="text-2xl font-bold">Organization invitation</h1>{error ? <p role="alert" className="my-5">{error}</p> : <p role="status" className="my-5">Opening invitation…</p>}<Link href="/login">Go to sign in</Link></>;
}
