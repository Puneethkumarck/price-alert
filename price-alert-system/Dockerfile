FROM eclipse-temurin:25-jre-alpine

ARG MODULE
ARG JAR_FILE=${MODULE}/build/libs/${MODULE}-0.1.0-SNAPSHOT.jar

COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--enable-preview", "-Dspring.profiles.active=docker", "-jar", "/app/app.jar"]
