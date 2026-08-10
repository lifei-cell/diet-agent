# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system diet \
    && useradd --system --gid diet --home-dir /app diet

COPY target/diet-agent-*.jar app.jar

USER diet

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/ > /dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
