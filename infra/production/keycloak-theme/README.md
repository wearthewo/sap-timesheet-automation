# Narrow Keycloak 26.8.1 registration correction

The `workforce` login theme inherits `keycloak.v2`. Its only override is upstream
`password-validation.ftl`, with one change: optional chaining before attaching
the password-field listener. The email-first registration step intentionally has
no password field. When a password field exists, validation is unchanged.
Server-side password policy, email verification, OIDC, consent and form submission
are untouched. No global browser-error handler or test exception is introduced.

Upstream source: Keycloak 26.8.1, Apache License 2.0.
https://github.com/keycloak/keycloak/blob/26.8.1/themes/src/main/resources/theme/keycloak.v2/login/password-validation.ftl

Exact original file SHA256 from the pinned vendor jar:
`a7a7714a846e89d205f82e0cf1987c998f9e67988879906cb7ff90234db2aef1`.
`keycloak-theme.test.mjs` reverses the one-character correction and checks that
hash so unrelated template changes require explicit review.

Fresh production realm imports select `loginTheme=workforce`. Keycloak does not
overwrite an existing realm on startup: an operator must explicitly select this
theme for any already-created deployment realm after compatibility verification.
No existing local realm is changed by this repository update.

On a future vendor upgrade, inspect its template and remove this override once
the vendor handles absent fields correctly. Do not keep a stale full template
across upgrades without comparison. The licensing notice and license are retained
alongside this modified vendor-derived file.
