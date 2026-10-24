# Thuhina Water API – multi-stage build (design 12).
# Build:  docker build -t thuhina/api ./server      Run: see deploy/docker-compose.yml

# ---- build stage: compile and package the Spring Boot jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon -q bootJar -x test && cp build/libs/*.jar /src/app.jar

# ---- run stage: JRE only, non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
# /data/files = uploaded files (company logo …); a volume in deploy/docker-compose.yml.
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /data/files && chown app:app /data/files
COPY --from=build /src/app.jar app.jar
USER app

# JVM heap is capped at 768 MB so the JVM, Next.js and PostgreSQL fit on a 2 GiB t4g.small (design 16, risk 6).
# Override JAVA_OPTS in deploy/.env if the server is resized.
ENV TZ=Asia/Colombo \
    SPRING_PROFILES_ACTIVE=prod \
    FILES_DIR=/data/files \
    JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Duser.timezone=Asia/Colombo -jar /app/app.jar"]
