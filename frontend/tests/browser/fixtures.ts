import { readFileSync } from "node:fs";

// Each seeded spec owns its identities and organizations. Never fall back to a
// developer's credentials or mutate process.env in a concurrently running worker.
export function fixture(name: string): Record<string, string> {
 const path = process.env.BROWSER_FIXTURES_PATH;
 if (!path) throw new Error("Prepare isolated fixtures with infra/ci/stack.mjs seed and run its test command.");
 const manifest = JSON.parse(readFileSync(path, "utf8")) as Record<string, Record<string, string>>;
 const values = manifest[name];
 if (!values) throw new Error(`Missing isolated browser fixture: ${name}`);
 return values;
}
