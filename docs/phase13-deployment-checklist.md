# Phase 13 prerequisites — no deployment authorized
12D prepares infrastructure; see the [12E release verification](phase12-production-readiness-verification.md) for the current evidence and release decisions. Do not provision, purchase, publish DNS or deploy from these documents.

- [ ] Review the local 12E checkpoint and resolve its acceptance gates before pushing or merging. A preservation commit does not establish release acceptance.
- [ ] Close P12E-CI-001: pass all 16 browser tests in one execution and obtain passing GitHub CI for the reviewed revision. The invitation auth-button hydration defect is fixed with three consecutive focused passes; the latest two-worker suite still had one initial `/login` load timeout (15/16 passed). Aggregate Mailpit independently passed 51 exact messages. The previously disk-blocked serial confirmation was not executed. Disk headroom recovered to approximately 60.09 GiB on 21 October; no broad rerun was requested for the preservation checkpoint. Preserve databases/evidence, isolate fixtures and do not combine partial runs.
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

## Evidence required before choosing a host

ARM64 manifest availability and Java/Mock SAP execution do not establish a complete ARM64 application. The final frontend attempt completed frozen installation but timed out during production compilation; Keycloak augmentation also exceeded its caller bound. Resolve frontend/Keycloak build and complete runtime verification before selecting Oracle. If it remains unverified, evaluate the AMD64 fallback; do not purchase either option in Phase12. Confirm provider capacity and quota directly. Oracle Always Free capacity is not guaranteed and idle reclamation can apply.

Planning calculation only, checked against the [published Hetzner adjustment](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/): CX33 is EUR8.49/month excluding IPv4 and VAT in Germany/Finland. At an **assumed**24% VAT it is approximatelyEUR10.53 before IPv4, leaving EUR4.47 of the EUR15 total for IPv4, amortized domain renewal, offsite encrypted storage and SMTP. Actual tax treatment, IPv4 and the other services must be quoted; the total is not yet verified. Oracle eligible compute may be EUR0, but domain/SMTP/offsite costs and availability still need confirmation. No paid services were provisioned.

## Public-launch blockers from12E

- Establish a maintained Spring dependency line and rerun full regression; Boot3.5 OSS support has ended. No commercial support subscription is assumed.
- Resolve or explicitly assess production-image security findings, rebuild and rescan the selected release images. Keep development-only audit findings separate.
- Verify anonymous registration/recovery abuse controls; authenticated application throttling and Keycloak login brute-force protection do not cover every anonymous flow.
- Complete actual domain/DNS/TLS, SMTP sender verification/delivery, offsite restore, alert delivery, firewall/SSH and production secret provisioning.
- Use a clean production database and fresh realm, without QA identities. Existing populated V14 migration requires a separately reviewed current ownership mapping and rehearsal.
- Establish disk-capacity alerts and backup/init failure recovery. Local pg_isready alone does not prove network-authenticated restore connectivity after interrupted initialization.
