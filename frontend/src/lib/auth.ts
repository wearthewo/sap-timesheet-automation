import "server-only";
import type { NextAuthOptions } from "next-auth";
import KeycloakProvider from "next-auth/providers/keycloak";

const issuer = process.env.KEYCLOAK_ISSUER_URI ?? "http://localhost:8180/realms/workforce";
const internal = process.env.KEYCLOAK_INTERNAL_URL ?? issuer;
export const authOptions: NextAuthOptions = {
 secret: process.env.NEXTAUTH_SECRET,
 session: { strategy: "jwt", maxAge: 30 * 60 },
 pages: { signIn: "/login", error: "/login" },
 providers: [KeycloakProvider({
  clientId: process.env.KEYCLOAK_CLIENT_ID ?? "workforce-web",
  clientSecret: process.env.KEYCLOAK_CLIENT_SECRET ?? "",
  issuer,
  // Discovery advertises browser URLs; explicit endpoints keep container backchannels reachable.
  wellKnown: "",
  httpOptions: { timeout: 10000 },
  authorization: { url: issuer + "/protocol/openid-connect/auth", params: { scope: "openid email profile" } },
  token: internal + "/protocol/openid-connect/token",
  userinfo: internal + "/protocol/openid-connect/userinfo",
  jwks_endpoint: internal + "/protocol/openid-connect/certs",
  checks: ["pkce", "state"],
 })],
 callbacks: {
  async jwt({ token, account }) {
   if (account) {
    token.accessToken = account.access_token;
    token.accessTokenExpires = account.expires_at ?? 0;
   }
   // Never copy tokens into the browser-visible session callback.
   return token;
  },
 },
};
