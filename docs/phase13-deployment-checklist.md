# Phase 13 launch prerequisites

**25 October 2026:** selected target is Contabo Cloud VPS 4, 4 shared vCPU / 8 GB / 100 GB SSD, AMD64 Ubuntu 24.04 LTS, EU, one-month billing. No server exists yet. See [current evidence and blockers](phase13-contabo-readiness.md) and the [operator runbook](contabo-deployment.md). Initial scope is an IP-restricted private demo using synthetic records and Mock SAP.

- [x] Confirm remote CI/E2E on `444f4b4` succeeded, including exact Mailpit acceptance. This does **not** cover local Phase 12F commit `77c665e` or later infrastructure changes.
- [x] Preserve Phase 12F acceptance and local application/data.
- [ ] Review, authorize pushing, and obtain exact-revision CI and full E2E for the release. No push/merge is authorized here.
- [ ] Resolve Caddy high findings and Java/OS findings; build and scan final release images. No blanket suppression or security clearance.
- [ ] Confirm Contabo one-month checkout total, VAT, setup/region/IPv4 charges, domain renewal, SMTP and offsite storage within EUR15/month. Advertising is not a final quote.
- [ ] Authorize purchase, provision a clean host, enable MFA, SSH keys, provider firewall and UFW; measure actual memory/disk/CPU.
- [ ] Restrict demo HTTPS to testers and monitor/operator IPs on the provider firewall. Docker published ports bypass ordinary UFW filtering. Check IPv6 too.
- [ ] Configure real app/auth DNS, verify TLS issuance/renewal and exact issuer/callback/logout. No DNS changes have been made.
- [ ] Generate independent secrets; keep encrypted configuration recovery copies and age private keys off-host. Never import QA users/data.
- [ ] Configure Keycloak/application SMTP, sender authentication, mandatory TLS, controlled recipient tests and provider quotas.
- [ ] Configure backup timers, independent offsite storage/retention and alerts; rehearse offsite restore and measure RPO/RTO.
- [ ] Configure GitHub required reviewers with no self-approval where available, registry read-only credentials, SSH host trust and runner ingress. If protection is unavailable, use approved manual operations, never a weaker workflow.
- [ ] Publish scanned AMD64 images only after separate authorization; retain release manifest, scans, revision and migration level.
- [ ] Separately authorize deployment. Verify actual-host health, private endpoints, role workflows, notifications, downloads, restart/recovery, backup alerts and rollback.
- [ ] Before unrestricted SaaS: verify anonymous abuse protection, incident response, privacy/retention requirements and sustainable resource/provider limits. Local testing is not public-launch readiness.

Original V14 upgrades require separate authorization, a current ownership mapping and a disposable restore rehearsal. Existing volumes are never reset. Historical findings remain in [12E verification](phase12-production-readiness-verification.md). ARM64 is no longer a deployment prerequisite.
