# Contabo AMD64 deployment runbook

Selected target: **Contabo Cloud VPS 6, 12 GB RAM**, AMD64, Ubuntu 24.04 LTS. Public website and self-registration, Namecheap DNS, Resend SMTP and Backblaze B2 encrypted backups. Confirm CPU/disk/invoice details at checkout; this is not a quoted price. No VPS has been purchased. This runbook is **not authorization to buy, provision, publish, change DNS or deploy**. [Public-production readiness](public-production-readiness.md) is the current launch decision. Use the existing `infra/production` stack, not development/CI Compose.

## 1. Go/no-go before spending money

- Confirm the exact approved source revision has green core CI **and** full E2E/Mailpit verification. Review production image scans, Caddy/JDK/OS findings and a completed release digest inventory.
- Obtain a final **one-month** quote including VAT, setup, region and IPv4, domain renewal amortized monthly, SMTP and independent backup storage. [Official Contabo specifications](https://docs.contabo.com/docs/servers-hosting/vps/) match the plan. The [sales page](https://contabo.com/en-us/vps/) displays long-term promotions, not a verified one-month checkout. EUR6.82 is historical, not a confirmed total. Do not select a 24-month term or extras to match advertising. Record the first invoice, recurring invoice and renewal rates before purchase. A directory on the same VPS is not offsite storage.
- Use two Namecheap DNS names, Resend authenticated STARTTLS on port587, private Backblaze B2 storage and an independent dead-man monitor. The repository does not provision these accounts. Confirm free-tier storage/email limits and the actual recurring total before purchase.
- Fresh production starts with empty volumes and a fresh Keycloak realm. Never import QA identities or original V14 data. A populated legacy migration needs a separate ownership audit and explicit authorization.
- Keep Mock SAP visibly labelled a simulator. A public website does not establish real SAP connectivity.

## 2. Host and network preparation (future approved operator actions)

All host commands below run from a reviewed source checkout copied to the **new** host after approval, with root/sudo where shown; do not assume repository files already exist on that host. Create one AMD64 Ubuntu24.04 server only after explicit approval; do not attach original Docker volumes. Upload an Ed25519 public SSH key; retain the private key in the operator's secure store. Check the host fingerprint through Contabo's console before trusting SSH. Enable account MFA and preserve recovery codes offline.

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

Use both the included [Contabo network firewall](https://docs.contabo.com/docs/network-services/firewall/) and host firewall. Inbound: TCP22 **only from operator addresses**, TCP80/443 for approved HTTPS service use; no5432/8080/9000/3000/8090/SMTP management exposure. Review IPv4 **and IPv6**; omit AAAA until verified. The selected deployment is public: allow80/443 to visitors, with no visitor IP allowlist. Preserve operator-only SSH. Do not make Keycloak administration or Mailpit public.

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

Attach the Contabo provider firewall **before** first service startup: TCP22 from operator addresses only; TCP80/443 publicly reachable for HTTPS, ACME and redirects. No visitor allowlist or VPN. Allow necessary ICMP/IPv6 control traffic per provider guidance, but no additional application/admin ports. Docker port publication can bypass ordinary UFW forwarding rules; verify provider and host policies externally for IPv4 and IPv6. Caddy must serve both hosts with valid certificates, never a production test CA. The repository ingress guard supplies bounded request/concurrency limits; it is not upstream DDoS protection.

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

Both Keycloak account mail and application invitations/notifications use the same SMTP host/username/password/sender, STARTTLS and port587. Application connect/read/write timeouts default to3000ms each; Keycloak provider timeout behavior must be verified for the pinned release before external-provider acceptance. No unsupported SMTP property is invented here. Confirm Contabo and the chosen provider permit outbound587; no SMTP reachability has been measured. Configure verified sender/domain, SPF, DKIM and DMARC with the chosen provider. Do not self-host an SMTP server. Provider credentials must satisfy existing secret validation (32 characters in preflight); if the chosen provider cannot supply that, review this compatibility explicitly rather than bypass validation or invent a password.

Keycloak imports SMTP settings **only for a new realm**. Existing realms require an explicitly reviewed admin update; replacing an environment variable does not update an imported client or password. Bootstrap admin credentials are recovery material, not employee logins. Establish named operators and remove/restrict bootstrap access using Keycloak's documented procedure. Its public proxy intentionally excludes `/admin`, master realm and management ports.

Before public registration: test real-provider registration verification, password recovery, employee invitation and notification delivery to controlled consenting test addresses; verify From, links, TLS and spam placement. No external emails are sent during repository preparation. SMTP ambiguous acceptance can produce duplicate physical delivery; there is no exactly-once SMTP guarantee.

## 4. Release images and manual approvals

`production-publish.yml` is manual-only, main-only and requires typed `PUBLISH` plus the `production-publish` environment. It checks exact-revision CI/E2E, builds and scans all seven AMD64 images, then pushes only after the scan gate passes. All UNKNOWN/high/critical findings currently block publication, including documented vendor-qualified findings; no blanket ignore list is installed. Review fixes or any future narrowly justified scan policy in a separate change. Scanner errors also fail. A partially failed push is not deployable: deployment requires a successful publishing run and its complete manifest.

`production-deploy.yml` is manual-only, main-only, requires typed `DEPLOY`, a successful publishing run ID and the separate `production` environment. It revalidates source gates and release provenance and sends only the digest manifest through SSH. Neither workflow runs on pull requests or ordinary pushes. No production images have been published by this checkpoint.

Configure required reviewers, prevent self-review, restrict deployment branches to main, and disallow bypass where supported. The scripts verify reviewer policy and fail closed if absent. [GitHub environment protection availability depends on plan/repository visibility](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments). **Do not purchase a plan just for this.** If unavailable, leave automated publication/deployment disabled and use an explicitly approved manual operator build/scan/push/deploy with the same checks and evidence. A workflow file alone does not create or enforce repository settings.

Production environment inputs: `DEPLOY_HOST` variable; `DEPLOY_SSH_KEY` and independently verified `DEPLOY_KNOWN_HOSTS` secrets. Registry writes use the job-scoped GitHub token. On the VPS, private GHCR pulls require a separately protected **read:packages-only** credential entered via `docker login --password-stdin`, never a repository write token. Decide package visibility deliberately; publishing workflow must not unexpectedly expose a private project.

Install the reviewed source checkout at `/opt/workforce/releases/<SHA>` before invoking deployment. Check `git rev-parse HEAD` and tracked-file cleanliness. Install reviewed `release.mjs`, `enforce-release-policy.mjs` and `private-demo-policy.mjs` together under `/opt/workforce/tools/`, and `deploy-release.sh` as root0755 `/usr/local/sbin/workforce-deploy`. Install its helper `validate-env.mjs` only through the reviewed source checkout. Keep source/tool directories non-writable by the deployment account. Root must explicitly install `/etc/workforce/release-policy.json`: `{"mode":"public-saas"}` for the unchanged strict path, or the externally trusted private configuration described in [the private-demo operator guide](private-demo-release-policy.md). Missing policy fails closed. Private deployment also requires the reviewed evidence directory and current independent signature; it is not enabled by choosing a workflow input alone.

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
install -m 0755 infra/production/deploy-release.sh /usr/local/sbin/workforce-deploy
```

Test rejection of a malformed manifest first; confirm no Docker mutation occurred. Review a real release inventory only after scans and GitHub approval. The checkout and all parent directories must be root-owned/non-writable by the deployment account. Do not enter the example placeholder as a real key.

GitHub-hosted runners lack a fixed single source IP. Do not open SSH globally to make the workflow work. Choose an approved temporary/narrow runner ingress policy or perform the same release command from the allowlisted operator workstation. Fixed-egress runner services may violate the budget and are not introduced. Host fingerprints must come through an independent channel; `ssh-keyscan` alone is not trust.

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

### October 30 readiness gate and October 31–November 1 plan

The dates are targets, not authorization or a security exception. **Do not purchase or deploy while `public-production-readiness.md` remains NO-GO.**

1. **Friday, October 30 — repository only:** close the exact image findings, obtain independent review of any not-affected package evidence, review the final commit, and obtain separate authorization to push. Require exact-revision CI/E2E and strict scans of all seven final images. No private-demo approvals apply. Configure independent GitHub environment reviewers; authorize publication separately. Retain reports and immutable digests. Stop if any required gate fails.
2. **Saturday, October 31 — only after clearance and purchase authorization:** obtain the selected 12 GB AMD64 host and domain, verify console SSH fingerprints, then follow sections 1–4 for Ubuntu, SSH, Docker, secret files and public network setup. Use a clean database and realm. Set `/etc/workforce/release-policy.json` to `{"mode":"public-saas"}`. Install reviewed, root-owned configuration; do not copy local QA credentials or databases. Configure Namecheap A records; add AAAA only after IPv6 exposure checks. Verify Resend's exact DNS records and private B2 credentials/lifecycle using provider interfaces.
3. **Clean-host SMTP preflight:** no local Avast CA and no TLS bypass. Before using credentials, check both certificate chains from the VPS:

   ```sh
   timeout 20 openssl s_client -starttls smtp -connect smtp.resend.com:587 -servername smtp.resend.com -verify_hostname smtp.resend.com -verify_return_error </dev/null
   timeout 20 openssl s_client -connect smtp.resend.com:465 -servername smtp.resend.com -verify_hostname smtp.resend.com -verify_return_error </dev/null
   ```

   Production uses port 587 STARTTLS. OpenSSL success is necessary but does not replace actual Java-runtime delivery checks. Verify account registration/recovery through Keycloak and invitations/notifications through Workforce with consenting synthetic recipients. A chain containing `Avast Web/Mail Shield Root` indicates interception; investigate the environment without importing that CA or disabling verification.
4. **Authorized deployment:** use the reviewed publication manifest and workflow described above. SSH must remain operator-restricted; resolve the GitHub runner ingress decision before triggering deployment. Verify all six services, both public HTTPS hosts, exact OIDC issuer/callbacks, secure cookies, tenant isolation, registration rate limits and IPv4/IPv6 port isolation. Mock SAP remains a simulator. Registration is not publicly accepted until these checks pass.
5. **Sunday, November 1 — acceptance:** run the first encrypted B2 backup, read-back and isolated offsite restore, check both database manifests and history, enable timers and test missed-heartbeat alerts. Record actual disk/RAM, SMTP quota consumption and recovery time. Rehearse reboot and rollback as described below. Finish with a written public-launch decision; defer launch if TLS, mail, backups, alerts, security or authorization checks fail.

If the Friday security gate remains blocked, postpone purchasing/deployment instead of spending the weekend weakening gates. No step above has been executed against a real host or provider account.

Keep existing service ceilings: proxy256MiB, PostgreSQL1536MiB, Keycloak1280MiB, backend1536MiB, frontend768MiB, Mock SAP256MiB = **5632MiB/5.5GiB**. Backup512MiB runs while writers are stopped. An advertised12GB may not mean12GiB; inspect actual MemTotal. Leave at least OS/cache/headroom and measure peak usage. CPU ceilings total6vCPU but are not reservations. Do not run builds/tests on the serving host or assume user capacity. Avoid unrestricted swap holding decrypted backup data; if needed, review encrypted swap separately.

Acceptance after deployment: verify all six healthy, actual RAM/disk/OOM/restarts, only intended public ports from an external host, valid certificates for both domains, exact OIDC issuer/callback/logout, registration/email verification, organization isolation, employee→manager→finance flow, downloads, local/offsite backup success and independent alert delivery. Mock SAP remains simulated. Test reboot recovery once authorized. Never expose plaintext backup, database, Keycloak admin or container-management endpoints.

Record release.json, source SHA, migration level, Keycloak version and archive ID before every upgrade. Review schema/Keycloak compatibility before approving an older release. On failed health wait, keep evidence and inspect safe logs; the deploy script does **not** automatically restore or rewind the database. If schema-compatible, use the reviewed prior digest inventory/source via the same approved path. If incompatible, restore the coordinated archive to **new volumes on an isolated target**, validate, then switch ingress explicitly. Never run down-v, drop schemas or downgrade Keycloak against an upgraded database to force rollback. A stopped/failed rollout requires operator action; a green health check is followed by business acceptance.

## 7. Local browser acceptance (existing preserved QA)

The preserved app is **http://localhost:33000**, with Mailpit **http://localhost:38025**. Use [the GUI manual](local-browser-testing-guide.md) and [Phase 12F evidence](phase12f-browser-acceptance.md). Roles belong to organization memberships. No acceptance rerun, environment recreation, data reset or external email was performed here.
