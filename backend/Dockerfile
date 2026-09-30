FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --home-dir /app spring

WORKDIR /app

COPY build/libs/app.jar /app/app.jar
RUN chown spring:spring /app/app.jar

USER spring:spring

EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=12 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
