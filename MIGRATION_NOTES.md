# Java 8 to 11 Migration Notes

## Overview

This document summarizes the changes made to migrate the BankApp from Java 8 to Java 11 (LTS).

## Changes Made

### 1. Build Configuration Updates

**Maven Configuration (`pom.xml`)**:
- Updated `java.version` from `1.8` to `11`
- Added `maven.compiler.release` property set to `11`
- Added `project.build.sourceEncoding` set to `UTF-8`
- Upgraded Maven plugins to Java 11-compatible versions:
  - `maven-compiler-plugin`: 3.11.0 with `<release>11</release>`
  - `maven-surefire-plugin`: 3.2.5
  - `maven-failsafe-plugin`: 3.2.5
  - `maven-enforcer-plugin`: 3.5.0 with Java 11+ requirement
  - `maven-javadoc-plugin`: 3.6.3

### 2. Dependencies for Removed JDK Modules

**JAXB Runtime**:
- Added `org.glassfish.jaxb:jaxb-runtime:2.3.1` dependency
- Spring Boot already includes JAXB API and activation API as transitive dependencies
- No code changes required as Spring Boot handles JAXB integration

### 3. Source Code Changes

**Swagger Migration (Springfox to SpringDoc OpenAPI)**:
- Replaced `io.springfox` dependencies with `org.springdoc:springdoc-openapi-ui:1.6.15`
- Updated controller annotations from Springfox (`@Api`, `@ApiOperation`) to SpringDoc (`@Tag`, `@Operation`)
- Rewrote `ApplicationConfig.java` to use SpringDoc configuration instead of Springfox Docket

**Test Framework Migration (JUnit 4 to JUnit 5)**:
- Updated test classes to use JUnit 5 annotations (`@Test` from `org.junit.jupiter.api`)
- Spring Boot 2.7.x includes JUnit 5 by default via `spring-boot-starter-test`

### 4. CI/CD Updates

**GitHub Actions**:
- Created workflow file `.github/workflows/ci.yml` for Java 11 builds
- Configured to use JDK 11 with Temurin distribution
- Added Maven caching for improved build performance
- Runs compile, test, and verify steps on push and pull requests

### 4. Runtime Environment

**Java Version**:
- Application now runs on OpenJDK 11 (Temurin distribution)
- No illegal reflective access warnings observed
- All tests pass with same functionality as Java 8 baseline

## Verification Results

### Build and Test Status
- Maven compilation successful with Java 11
- All unit tests pass
- Spring Boot application starts correctly
- H2 database integration working
- API documentation accessible
- Spring Security configuration functional

### Application Startup Verification (MBA-782)

**Verification Date**: December 14, 2025

**Java Runtime Used**:
```
openjdk version "11.0.29" 2025-10-21
OpenJDK Runtime Environment (build 11.0.29+7-post-Ubuntu-1ubuntu122.04)
OpenJDK 64-Bit Server VM (build 11.0.29+7-post-Ubuntu-1ubuntu122.04, mixed mode, sharing)
```

**Startup Command**: `java -jar target/bank-app-1.0.0.jar`

**Startup Results**:
- Application started successfully in approximately 5.08 seconds
- Tomcat initialized on port 8989 with context path '/bank-api'
- Spring Boot version: 2.7.18
- Hibernate ORM version: 5.6.15.Final
- H2 database console available at '/h2-console'
- Spring Security filter chain configured correctly
- Actuator endpoint exposed at '/actuator'

**Startup Log Analysis**:
- No ERROR level messages in startup logs
- One WARN message about `spring.jpa.open-in-view` being enabled by default (expected, non-critical)
- All Spring Data JPA repositories bootstrapped successfully (4 repositories found)
- HikariCP connection pool started successfully
- JPA EntityManagerFactory initialized for persistence unit 'default'

**Service Verification**:
| Endpoint | Status | Response |
|----------|--------|----------|
| `/actuator/health` | 200 OK | `{"status":"UP"}` |
| `/actuator` | 200 OK | Links to health endpoints |
| `/customers/all` | 200 OK | Empty array (expected) |
| `/swagger-ui.html` | 302 Redirect | Redirects to Swagger UI |
| `/h2-console/` | 200 OK | H2 Console HTML page |

**Conclusion**: The application starts without errors on Java 11 and all services are running correctly.

### Performance and Compatibility
- No illegal reflective access warnings
- JAXB functionality working with added runtime dependency
- Default G1 garbage collector (Java 11 default) performing well
- TLS 1.3 support enabled by default

## Java 11 Benefits Gained

1. **Performance**: G1 garbage collector improvements and general JVM optimizations
2. **Security**: TLS 1.3 support and updated security algorithms
3. **Language Features**: Ready for future adoption of Java 9-11 language features
4. **Long-term Support**: Java 11 LTS provides extended support lifecycle

## Areas With Minimal Changes

The following areas required no or minimal modifications:
- **TLS Configuration**: Application uses Spring Boot defaults, no custom TLS setup
- **GC Logging**: No custom GC logging was configured, using Java 11 defaults
- **Module System**: Staying on classpath (not adopting JPMS modules)

## Future Considerations

1. **Optional Modernizations** (future PRs):
   - Adopt `var` keyword for local variables (Java 10+)
   - Use new HTTP Client API (Java 11+) if external HTTP calls are added
   - Consider adopting Java modules (JPMS) if project grows

2. **Monitoring**:
   - Monitor application performance in production
   - Watch for any TLS compatibility issues with external services (if added)

## Rollback Plan

If rollback to Java 8 is needed:
1. Revert `pom.xml` changes (set `java.version` back to `1.8`)
2. Remove JAXB runtime dependency
3. Update CI workflow to use Java 8
4. Revert Maven plugin versions if needed

## Migration Completion

- Java 11 build configuration
- Dependencies for removed JDK modules
- CI/CD updated to Java 11
- All tests passing
- Documentation updated
- Migration notes created

The migration is complete and the application is ready for production deployment on Java 11.

# Spring Boot 2.7.18 -> 3.3.x / Java 17 Migration

## Overview

This section covers the upgrade from Spring Boot 2.7.18 / Java 11 to Spring Boot 3.3.13 / Java 17.
The baseline e2e suite added before this change (`BankingHttpContractTest`, `BaselinePersistenceDefectTest`,
`SecurityHttpTest`, `BankingApplicationTests`) is the check that HTTP behavior is preserved.

## Build Configuration (`pom.xml`)

- `spring-boot-starter-parent`: `2.7.18` -> `3.3.13` (latest 3.3.x patch)
- `java.version` and `maven.compiler.release`: `11` -> `17`
- `maven-compiler-plugin` `<release>`: `11` -> `17`
- `maven-enforcer-plugin` `requireJavaVersion`: `[11,)` -> `[17,)`
- Resulting stack: Spring Framework 6.1, Spring Security 6.3, Hibernate ORM 6.5.3.Final, Tomcat 10.1, H2 2.2

## Dependencies

| Before | After | Reason |
|--------|-------|--------|
| `org.springdoc:springdoc-openapi-ui:1.6.15` | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0` | springdoc 1.x targets Boot 2; 2.6.0 is the line built for Boot 3.3 |
| `org.glassfish.jaxb:jaxb-runtime:2.3.8` | removed | javax-based JAXB runtime; the app does not use JAXB, and Hibernate 6 brings the Jakarta `jaxb-runtime` 4.x transitively |

`mvn dependency:tree` contains no `javax.*` artifacts after the change.

## javax -> jakarta

All `javax.persistence.*` imports in `model/` (`Account`, `Address`, `BankInfo`, `Contact`, `Customer`,
`CustomerAccountXRef`, `Transaction`) now use `jakarta.persistence.*`. No other Jakarta EE `javax.*` imports
existed in main or test code.

## Security Configuration

Old (`WebSecurityConfigurerAdapter`, removed in Spring Security 6):

```java
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeRequests().antMatchers("/").permitAll().and()
                .authorizeRequests().antMatchers("/h2-console/**").permitAll();
        httpSecurity.csrf().disable();
        httpSecurity.headers().frameOptions().disable();
    }
}
```

New (`@Bean SecurityFilterChain`, lambda DSL):

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
    httpSecurity
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(antMatcher("/")).permitAll()
                    .requestMatchers(antMatcher("/h2-console/**")).permitAll()
                    .anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                    .frameOptions(frame -> frame.disable())
                    .xssProtection(xss -> xss.headerValue(HeaderValue.ENABLED_MODE_BLOCK)));
    return httpSecurity.build();
}
```

- The old config had no `anyRequest()` rule, so under Security 5 every endpoint was open (Basic credentials from
  `spring.security.user.*` were never required). Security 6 denies unmatched requests, so
  `anyRequest().permitAll()` keeps the API open exactly as before.
- `AntPathRequestMatcher.antMatcher(...)` is used because the H2 console registers a second servlet, which makes
  `requestMatchers(String)` ambiguous.
- Security 6 changed the default `X-XSS-Protection` header from `1; mode=block` to `0`. The old value is kept
  explicitly so response headers are unchanged (`SecurityHttpTest` pins them).
- CSRF stays disabled and `X-Frame-Options` stays absent (H2 console frames).

## OpenAPI

- Swagger UI still at `/bank-api/swagger-ui/index.html`; `/bank-api/swagger-ui.html` still redirects (302) to it.
- `/bank-api/v3/api-docs` still serves OpenAPI `3.0.1` with title `BANKING APPLICATION REST API` and the same 7 paths.
- `ApplicationConfig` (`OpenAPI` bean) and the `io.swagger.v3.oas.annotations` on the controllers needed no changes.

## CI

`.github/workflows/ci.yml`: JDK `11` -> `17` (Temurin). Nothing else changed.

## Test Changes

Test count is unchanged (15 run by Maven). No test was deleted, disabled or loosened.

1. `BankingHttpContractTest`: removed the test-only `UuidDialect` (an `H2Dialect` subclass overriding
   `getTypeName(int, long, int, int)` to map `BINARY` to `binary(16)`) and its `spring.jpa.database-platform`
   property. That Hibernate 5 API no longer exists in Hibernate 6, and the workaround is no longer needed:
   Hibernate 6 maps `java.util.UUID` to H2's native `uuid` type (16 bytes). All assertions are unchanged.
   Reference: Hibernate 6.0 migration guide, "UUID mapping changes"
   (https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc).
2. `BaselinePersistenceDefectTest` (`@Tag("baseline-defect")`): this class pinned the Boot 2.7 defect where
   Hibernate 5.6 + H2 2.x created UUID ids as `binary(255)`, so every read of a stored entity returned 500.
   Hibernate 6 fixes the mapping, and keeping the 500s would mean re-introducing a known defect, so the
   expectations now pin the fixed behavior on the same unmodified schema: id columns are 16 bytes, and stored
   customer read/list/update/delete, account creation and transfer for a stored customer, and stored account
   read/transactions succeed with the same responses as `BankingHttpContractTest`. The two test methods were
   renamed from `...Fail` to `...Succeed` to match. Same reference as above.

Compile warnings for `ResponseEntity.getStatusCodeValue()` (deprecated in Spring Framework 6) are left as is
to avoid test churn; the method still exists in 6.1.

## Behavioral Changes

- Stored customers and accounts are now readable on the default schema: requests that returned 500 on Boot 2.7
  (stored customer GET/PUT/DELETE, `/customers/all`, account add/transfer for a stored customer, stored account
  GET and transactions) now return the documented success responses. This is the fix of the baseline UUID defect.
- No other HTTP status, body or header change was observed: all `SecurityHttpTest` and `BankingHttpContractTest`
  expectations pass unchanged, including the pinned quirks (account GET returns 302, duplicates give 500, `{}`
  bodies give 500, open API with no auth challenge).
- Log format follows Boot 3 defaults (ISO-8601 timestamps).

## Verification Results

- JDK: `openjdk version "17.0.19" 2026-04-21` (Ubuntu), Maven 3.8.8 via `./mvnw`
- `./mvnw -B clean verify`: BUILD SUCCESS, Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
- `./mvnw -B test`: BUILD SUCCESS, Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
- Baseline on JDK 17 before the change (Boot 2.7.18, release 11): 15 tests, all green

**Startup check** (`java -jar target/bank-app-1.0.0.jar`): Spring Boot v3.3.13, Hibernate ORM 6.5.3.Final,
Tomcat 10.1.42, started in about 3.4 seconds on port 8989 with context path `/bank-api`, no ERROR lines
(only the existing `spring.jpa.open-in-view` WARN).

| Endpoint | Status |
|----------|--------|
| `/` | 404 (JSON error, as before) |
| `/h2-console/` | 200 |
| `/actuator/health` | 200 `{"status":"UP"}` |
| `/actuator/env` | 404 |
| `/v3/api-docs` | 200, OpenAPI 3.0.1, 7 paths |
| `/swagger-ui/index.html` | 200 |
| `/swagger-ui.html` | 302 -> `/bank-api/swagger-ui/index.html` |
| `/customers/all` | 200 |
| `/accounts/{absent}` | 404 |
