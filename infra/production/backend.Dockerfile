# syntax=docker/dockerfile:1
FROM --platform=$BUILDPLATFORM maven:3.9.12-eclipse-temurin-21@sha256:c3c9d3ac4ce8431a3995c0318b8d390f448e693dd4fabc16e9b68d2e1f3d7b46 AS build
WORKDIR /app
COPY backend/ ./
ENV MAVEN_OPTS="-Xmx768m -XX:ActiveProcessorCount=2"
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -ntp clean package -DskipTests
FROM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0
RUN groupadd -g 1000 app && useradd -u 1000 -g app -M app
WORKDIR /app
COPY --from=build --chown=1000:1000 /app/target/workforce-0.1.0.jar app.jar
COPY --chmod=0555 infra/production/with-secrets.sh /usr/local/bin/with-secrets
USER 1000:1000
ENV PORT=8080 SERVICE_ROLE=backend
ENTRYPOINT ["/usr/local/bin/with-secrets","java","-jar","/app/app.jar"]
