"use client";
import { useActionState } from "react";
import { acceptInvitation } from "./actions";
export default function AcceptForm() {
 const [state, action, pending] = useActionState(acceptInvitation, {});
 return <form action={action} className="mt-6">{state.error && <p role="alert" className="mb-4 text-red-800">{state.error}</p>}<button className="button" disabled={pending}>{pending ? "Accepting…" : "Accept invitation"}</button></form>;
}
