# Production operator runbook

Provider-neutral procedures for the existing AMD64 Ubuntu 24.04 Docker Compose stack. This is an operator reference, not evidence of a live deployment. Review [release prerequisites](public-production-readiness.md), [environment configuration](production-environment.md) and the [signed public release policy](public-production-release-policy.md) before executing commands.

## 1. Release prerequisites

- Require successful CI and full E2E/Mailpit verification for the exact source revision, fresh scans/SBOMs for all seven images, immutable manifests and the assessment required by the externally configured trust policy.
- Use separate application and identity hostnames, authenticated TLS SMTP and independently recoverable encrypted offsite backups. Resend and B2 settings below describe implemented integrations, not hosting recommendations.
- Fresh production uses empty volumes and a fresh realm; never import QA identities. Existing populated data requires an explicit ownership audit and tested migration plan.
- Keep Mock SAP visibly labelled as a simulator; it does not establish production SAP connectivity.

## 2. Host and network preparation (future approved operator actions)

All host commands below run from a reviewed source checkout copied to the **new** host after approval, with root/sudo where shown; do not assume repository files already exist on that host. Create one AMD64 Ubuntu24.04 server only after explicit approval; do not attach original Docker volumes. Upload an Ed25519 public SSH key; retain the private key in the operator's secure store. Check the host fingerprint through an independently authenticated host console before trusting SSH. Enable account MFA and preserve recovery codes offline.

Create a non-root sudo operator from the initial console/root session and verify a **second** key-authenticated sudo session before disabling root/password login:

```sh
adduser workforce-ops
usermod -aG sudo workforce-ops
install -d -m 0700 -o workforce-ops -g workforce-ops /home/workforce-ops/.ssh
# Install the operator's authorized public key, owner workforce-ops, mode0600.
apt-get update
apt-get upgrade
apt-get install -y ca-certificates curl git openssl age ufw unattended-upgrades gnupg util-linux dnsutils rclone
timedatectl set-timezone UTC
timedatectl set-ntp true
install -m 0600 infra/production/ssh-hardening.conf /etc/ssh/sshd_config.d/00-workforce.conf
sshd -t
sshd -T | grep -E 'permitrootlogin|passwordauthentication|kbdinteractiveauthentication|pubkeyauthentication'
# Only after the second sudo/key session works:
systemctl reload ssh
```

The template disables SSH forwarding; an explicitly reviewed management-tunnel exception is required if later needed. Inspect effective settings because cloud-init includes can override expectations. Do not lose console recovery access. Keep host security updates enabled; schedule reboots and test Docker recovery. Set bounded journald retention (for example `SystemMaxUse=256M`) in a reviewed drop-in.

Use an external network firewall and the host firewall. Inbound: TCP22 **only from operator addresses**, TCP80/443 for approved HTTPS service use; no5432/8080/9000/3000/8090/SMTP management exposure. Review IPv4 **and IPv6**; omit AAAA until verified. The selected deployment is public: allow80/443 to visitors, with no visitor IP allowlist. Preserve operator-only SSH. Do not make Keycloak administration or Mailpit public.

```sh
# Replace the documentation address with your actual operator CIDR before running.
ufw default deny incoming
ufw default allow outgoing
ufw allow from 192.0.2.10/32 to any port 22 proto tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
ufw status verbose
```

Docker-published ports can bypass UFW: the cloud firewall and the Compose rule publishing **only the proxy** are essential. See [Docker firewall guidance](https://docs.docker.com/engine/network/packet-filtering-firewalls/). Leave Docker packet filtering enabled. Outbound needs DNS, NTP, HTTPS registries/ACME/monitoring, provider SMTP587 and B2 HTTPS. Do not guess an egress deny list before testing those paths.

Install Docker Engine, Buildx and Compose plugin through the [official Ubuntu apt repository instructions](https://docs.docker.com/engine/install/ubuntu/), verifying the repository key and selected package versions. Do not use an unaudited curl-to-shell installer. Confirm `docker version` and `docker compose version`. Build on GitHub/off-host, not on the serving 12 GB machine. Install the repository's exact `.nvmrc` Node24 version from its official archive after verifying its signed checksums; keep it under `/opt/workforce/node` with a root-owned `/usr/local/bin/node` link. Docker-group membership is root-equivalent; do not grant it to application users.

### Docker installation on the new Ubuntu 24.04 host

Run only after host provisioning is separately approved, from a verified root/sudo session. Do not execute on the development machine. The [official Docker apt repository](https://docs.docker.com/engine/install/ubuntu/) supplies Engine, Buildx and the Compose plugin:

```sh
. /etc/os-release
test "$ID" = ubuntu && test "$VERSION_ID" = 24.04
test "$(dpkg --print-architecture)" = amd64
install -m 0755 -d /etc/apt/keyrings
curl --fail --show-error --silent --location https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
# Inspect the key fingerprint against Docker's independent official documentation before proceeding.
gpg --show-keys --fingerprint /etc/apt/keyrings/docker.asc
cat > /etc/apt/sources.list.d/docker.sources <<'SOURCES'
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
SOURCES
apt-get update
apt-cache policy docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
# Review the candidate versions shown above, then install and record them.
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
docker version
docker compose version
```

Install `gnupg` and `util-linux` with the earlier host packages. For a **new host without an existing daemon configuration**, set bounded defaults before any application starts:

```sh
test ! -e /etc/docker/daemon.json
install -d -m 0755 /etc/docker
printf '%s\n' '{"log-driver":"local","log-opts":{"max-size":"10m","max-file":"3"}}' > /etc/docker/daemon.json
dockerd --validate --config-file=/etc/docker/daemon.json
systemctl restart docker
install -d -m 0755 /etc/systemd/journald.conf.d
printf '%s\n' '[Journal]' 'SystemMaxUse=256M' > /etc/systemd/journald.conf.d/workforce.conf
systemctl restart systemd-journald
```

If configuration exists, merge it deliberately instead of overwriting. Compose also imposes per-container log bounds. Do not run builds on this VPS. Copy a reviewed exact-revision source checkout to `/opt/workforce/releases/<SHA>` as root, verifying SHA and a clean tracked tree before using any scripts. Scripts cannot create a server, invent a release SHA or approve a release.

### Domain, TLS and public access

Fill `APP_HOST`, `AUTH_HOST` and `TLS_CONFIG` only after selecting a domain/contact. Create two A records to the approved VPS IPv4. Add AAAA only after IPv6 routing/firewall is verified. Check authoritative resolution with `dig +short A YOUR_APP_HOST` and `dig +short A YOUR_AUTH_HOST`.

Apply the external network firewall **before** first service startup: TCP22 from operator addresses only; TCP80/443 publicly reachable for HTTPS, ACME and redirects. No visitor allowlist or VPN. Allow necessary ICMP/IPv6 control traffic per provider guidance, but no additional application/admin ports. Docker port publication can bypass ordinary UFW forwarding rules; verify provider and host policies externally for IPv4 and IPv6. Caddy must serve both hosts with valid certificates, never a production test CA. The repository ingress guard supplies bounded request/concurrency limits; it is not upstream DDoS protection.

Caddy stores certificates/renewal state in persistent `caddy_data` and `caddy_config`. Public HTTP redirects to HTTPS. Expected issuer is exactly `https://YOUR_AUTH_HOST/realms/workforce`; callbacks are `https://YOUR_APP_HOST/api/auth/callback/keycloak` and `/api/auth/callback/keycloak-register`; logout returns `/login`. The production realm already uses these two exact callbacks, no wildcard. Keycloak trusts only the fixed Caddy ingress address, and Caddy overwrites untrusted forwarding headers. Backend and management ports have no published mapping.

After authorized startup, from an external workstation:

```sh
curl --fail --show-error --silent --head http://YOUR_APP_HOST/login
curl --fail --show-error --silent https://YOUR_APP_HOST/api/health
curl --fail --show-error --silent https://YOUR_AUTH_HOST/realms/workforce/.well-known/openid-configuration
# Expect 404, not an administrator console:
curl --silent --output /dev/null --write-out '%{http_code}\n' https://YOUR_AUTH_HOST/admin/
```

Never use `-k` for production acceptance. Test certificate expiry monitoring and renewal after DNS/firewall changes. Recovery/administration remains an operator-only operation; do not open Keycloak `/admin` publicly. Use a reviewed temporary local tunnel exception or container-local admin CLI without logging credentials.

## 3. Files, secrets and SMTP

Use root-owned `/opt/workforce/releases/<full-40-character-SHA>` checkouts, `/opt/workforce/tools`, `/etc/workforce`, `/var/backups/workforce` and `/run/workforce`. Keep the `workforce-prod` Compose project name fixed so release changes retain the same four volumes: `postgres_data` (both databases), `mock_sap_data`, `caddy_data`, `caddy_config`. No `down -v`, pruning, database resets or automatic volume recreation.

```sh
install -d -m 0700 /etc/workforce /var/backups/workforce /run/workforce
install -d -m 0755 /opt/workforce/releases /opt/workforce/tools
install -m 0600 infra/production/production.env.example /etc/workforce/production.env
install -m 0600 infra/production/operations.env.example /etc/workforce/operations.env
bash infra/production/init-secrets.sh
```

Fill the non-secret environment templates with approved values, `PLATFORM=linux/amd64` and a reviewed `RELEASE_TAG` (the deployment override uses actual digests). `init-secrets.sh` refuses any existing secret directory and generates eight independent secrets without printing them. Supply `SMTP_PASSWORD` from the SMTP provider separately. Each file is mounted individually read-only; mode0444 files inside a root0700 directory support nonroot container reads. Root/Docker operators can still access secrets. Never print resolved Compose config, `docker inspect` environments, shell tracing or token-bearing HTTP logs. Preserve an encrypted independent recovery copy of secrets/configuration.

Both Keycloak account mail and application invitations/notifications use the same SMTP host/username/password/sender, STARTTLS and port587. Application connect/read/write timeouts default to3000ms each; Keycloak provider timeout behavior must be verified for the pinned release before external-provider acceptance. No unsupported SMTP property is invented here. Confirm the host network and SMTP service permit outbound587; no SMTP reachability has been measured. Configure verified sender/domain, SPF, DKIM and DMARC with the chosen provider. Do not self-host an SMTP server. Provider credentials must satisfy existing secret validation (32 characters in preflight); if the chosen provider cannot supply that, review this compatibility explicitly rather than bypass validation or invent a password.

Keycloak imports SMTP settings **only for a new realm**. Existing realms require an explicitly reviewed admin update; replacing an environment variable does not update an imported client or password. Bootstrap admin credentials are recovery material, not employee logins. Establish named operators and remove/restrict bootstrap access using Keycloak's documented procedure. Its public proxy intentionally excludes `/admin`, master realm and management ports.

Before public registration: test real-provider registration verification, password recovery, employee invitation and notification delivery to controlled consenting test addresses; verify From, links, TLS and spam placement. No external emails are sent during repository preparation. SMTP ambiguous acceptance can produce duplicate physical delivery; there is no exactly-once SMTP guarantee.

## 4. Release images and manual approvals

`production-publish.yml` is manual-only, main-only and requires typed `PUBLISH PUBLIC PRODUCTION` plus the `production-publish` environment. After exact-revision CI/E2E, prepare all seven AMD64 archives with the shared candidate workflow using `policy: public-saas`. Publication verifies a signed public assessment under the explicitly configured independent or solo-maintainer trust mode, retained raw scans/SBOMs and archive hashes, then copies those exact OCI bytes without rebuilding. Unassessed findings and scanner errors fail closed. See [public release assessment](public-production-release-policy.md) for the required inputs, trust configuration and evidence installation. No current finding has an approval. A partially failed push is not deployable: deployment requires a successful publishing run and its complete manifest.

`production-deploy.yml` is manual-only, main-only, requires typed `DEPLOY`, a successful publishing run ID and the separate `production` environment. It revalidates source gates and release provenance and sends only the digest manifest through SSH. Neither workflow runs on pull requests or ordinary pushes. No production images have been published by this checkpoint.

Restrict deployment branches to main and disallow bypass where supported. Independent mode requires required reviewers and prevention of self-review. For the single-maintainer workflow, explicitly configure schema 2 `approvalMode: solo-maintainer` in `PUBLIC_PRODUCTION_TRUST_JSON` in both environments and the host trust file, following [single-maintainer setup](public-production-release-policy.md#single-maintainer-setup-and-operation). This mode requires manual main dispatch and an owner-signed exact-bundle assessment; no second reviewer is required. Solo mode cannot approve residual-risk waivers. The candidate job also uses `production-publish` to read its external trust. [GitHub environment availability depends on plan/repository visibility](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments). If required settings are unavailable, leave automation disabled; do not bypass checks. A workflow file alone does not configure trust or approve findings.

Production environment inputs: `DEPLOY_HOST` variable; `DEPLOY_SSH_KEY` and independently verified `DEPLOY_KNOWN_HOSTS` secrets. Registry writes use the job-scoped GitHub token. On the VPS, private GHCR pulls require a separately protected **read:packages-only** credential entered via `docker login --password-stdin`, never a repository write token. Decide package visibility deliberately; publishing workflow must not unexpectedly expose a private project.

Install the reviewed source checkout at `/opt/workforce/releases/<SHA>` before invoking deployment. Check `git rev-parse HEAD` and tracked-file cleanliness. Install reviewed `release.mjs`, `enforce-release-policy.mjs`, `private-demo-policy.mjs` and `public-production-policy.mjs` together under `/opt/workforce/tools/`, and `deploy-release.sh` as root0755 `/usr/local/sbin/workforce-deploy`. Install its helper `validate-env.mjs` only through the reviewed source checkout. Keep source/tool directories non-writable by the deployment account. Root must explicitly install `/etc/workforce/release-policy.json` with `mode: public-saas` and independently configured public reviewer trust, plus the signed evidence described in [the public operator guide](public-production-release-policy.md). Mode alone no longer authorizes public deployment. The externally trusted private configuration remains separate; private approvals never authorize public deployment. Missing policy, trust or evidence fails closed.

For remote automation, create an unprivileged `workforce-deploy` account. Its authorized key must use `restrict,command="sudo /usr/local/sbin/workforce-deploy"`; sudoers permits only `/usr/local/sbin/workforce-deploy` **with no arguments** (`""` argument restriction), validated with `visudo -cf`. No password, interactive shell access, agent forwarding or generic sudo/docker membership for that account. The release JSON is bounded and validated; source must already be installed by an operator. Protect this key as production authority.

Example setup, after source and key review (substitute a public key, never a private key):

```sh
useradd --create-home --shell /bin/bash workforce-deploy
usermod --lock workforce-deploy
chown root:root /home/workforce-deploy
chmod 0755 /home/workforce-deploy
install -d -m 0755 -o root -g root /home/workforce-deploy/.ssh
# root owns the public authorized_keys file so the restricted user cannot replace it.
printf '%s\n' 'restrict,command="sudo /usr/local/sbin/workforce-deploy" ssh-ed25519 REPLACE_WITH_APPROVED_PUBLIC_KEY' > /home/workforce-deploy/.ssh/authorized_keys
chmod 0644 /home/workforce-deploy/.ssh/authorized_keys
printf '%s\n' 'workforce-deploy ALL=(root) NOPASSWD: /usr/local/sbin/workforce-deploy ""' > /etc/sudoers.d/workforce-deploy
chmod 0440 /etc/sudoers.d/workforce-deploy
visudo -cf /etc/sudoers.d/workforce-deploy
install -m 0644 infra/production/release.mjs /opt/workforce/tools/release.mjs
install -m 0644 infra/production/enforce-release-policy.mjs /opt/workforce/tools/enforce-release-policy.mjs
install -m 0644 infra/production/private-demo-policy.mjs /opt/workforce/tools/private-demo-policy.mjs
install -m 0644 infra/production/public-production-policy.mjs /opt/workforce/tools/public-production-policy.mjs
install -m 0755 infra/production/deploy-release.sh /usr/local/sbin/workforce-deploy
```

Test rejection of a malformed manifest first; confirm no Docker mutation occurred. Review a real release inventory only after scans and GitHub approval. The checkout and all parent directories must be root-owned/non-writable by the deployment account. Do not enter the example placeholder as a real key.

GitHub-hosted runners lack a fixed single source IP. Do not open SSH globally to make the workflow work. Choose an approved temporary/narrow runner ingress policy or perform the same release command from the allowlisted operator workstation. Fixed-egress runner services are not required by this implementation. Host fingerprints must come through an independent channel; `ssh-keyscan` alone is not trust.

```sh
# Future explicitly approved manual alternative; manifest from the successful publishing artifact.
node infra/production/release.mjs check release.json
ssh -T workforce-deploy@YOUR_APPROVED_HOST sudo /usr/local/sbin/workforce-deploy < release.json
```

The host validates the manifest, source, secret files and PostgreSQL pin, pulls exact digests, and takes a coordinated encrypted/offsite backup for upgrades. It stops writers before starting the new release, uses `--no-build` and a bounded health wait, then switches `/opt/workforce/current` only after success. A first deployment refuses preexisting production containers/volumes without a current release pointer. It never guesses legacy ownership or automatically restores/downgrades data. No host script can prove image security merely from a digest: operator provenance/scan review remains mandatory.

## 5. Automated encrypted backups and independent monitoring

Generate age recipient/identity pairs **off the VPS**. Put only public recipients in `/etc/workforce/backup-recipients.txt`; keep private keys and config recovery copies offline in two independently recoverable places. Install the B2 rclone template under `/etc/workforce/offsite`0700 with file mode0600. Use a private bucket, bucket/prefix-scoped read/write/list key and actual regional HTTPS endpoint. No remote-delete permission or age private key is required on the application host. Set `BACKUP_TRANSPORT=b2` and the destination in operations.env. Existing SSH offsite transport remains an alternative, not the selected provider.

`scheduled-backup.sh` serializes against deployment, invokes existing quiesced backups, then copies the encrypted archive/checksum. `offsite-b2.sh` verifies the local checksum, uploads ciphertext immutably, downloads ciphertext and verifies SHA256 before updating success. Local retention defaults to14days. Apply the reviewed B2 lifecycle template separately: retain30days, hide, then delete one day later. Size all object versions and extra upgrade backups against the provider allowance; do not assume free storage is unlimited. No destructive remote cleanup runs on the VPS.

```sh
install -m 0644 infra/production/systemd/workforce-* /etc/systemd/system/
systemd-analyze verify /etc/systemd/system/workforce-{backup,monitor}.{service,timer}
systemctl daemon-reload
# After initial release, recovery-key storage and destinations have been validated:
systemctl start workforce-backup.service
systemctl status workforce-backup.service
systemctl enable --now workforce-backup.timer workforce-monitor.timer
systemctl list-timers 'workforce-*'
```

For an immediate production backup, use `systemctl start workforce-backup.service` so the maintenance lock also covers offsite copying. Do not run the lower-level `ops.sh backup` concurrently with deployment; it only has a project backup lock. Restore exercises use a separate isolated target and explicit maintenance control.

Backup runs at02:30UTC plus0–5min jitter, with `Persistent=true` and a20min bound. It briefly stops writers; this is planned downtime. The monitor runs every5min and checks six health states; public HTTPS and exact OIDC issuer; local/offsite backup freshness26h; root/Docker/backup filesystems80%; host memory512MiB; five-minute load above twice visible CPUs; FAILED/UNKNOWN SAP, failed notifications, work overdue15min and expired5min claims. SQL uses a read-only transaction with a5s statement bound and emits only aggregate counts. Alerts remain active until the underlying condition is resolved; no automatic acknowledgement hides terminal failures. It sends a success-only heartbeat to an operator-configured HTTPS dead-man service using root0600 `monitor-success.curl`; no endpoint token enters command-line logs. Configure the remote service to alert after missed pulses with grace for the maintenance window, and test alert delivery, including total VPS loss. The remote service and alert channel are not provisioned here. Local journal failures alone are insufficient alerting. Review `ops.sh status` and `docker stats --no-stream` for diagnosis. Count-based monitoring is not business acceptance or a capacity guarantee. Deploy outcome records are appended to root-only `/var/log/workforce/deployments.log`; configure host log retention (e.g. logrotate) for this small file.

Download a real encrypted offsite copy to an **empty isolated recovery host** and follow [backup/restore](backup-restore.md). Validate checksum, decryption, both DB manifests, Flyway, memberships, historical approvals/exports, Mock SAP identity persistence, login and worker reconciliation. Do not rehearse on the original V14 or local QA data. Test duplicate restore refusal. Record duration and maximum acceptable data loss; neither RPO nor RTO is guaranteed by a timer.

## 6. Startup acceptance, resource use and rollback

### Acceptance sequence

1. Obtain the exact-revision CI/E2E, image evidence and signed assessment required by the public release policy. Do not use private-demo approvals for a public release.
2. Verify host SSH trust, public DNS, root-owned secrets/configuration and release-policy trust parity. Use a clean database/realm for first deployment; populated data requires a separate migration plan.
3. Verify SMTP certificate chains and hostnames from the actual host before sending credentials. Production uses port 587 STARTTLS. OpenSSL checks do not replace delivery through the actual Java runtimes. Verify registration, recovery, invitations and notifications using controlled recipients. Never import a workstation interception CA or disable TLS validation.
4. After deployment, verify all six services, both HTTPS hosts, exact OIDC issuer/callback/logout, secure cookies, tenant isolation, registration limits and IPv4/IPv6 port exposure.
5. Exercise encrypted offsite upload/read-back and an isolated restore, alert delivery, reboot recovery and schema-compatible rollback. Record measured resource use and recovery time. Registration is accepted only after these checks pass.

Keep existing service ceilings: proxy256MiB, PostgreSQL1536MiB, Keycloak1280MiB, backend1536MiB, frontend768MiB, Mock SAP256MiB = **5632MiB/5.5GiB**. Backup512MiB runs while writers are stopped. An advertised12GB may not mean12GiB; inspect actual MemTotal. Leave at least OS/cache/headroom and measure peak usage. CPU ceilings total6vCPU but are not reservations. Do not run builds/tests on the serving host or assume user capacity. Avoid unrestricted swap holding decrypted backup data; if needed, review encrypted swap separately.

Acceptance after deployment: verify all six healthy, actual RAM/disk/OOM/restarts, only intended public ports from an external host, valid certificates for both domains, exact OIDC issuer/callback/logout, registration/email verification, organization isolation, employee→manager→finance flow, downloads, local/offsite backup success and independent alert delivery. Mock SAP remains simulated. Test reboot recovery once authorized. Never expose plaintext backup, database, Keycloak admin or container-management endpoints.

Record release.json, source SHA, migration level, Keycloak version and archive ID before every upgrade. Review schema/Keycloak compatibility before approving an older release. On failed health wait, keep evidence and inspect safe logs; the deploy script does **not** automatically restore or rewind the database. If schema-compatible, use the reviewed prior digest inventory/source via the same approved path. If incompatible, restore the coordinated archive to **new volumes on an isolated target**, validate, then switch ingress explicitly. Never run down-v, drop schemas or downgrade Keycloak against an upgraded database to force rollback. A stopped/failed rollout requires operator action; a green health check is followed by business acceptance.

## 7. Runtime and local acceptance references

Keycloak now uses the reviewed AMD64 source-build definition described in [Keycloak adoption](phase13-keycloak-adoption.md), with pinned Corretto and Guava inputs. Build it in release CI, not on the VPS. Budget for upstream compilation and retain final image/authentication evidence; the old official-image or experimental-image scan cannot authorize this new image. Do not apply the obsolete UBI trust-store paths to the Corretto runtime. The production realm, secret files, health checks and data ownership contract remain the same. No additional hosting service or runtime configuration is required by this adoption.

The preserved app is **http://localhost:33000**, with Mailpit **http://localhost:38025**. Use [the GUI manual](local-browser-testing-guide.md) and [Phase 12F evidence](phase12f-browser-acceptance.md). Roles belong to organization memberships. No acceptance rerun, environment recreation, data reset or external email was performed here.
