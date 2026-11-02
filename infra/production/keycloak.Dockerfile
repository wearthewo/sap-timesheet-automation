# Operator-maintained source build; see docs/phase13-keycloak-adoption.md.
# The reviewed Corretto archive and RPM snapshot are AMD64 only.
FROM amazonlinux:2023@sha256:b156298161e9ab6392cda59081d016866601f7213a28ec21270d3a75c72f2170 AS java
ARG TARGETARCH
RUN test "$TARGETARCH" = amd64
RUN dnf install -y --setopt=install_weak_deps=False --setopt=gpgcheck=1 ca-certificates-2025.2.76-1.0.amzn2023.0.3 fontconfig-2.13.94-2.amzn2023.0.2 freetype-2.13.2-5.amzn2023.0.2 shadow-utils-4.9-12.amzn2023.0.4 gzip-1.12-1.amzn2023.0.1 tar-1.34-1.amzn2023.0.4 findutils-4.8.0-2.amzn2023.0.2 && dnf clean all
# Immutable repository snapshot; official RPM signature checks remain enabled.
RUN dnf --disablerepo='*' --repofrompath=reviewed,https://cdn.amazonlinux.com/al2023/core/guids/0d08b70774897c2bb6edd688009cb160ffc4e4afb06ced01555d6eee2ee221d7/x86_64/ --setopt=reviewed.gpgcheck=1 --setopt=reviewed.gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-amazon-linux-2023 --setopt=install_weak_deps=False upgrade -y \
    curl-minimal-8.21.0-5.amzn2023.0.3 libcurl-minimal-8.21.0-5.amzn2023.0.3 \
    libbrotli-1.1.0-1.amzn2023.0.2 libcap-2.73-1.amzn2023.0.8 \
    pcre2-10.40-1.amzn2023.0.5 pcre2-syntax-10.40-1.amzn2023.0.5 \
    python3-3.9.25-1.amzn2023.0.11 python3-libs-3.9.25-1.amzn2023.0.11 && dnf clean all
ADD --checksum=sha256:f3330bf440b8f4514b7b279b6145d4b4493dea9dda5f3a702b49c53c41bec1e4 https://corretto.aws/downloads/resources/21.0.13.0.11/amazon-corretto-21.0.13.0.11-linux-x64.tar.gz /tmp/java.tar.gz
RUN mkdir -p /opt/java/openjdk && tar -xzf /tmp/java.tar.gz -C /opt/java/openjdk --strip-components=1 && rm /tmp/java.tar.gz
ENV JAVA_HOME=/opt/java/openjdk PATH=/opt/java/openjdk/bin:$PATH
RUN java -XshowSettings:properties -version 2>&1 | grep -F '21.0.13+11-LTS'

FROM java AS source-build
RUN dnf install -y --setopt=install_weak_deps=False --setopt=gpgcheck=1 git unzip which libicu-67.1-7.amzn2023.0.4 && dnf clean all
ADD --checksum=sha256:f7763d73c33a2d345227522aa51a5ead3b7f0fde2a807704933174b8a3628b5c https://codeload.github.com/keycloak/keycloak/tar.gz/refs/tags/26.8.1 /tmp/source.tar.gz
COPY infra/production/keycloak/prepare-source.py /tmp/prepare-source.py
RUN python3 /tmp/prepare-source.py /tmp/source.tar.gz /build && rm /tmp/source.tar.gz
WORKDIR /build
ENV MAVEN_OPTS="-Xmx1024m -XX:ActiveProcessorCount=2" NODE_OPTIONS="--max-old-space-size=1024" LD_LIBRARY_PATH=/usr/lib64
# Upstream packaging skips tests; it never substitutes for authentication verification.
RUN --mount=type=cache,id=workforce-keycloak-maven,target=/root/.m2,sharing=locked \
    timeout --signal=TERM --kill-after=15s 2400s ./mvnw -B -ntp -pl quarkus/deployment,quarkus/dist -am -DskipTests -DskipTestsuite -Dnode.version=v24.22.1 install
RUN mkdir /assembled && tar -xzf quarkus/dist/target/keycloak-26.8.1.tar.gz -C /assembled --strip-components=1
COPY infra/production/keycloak/verify-distribution.py /tmp/verify-distribution.py
RUN python3 /tmp/verify-distribution.py /assembled > /assembled/workforce-dependencies.json

FROM java AS runtime
RUN groupadd -g 1000 keycloak && useradd -u 1000 -g 1000 -M -d /opt/keycloak keycloak
COPY --from=source-build --chown=1000:1000 /assembled/ /opt/keycloak/
COPY --chmod=0555 infra/production/with-secrets.sh /opt/keycloak/with-secrets.sh
COPY --chown=1000:1000 infra/production/workforce-realm.json /opt/keycloak/data/import/workforce-realm.json
COPY infra/production/keycloak-theme/workforce/ /opt/keycloak/themes/workforce/
COPY infra/production/keycloak/inputs.json /opt/workforce-evidence/keycloak-inputs.json
RUN mkdir -p /opt/keycloak/data/tmp /opt/keycloak/data/transaction-logs \
 && chown -R 1000:1000 /opt/keycloak/data \
 && chmod 0750 /opt/keycloak/data /opt/keycloak/data/tmp /opt/keycloak/data/transaction-logs \
 && rpm -qa | sort > /opt/workforce-evidence/rpm-inventory.txt
USER 1000:1000
ENV KC_DB=postgres KC_HEALTH_ENABLED=true SERVICE_ROLE=keycloak
RUN test -w /opt/keycloak/data && test -w /opt/keycloak/data/tmp \
 && JAVA_OPTS_KC_HEAP="-Xms128m -Xmx512m -XX:ActiveProcessorCount=2" /opt/keycloak/bin/kc.sh build
ENTRYPOINT ["/opt/keycloak/with-secrets.sh","/opt/keycloak/bin/kc.sh"]
CMD ["start","--optimized","--import-realm"]
