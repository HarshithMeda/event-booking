# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar with Maven (full JDK, thrown away afterwards) ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependencies first: this layer is reused until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# Then the source code. Tests run in CI, not during the image build.
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/*.jar application.jar \
    # Split the jar into layers (libraries change rarely, our code changes often).
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Stage 2: run on a small Java 21 runtime as a non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app -u 10001 app

COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app

# Size the heap from the container's memory limit (not the host's), leaving 30% for
# metaspace, threads and other non-heap memory. Exit on OutOfMemoryError so Docker restarts us.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
