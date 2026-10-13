"use client";
export default function Error({ reset }: { reset: () => void }) {
 return <main className="mx-auto max-w-xl px-5 py-12"><h1 className="text-2xl font-bold">Unable to load your account</h1>
  <p role="alert" className="my-5">The service is temporarily unavailable. Please try again.</p>
  <button className="button" onClick={reset}>Try again</button>
  <form action="/api/logout" method="post" className="mt-5"><button className="rounded-lg border px-4 py-2">Log out</button></form></main>;
}
