# Specify java runtime base image
FROM amazoncorretto:25-alpine AS base

# Set up working directory in the container
RUN mkdir -p /opt/laa-data-claims-api/claims-data/
WORKDIR /opt/laa-data-claims-api/claims-data/

COPY claims-data/service/build/libs/service-*.jar app.jar

# Create a group and non-root user
RUN addgroup -S appgroup && adduser -u 1001 -S appuser -G appgroup

# Set the default user
USER 1001

# Expose the port that the application will run on
EXPOSE 8080

# Run the JAR file
CMD ["java", "-jar", "app.jar"]