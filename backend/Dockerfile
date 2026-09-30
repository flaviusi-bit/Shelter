# Build the Spring Boot application inside Docker.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# Small runtime image containing only the built application.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home shelter
WORKDIR /app
RUN mkdir -p /app/data/documents /app/data/backups \
    && chown -R shelter:shelter /app/data

COPY --from=build --chown=shelter:shelter /workspace/target/shelter-api-0.1.0-SNAPSHOT.jar app.jar
USER shelter

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
