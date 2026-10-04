import SignIn from "./sign-in";
export default async function Login({ searchParams }: { searchParams: Promise<{ error?: string; expired?: string }> }) {
 const params = await searchParams;
 return <main className="flex min-h-screen items-center justify-center px-5 py-12">
  <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 shadow-sm sm:p-10">
   <p className="mb-9 text-sm font-bold tracking-widest text-blue-800">WORKFORCE</p>
   <h1 className="text-3xl font-bold tracking-tight">Your work, connected.</h1>
   <p className="mt-4 mb-8 leading-relaxed text-slate-600">Sign in to view your employee profile and the projects assigned to you.</p>
   {params.error && <p role="alert" className="mb-4 text-sm text-red-700">Sign-in was not completed. Please try again.</p>}
   {params.expired && <p className="mb-4 text-sm text-slate-600">Your session has expired. Sign in to continue.</p>}
   <SignIn />
   <p className="mt-6 text-xs leading-5 text-slate-500">Secure access through your organization’s identity provider.</p>
  </section>
 </main>;
}
