import { identityProfile } from "@/lib/backend";

export default async function OnboardingLayout({ children }: { children: React.ReactNode }) {
 // Authenticate before the page's loading boundary can start streaming its response.
 await identityProfile();
 return children;
}
