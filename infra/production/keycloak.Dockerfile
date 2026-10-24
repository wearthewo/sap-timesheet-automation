FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b AS build
ENV KC_DB=postgres KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build
FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b
COPY --from=build /opt/keycloak/ /opt/keycloak/
COPY --chmod=0555 infra/production/with-secrets.sh /opt/keycloak/with-secrets.sh
COPY infra/production/workforce-realm.json /opt/keycloak/data/import/workforce-realm.json
ENV SERVICE_ROLE=keycloak
ENTRYPOINT ["/opt/keycloak/with-secrets.sh","/opt/keycloak/bin/kc.sh"]
CMD ["start","--optimized","--import-realm"]
