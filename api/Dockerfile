# syntax=docker/dockerfile:1
#
# Demo_phase/api — Spring Boot 4.x REST API over PhaseDB (Java 17 / Maven).
#
# Builds with Maven from the official Maven 3.9.9 image to a Spring "fat" jar,
# then runs it with the eclipse-temurin 17 JRE.
#
#   docker build -t erp-api:latest -f Demo_phase/api/Dockerfile .
#   docker run --rm -p 8080:8080 \
#     -e PORT=8080 \
#     -e ERP_PHASE_URL=http://phasedb-hs:8484 \
#     erp-api:latest
#
# Notes:
#   * The image provides Maven 3.9.9, so the build does not download Maven.
#     The produced jar is the Spring repackaged `api-0.0.1-SNAPSHOT.jar`.
#   * The API is read-only about PhaseDB except for the JWT/seed machinery;
#     it talks to PhaseDB at $ERP_PHASE_URL (default 127.0.0.1:8484).
#   * The API binds $PORT (default 8080) and its base path is /api/v1.

FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Resolve plugins and dependencies before copying source for better caching.
COPY Demo_phase/api/pom.xml ./
RUN mvn -B dependency:go-offline

# Source next, so incremental builds skip unchanged packages.
COPY Demo_phase/api/src ./src

RUN mvn -B package -DskipTests -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-noble

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

ENV PORT=8080
# PhaseDB HTTP endpoint. Set to the compose service name (phasedb-hs) or the
# host's PhaseDB when running the API on the host.
ENV ERP_PHASE_URL=http://127.0.0.1:8484

EXPOSE 8080

# All libs ship with the JRE image; curl supports the container healthcheck.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app \
 && useradd --system --gid app --home-dir /home/app --no-create-home app
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD curl -fsS http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1
