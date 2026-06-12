FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR file
COPY target/*.jar app.jar

# Expose port
EXPOSE 8084

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Made with Bob
