"use client";
export default function ErrorPage({ reset }: { reset: () => void }) {
 return <section className="rounded-xl border border-slate-200 bg-white p-8"><h1 className="text-xl font-bold">Unable to load your workspace</h1><p className="my-4 text-slate-600">Please try again in a moment.</p><button className="button" onClick={reset}>Try again</button></section>;
}
