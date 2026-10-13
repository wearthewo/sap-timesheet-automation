"use client";
export default function ErrorPage({ reset }: { reset: () => void }) { return <section role="alert" className="rounded-xl border bg-white p-6"><h2 className="font-semibold">Unable to load administration</h2><p className="my-3">Check your connection and organization access, then try again.</p><button className="button" onClick={reset}>Try again</button></section>; }
