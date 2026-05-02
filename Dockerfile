# ─── Stage 1: Build ───────────────────────────────────────
FROM amazoncorretto:21 AS build

WORKDIR /build

# Copy Gradle wrapper + build scripts first (layer caching)
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# Copy source modules needed for server build
COPY shared/ shared/
COPY server/ server/

# Create stub client dir so Gradle configuration succeeds (client is excluded by .dockerignore)
RUN mkdir -p client/src

# Build the fat JAR — skip packageWorld (world provided at runtime, not bundled)
RUN chmod +x gradlew && ./gradlew :server:shadowJar -x packageWorld --no-daemon

# ─── Stage 2: Runtime ─────────────────────────────────────
FROM amazoncorretto:21-alpine-jdk

# Create non-root user
RUN addgroup -g 1000 -S neomud && adduser -u 1000 -S neomud -G neomud

# Create directories
RUN mkdir -p /app /data /worlds && chown -R neomud:neomud /app /data /worlds

# Copy the fat JAR from build stage
COPY --from=build --chown=neomud:neomud /build/server/build/libs/neomud-server.jar /app/neomud-server.jar

# Install wget for health checks (not included in Alpine JDK by default)
RUN apk add --no-cache wget

# Volumes for persistent data and world bundles
VOLUME ["/data", "/worlds"]

# Game server port
EXPOSE 8080

# Environment defaults
ENV NEOMUD_PORT=8080
ENV NEOMUD_WORLD=/worlds/world.nmd
ENV NEOMUD_DB=/data/neomud.db
ENV NEOMUD_ADMINS=

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO/dev/null http://localhost:8080/health || exit 1

# Run as non-root
USER neomud

ENTRYPOINT ["java", "-jar", "/app/neomud-server.jar"]
