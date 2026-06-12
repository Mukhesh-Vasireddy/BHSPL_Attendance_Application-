# --- Build Stage ---
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies to cache them
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy src and build the packaged jar
COPY src ./src
RUN mvn package -DskipTests

# --- Run Stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the fat executable jar from build stage
COPY --from=build /app/target/bhspl-attendance-1.0-SNAPSHOT.jar app.jar

# Expose ports: Web Portal (8080) and ADMS Push Service (8081)
EXPOSE 8080
EXPOSE 8081

# Headless JVM configuration is critical for server/container environments to prevent Swing graphics failures
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
