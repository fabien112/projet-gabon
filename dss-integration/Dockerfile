# Build (sources discarded after this stage)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package \
    && find target -maxdepth 1 -name 'dss-integration-*.jar' ! -name '*-plain.jar' -exec cp {} /build/app.jar \;

# Runtime only — no source, no Maven
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 10001 appuser
COPY --from=build /build/app.jar /app/app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=8 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
