FROM maven:3.9.9-eclipse-temurin-8 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=builder /workspace/target/ai-streaming-framework-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]