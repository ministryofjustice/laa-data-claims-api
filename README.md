# laa-data-claims-api
[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/laa-data-claims-api/badge)](https://github-community.service.justice.gov.uk/repository-standards/laa-data-claims-api)

This is a Java based Spring Boot application hosted on [MOJ Cloud Platform](https://user-guide.cloud-platform.service.justice.gov.uk/documentation/concepts/what-is-the-cloud-platform.html).

## Overview 

Java Spring Boot based application that provides APIs to access data maintained by the Data Stewardship (Payments) team at the LAA.

The project was created from [this Github Template](https://github.com/ministryofjustice/laa-spring-boot-microservice-template) 

### Project Structure
Includes the following subprojects:

- `claims-data/api` - OpenAPI specification used for generating API stub interfaces and documentation.
- `claims-data/service` - REST API service with CRUD operations interfacing a JPA repository.
- `claims-data/swagger-ui-app` - Stub REST API application which allows swagger-ui docs to be exposed
                                - this allows us to upgrade open api spec ahead of the java code 
                                - (openapi spec changes have to be made on a branch named openapi/*)
## Add GitHub Token
1.	Generate a Github PAT (Personal Access Token) to access the required plugin, via https://github.com/settings/tokens
2.	Specify the Note field, e.g. “Token to allow access to LAA Gradle plugin” 
3.  If you haven’t got a gradle.properties file create one under `~/.gradle/gradle.properties`
4.  Add the following properties to `~/.gradle/gradle.properties` and replace the placeholder values as follows:
    - `project.ext.gitPackageUser` = YOUR_GITHUB_USERNAME
    - `project.ext.gitPackageKey` = PAT_CREATED_ABOVE
 
5.	Go back to Github to authorize MOJ for SSO

## Build And Run Application

### Build application
`./gradlew clean build`

### Run integration tests

`./gradlew integrationTest`

### Run application (after first starting local postgres container)
`cd claims-data`
`docker compose up -d`
`../gradlew bootRun`

### Reset local Postgres DB data (if you want to delete flyway history and start from a blank database)
`cd claims-data`
`docker compose down -v`

## Application Endpoints

### API Documentation

#### Swagger UI
- http://localhost:8080/swagger-ui/index.html
#### API docs (JSON)
- http://localhost:8080/v3/api-docs

### Actuator Endpoints
The following actuator endpoints have been configured:
- http://localhost:8080/actuator
- http://localhost:8080/actuator/health
- http://localhost:8080/actuator/info

## Additional Information

### Libraries Used
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) - used to provide various endpoints to help monitor the application, such as view application health and information.
- [Spring Boot Web](https://docs.spring.io/spring-boot/reference/web/index.html) - used to provide features for building the REST API implementation.
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa.html) - used to simplify database access and interaction, by providing an abstraction over persistence technologies, to help reduce boilerplate code.
- [Springdoc OpenAPI](https://springdoc.org/) - used to generate OpenAPI documentation. It automatically generates Swagger UI, JSON documentation based on your Spring REST APIs.
- [Lombok](https://projectlombok.org/) - used to help to reduce boilerplate Java code by automatically generating common
  methods like getters, setters, constructors etc. at compile-time using annotations.
- [MapStruct](https://mapstruct.org/) - used for object mapping, specifically for converting between different Java object types, such as Data Transfer Objects (DTOs)
  and Entity objects. It generates mapping code at compile code.


