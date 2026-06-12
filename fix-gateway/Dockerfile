FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR file
COPY target/*.jar app.jar

# Create directories for FIX data
RUN mkdir -p /app/data/fix/store /app/data/fix/log

# Expose ports
EXPOSE 8081 9876

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Made with Bob
