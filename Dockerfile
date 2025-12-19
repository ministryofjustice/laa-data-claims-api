# Build stage
FROM gradle:8-jdk21 AS builder

# 1. Declare that we expect these arguments
ARG GIT_PACKAGE_USER
ARG GIT_PACKAGE_KEY

# 2. Map them to the ENV variables Gradle is looking for
ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}

# Set up working directory for build
WORKDIR /build

# Copy gradle files and source code
COPY . .


# Run gradle build
RUN gradle claims-data:service:spotlessApply build -x test


# Debug step: List all JAR files to find the correct path
RUN find /build -name "*.jar"

# Specify java runtime base image
FROM amazoncorretto:21-alpine

# Set up working directory in the container
RUN mkdir -p /opt/laa-data-claims-api/claims-data/
WORKDIR /opt/laa-data-claims-api/claims-data/

# Copy the JAR file into the container
COPY --from=builder /build/claims-data/service/build/libs/service-*.jar app.jar

# Create a group and non-root user
RUN addgroup -S appgroup && adduser -u 1001 -S appuser -G appgroup

# Set the default user
USER 1001

# Expose the port that the application will run on
EXPOSE 8080

# Run the JAR file
CMD ["java", "-jar", "app.jar"]