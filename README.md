# Banking Application using Java 17, Spring Boot, Spring Security and H2 DB

RESTful API to simulate simple banking operations. 

## Requirements

*	CRUD operations for customers and accounts.
*	Support deposits and withdrawals on accounts.
*	Internal transfer support (i.e. a customer may transfer funds from one account to another).


## Getting Started

1. Checkout the project from GitHub

```
git clone https://github.com/sbathina/BankApp

```
2. Enable Lombok support on your IDE

Refer to the following link for instructions:

```
https://projectlombok.org/setup/eclipse

```
3. Open IDE of your choice and Import as existing maven project in your workspace

```
- Import existing maven project
- Run mvn clean install
- If using STS, Run As Spring Boot App

```
4. Default port for the api is 8989


### Prerequisites

* Java 17 (LTS) - OpenJDK 17 or later
* Spring Tool Suite 4 or similar IDE
* [Maven](https://maven.apache.org/) - Dependency Management

### Maven Dependencies

```
spring-boot-starter-actuator
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-web
spring-boot-devtools
h2 - Inmemory database
lombok - to reduce boilerplate code
springdoc-openapi-ui - API documentation (OpenAPI 3.0)
spring-boot-starter-test
spring-security-test

```

## API Documentation

Please find the Rest API documentation (OpenAPI 3.0) in the below url

```
http://localhost:8989/bank-api/swagger-ui.html

```

## H2 In-Memory Database

Make sure to use jdbc:h2:mem:testdb as your jdbc url. If you intend to you use custom database name, please
define datasource properties in application.yml

```
http://localhost:8989/bank-api/h2-console/

```

## Testing the Bank APP Rest Api

1. Please use the Swagger url to perform CRUD operations. 

2. Browse to <project-root>/src/test/resources to find sample requests to add customer and accounts.

## API V2

V1 remains available at its existing unversioned paths. V2 uses the same
resources under `/v2`; for example,
`GET /customers/{customerNumber}` becomes
`GET /v2/customers/{customerNumber}`. V2 errors use RFC 9457 ProblemDetail
responses with the `application/problem+json` media type.

V2 specifications and conventions are in
`src/main/resources/openapi/v2/README.md` and `_template.yaml`. The endpoint
group inventory is `docs/api-v2/endpoint-groups.json`. OpenAPI documents are
available at `/bank-api/v3/api-docs/v1` and `/bank-api/v3/api-docs/v2`, and the
Swagger UI includes a group selector. In springdoc's `/v3/api-docs` URL, “v3”
refers to OpenAPI 3, not to an API version.

## Migration Notes

This application has been migrated from Java 8 to Java 11 (LTS), and then to Java 17 (LTS) with Spring Boot 3.3. See `MIGRATION_NOTES.md` for detailed information about the migration process and changes made.

## Authors

* **Shyam Bathina**

