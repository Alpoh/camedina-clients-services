# syntax=docker/dockerfile:1
FROM eclipse-temurin:26-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B clean package -DskipTests

RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

FROM eclipse-temurin:26-jre-alpine AS runtime
RUN addgroup -S spring && adduser -S -G spring spring
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/lib/ ./lib/
COPY --from=build /workspace/extracted/application/*.jar ./app.jar

USER spring:spring
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD wget --spider -q -T 3 http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
