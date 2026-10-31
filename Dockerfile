# syntax=docker/dockerfile:1

# ---- Stage 1: build the React app (Node is only used here, not in the final image) ----
FROM node:26-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: build the jar with Maven (full JDK, thrown away afterwards) ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependencies first: this layer is reused until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# Then the source code, plus the React build as static files, so one jar serves screens and API.
# Tests run in CI, not during the image build.
COPY src src
COPY --from=frontend /frontend/dist/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/*.jar application.jar \
    # Split the jar into layers (libraries change rarely, our code changes often).
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Stage 3: run on a small Java 21 runtime as a non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app -u 10001 app

COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app

# Memory for a small container (measured: non-heap is ~260 MB for this app, the heap ~125 MB under load).
# - Heap: 40% of the container limit (~205 MB on 512 MB), the rest is for non-heap memory.
# - Code cache and metaspace are capped so they can't slowly grow past the container limit.
# - Exit on OutOfMemoryError so the platform restarts the app cleanly.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=160m -XX:+ExitOnOutOfMemoryError"
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
