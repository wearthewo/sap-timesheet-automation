FROM quay.io/keycloak/keycloak:26.8.1@sha256:79c623a22abacf3fe6c00a5fb30f5b7843d717b234033b80995891c06116d244 AS build
ENV KC_DB=postgres KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build
FROM quay.io/keycloak/keycloak:26.8.1@sha256:79c623a22abacf3fe6c00a5fb30f5b7843d717b234033b80995891c06116d244
COPY --from=build /opt/keycloak/ /opt/keycloak/
COPY --chmod=0555 infra/production/with-secrets.sh /opt/keycloak/with-secrets.sh
COPY infra/production/workforce-realm.json /opt/keycloak/data/import/workforce-realm.json
ENV SERVICE_ROLE=keycloak
ENTRYPOINT ["/opt/keycloak/with-secrets.sh","/opt/keycloak/bin/kc.sh"]
CMD ["start","--optimized","--import-realm"]
