# Specify java runtime base image
FROM amazoncorretto:21-alpine

# Set up working directory in the container
RUN mkdir -p /opt/laa-data-claims-api/claims-data/
WORKDIR /opt/laa-data-claims-api/claims-data/

# Copy the JAR file into the container
COPY service/build/libs/service-1.0.0.jar app.jar

# Create a group and non-root user
RUN addgroup -S appgroup && adduser -u 1001 -S appuser -G appgroup

# Set the default user
USER 1001

# Expose the port that the application will run on
EXPOSE 8080

# Run the JAR file
CMD ["java", "-jar", "app.jar"]