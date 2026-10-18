# Phase 13 prerequisites — no deployment authorized
12D prepares infrastructure; 12E remains the final verification checkpoint. Do not provision, purchase, publish DNS or deploy from these documents.

- [ ] Review/commit 12D changes and finish 12E separately.
- [ ] Resolve the documented Spring support/security-advisory strategy and review frontend/tooling/OS image scans. Unused vulnerable features do not replace maintained dependencies.
- [ ] Choose Oracle Always Free ARM64 without PAYG or an AMD64 Hetzner option within the **total EUR15/month** ceiling, including tax, IPv4, domain, storage/backup and SMTP costs. Confirm availability and current provider terms.
- [ ] Verify native target hardware, sustained memory/CPU/disk, cold startup and bounded representative workload. Local emulation is not cloud verification.
- [ ] Acquire authorized hostnames/provider accounts separately; configure DNS, real ACME issuance/renewal and HTTP redirect tests.
- [ ] Establish SSH keys, restricted admin access, patching, firewall and private-network boundaries. Test anonymous abuse protection/public registration limits.
- [ ] Generate production secrets outside Git; document rotation, offline recovery and named Keycloak administrators.
- [ ] Verify SMTP sender/SPF/DKIM/DMARC, authenticated required TLS and actual verification/reset/invitation/notification delivery with permission.
- [ ] Configure reliable local backup scheduling/failure alerts, offsite encrypted transfer and remote retention; exercise full offsite restore on another host with offline keys.
- [ ] Measure backup space, outage duration and recovery objectives; agree RPO/RTO.
- [ ] If using existing V14 data, approve current organization ownership mapping and rehearse both database and realm upgrade from a fresh disposable backup. Never enable legacy confirmation based only on an old fixture report.
- [ ] Verify production callbacks, secure cookies, logout, tenant authorization, reports/downloads, retries/reconciliation and notification privacy.
- [ ] Review logs for action links/tokens/secrets, ensure monitoring actually reaches an operator, and test rollback compatible with schema.
- [ ] Explicitly approve the real deployment. Mock SAP remains clearly labelled simulation; no real SAP or billing is introduced.
