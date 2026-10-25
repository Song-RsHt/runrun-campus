FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine-3.24
RUN apk upgrade --no-cache \
    && addgroup -S runrun \
    && adduser -S -D -h /app -s /sbin/nologin -G runrun runrun
WORKDIR /app
RUN mkdir -p /app/uploads/order-proofs && chown -R runrun:runrun /app
COPY --from=build --chown=runrun:runrun /app/target/runrun-campus-backend-0.0.1-SNAPSHOT.jar app.jar
USER runrun
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
