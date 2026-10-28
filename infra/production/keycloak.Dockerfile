FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b AS build
ENV KC_DB=postgres KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build
FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b AS trust
USER 0
COPY --chmod=0555 infra/production/keycloak-trust-mitigation.sh /tmp/mitigate-trust.sh
RUN /tmp/mitigate-trust.sh
FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b
COPY --from=trust /etc/pki/ca-trust/source/blocklist/ /etc/pki/ca-trust/source/blocklist/
COPY --from=trust /etc/pki/ca-trust/extracted/java/ /etc/pki/ca-trust/extracted/java/
COPY --from=build /opt/keycloak/ /opt/keycloak/
COPY --chmod=0555 infra/production/with-secrets.sh /opt/keycloak/with-secrets.sh
COPY infra/production/workforce-realm.json /opt/keycloak/data/import/workforce-realm.json
COPY infra/production/keycloak-theme/workforce/ /opt/keycloak/themes/workforce/
ENV SERVICE_ROLE=keycloak
ENTRYPOINT ["/opt/keycloak/with-secrets.sh","/opt/keycloak/bin/kc.sh"]
CMD ["start","--optimized","--import-realm"]
