# Spring Boot 2.7 to 3.3 / Java 11 to 17 Migration

## Overview

This section summarizes the changes made to migrate the BankApp from Spring Boot 2.7.18 on
Java 11 to Spring Boot 3.3.13 on Java 17 (LTS). The end-to-end test suite added in the previous
PR (76 tests, green on Boot 2.7) was used as the behavioral baseline.

## Changes Made

### 1. Build Configuration Updates

**Maven Configuration (`pom.xml`)**:

- Updated `spring-boot-starter-parent` from `2.7.18` to `3.3.13` (latest 3.3 patch)
- Updated `java.version` from `11` to `17`
- Updated `maven.compiler.release` from `11` to `17`
- `maven-compiler-plugin` 3.11.0: `<release>11</release>` changed to `<release>17</release>`
- `maven-enforcer-plugin` 3.5.0: `requireJavaVersion` changed from `[11,)` to `[17,)`
- Other plugin versions unchanged (surefire/failsafe 3.2.5, javadoc 3.6.3 support Java 17)

### 2. Dependency Changes

| Dependency | Before | After |
|------------|--------|-------|
| Spring Boot | 2.7.18 | 3.3.13 |
| Spring Framework | 5.3.x | 6.1.21 |
| Spring Security | 5.7.x | 6.3.10 |
| Hibernate ORM | 5.6.15.Final | 6.5.3.Final |
| H2 | 2.1.214 | 2.2.224 |
| Embedded Tomcat | 9.0.x | 10.1.42 |
| SpringDoc | `springdoc-openapi-ui:1.6.15` | `springdoc-openapi-starter-webmvc-ui:2.6.0` |
| Swagger UI (webjar) | 4.x | 5.17.14 |
| JAXB runtime | `org.glassfish.jaxb:jaxb-runtime:2.3.8` (explicit) | removed (see below) |

- `springdoc-openapi-ui` 1.x only supports Spring Boot 2 / `javax`. It was replaced with
  `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`, the springdoc line built for
  Spring Boot 3.3.
- The explicit `org.glassfish.jaxb:jaxb-runtime:2.3.8` dependency was removed. It is the
  `javax.xml.bind` implementation, is not used by application code, and pinning it would have
  overridden the Jakarta `jaxb-runtime` 4.0.5 that Hibernate 6 pulls in transitively.

### 3. javax to jakarta

All Jakarta EE imports were moved from `javax.*` to `jakarta.*`. The only Jakarta EE API used by
the application is JPA, in the entities under `src/main/java/com/coding/exercise/bankapp/model/`:

- `javax.persistence.*` to `jakarta.persistence.*` in `Account`, `Address`, `BankInfo`,
  `Contact`, `Customer`, `CustomerAccountXRef`, `Transaction`

No `javax.validation`, `javax.servlet` or `javax.annotation` imports exist in the codebase, and
no JDK `javax.*` packages are used, so nothing else needed to change.

### 4. Security Configuration Rewrite

`WebSecurityConfigurerAdapter` was removed in Spring Security 6. `SecurityConfig` now exposes a
`SecurityFilterChain` bean built with the lambda DSL.

**Before (Spring Security 5.7)**:

```java
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeRequests().antMatchers("/").permitAll().and()
                .authorizeRequests().antMatchers("/h2-console/**").permitAll();
        httpSecurity.csrf().disable();
        httpSecurity.headers().frameOptions().disable();
    }
}
```

**After (Spring Security 6.3)**:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
    httpSecurity
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(antMatcher("/")).permitAll()
                    .requestMatchers(antMatcher("/h2-console/**")).permitAll()
                    .anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));
    return httpSecurity.build();
}
```

Notes:

- **`anyRequest().permitAll()`**: in Spring Security 5 `authorizeRequests()` let requests that
  matched no rule through, so every endpoint was anonymously accessible (pinned by
  `SecurityE2ETest`). In Spring Security 6 `authorizeHttpRequests()` denies unmatched requests.
  The explicit `anyRequest().permitAll()` keeps the previous effective behavior.
- **`AntPathRequestMatcher`**: the old `antMatchers(...)` were Ant matchers. With the H2 console
  servlet registered next to the `DispatcherServlet`, `requestMatchers(String)` cannot decide
  between MVC and Ant matching, so the rules use `antMatcher(...)` to keep the same semantics.
- CSRF disabled and `X-Frame-Options` disabled exactly as before. No `httpBasic()` or
  `formLogin()` was added; the default logout endpoint is unchanged.

### 5. SpringDoc Changes

- Dependency coordinates changed as listed above. `ApplicationConfig` (the `OpenAPI` bean) and
  the `io.swagger.v3.oas.annotations` annotations on the controllers work unchanged with
  springdoc 2.x, so no source changes were required.
- Endpoints are unchanged: `/v3/api-docs`, `/v3/api-docs/swagger-config`, `/swagger-ui.html`
  (302 to `/swagger-ui/index.html`).

### 6. CI/CD Updates

**GitHub Actions** (`.github/workflows/ci.yml`):

- `Set up JDK 11` / `java-version: '11'` changed to `Set up JDK 17` / `java-version: '17'`
  (Temurin distribution, Maven cache and build steps unchanged)

### 7. Test Changes

Only `src/test/java/com/coding/exercise/bankapp/e2e/DefaultSchemaBaselineE2ETest.java` was
changed. No other test file was touched, and no test was removed or disabled (76 tests before and
after).

**Why**: this class pinned a defect of the Hibernate 5.6 generated schema. Hibernate 5.6 mapped
the `java.util.UUID` ids to `binary(255)` columns; H2 2.x right-pads fixed-length binary values,
so every read of a stored customer or account failed with `EntityNotFoundException` (HTTP 500).
Its javadoc stated that the tests tagged `h2-binary-uuid-defect` "are expected to change if the
persistence stack (Hibernate/H2 versions or id mapping) changes".

Hibernate 6 maps `UUID` to `SqlTypes.UUID`, which is the native `uuid` column type on H2, so the
generated production schema is now the same as the test-only `e2e/uuid-schema.sql` and the defect
is gone. See the [Hibernate 6.0 migration guide](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
("UUID mapping changes").

**Could the old behavior be preserved?** Setting
`hibernate.type.preferred_uuid_jdbc_type=BINARY` restores a `BINARY` column type, but Hibernate 6
sizes it as `binary(16)`, so reads still succeed. Reproducing the 500s would require forcing
`binary(255)` column definitions on every id and foreign key, i.e. deliberately reintroducing a
data-access defect. That is clearly wrong, so the tests were updated to pin the new behavior
instead.

**What changed** (same 17 tests; the `h2-binary-uuid-defect` tags were removed because the
defect no longer exists; untagged tests are unchanged):

| Before | After |
|--------|-------|
| `idColumnsAreGeneratedAsFixedLengthBinary`: `BINARY` | `idColumnsAreGeneratedAsNativeUuid`: `UUID` |
| `getCreatedCustomerReturns500` | `getCreatedCustomerReturnsCustomerDetails`: 200 with the customer |
| `getAllCustomersReturns500OnceAnyCustomerExists` | `getAllCustomersReturnsCreatedCustomer`: 200, 1 element |
| `updateExistingCustomerReturns500` | `updateExistingCustomerReturns200`: `Success: Customer updated.` |
| `deleteExistingCustomerReturns500AndKeepsRow` | `deleteExistingCustomerReturns200AndRemovesRow` |
| `createAccountForExistingCustomerReturns500AndPersistsNothing` | `createAccountForExistingCustomerReturns201AndPersistsAccount` |
| `transferForExistingCustomerReturns500` | `transferFromUnknownAccountOfExistingCustomerReturns404` |

The new expectations match what the unchanged `UuidSchemaE2ETestSupport` suites
(`CustomerApiE2ETest`, `AccountApiE2ETest`, `TransferApiE2ETest`) already assert for the same
operations.

## Behavioral Changes

- **Default schema UUID ids (Hibernate 6)**: id and foreign key columns are generated as native
  H2 `uuid` instead of `binary(255)`. With the default (production) schema, reading, updating
  and deleting stored customers, creating accounts for existing customers and transfers now work
  instead of returning 500. This is the only behavior change observed by the e2e suite.
- Security behavior is unchanged: all endpoints (API, H2 console, actuator health, OpenAPI docs,
  Swagger UI) remain accessible without credentials, CSRF stays disabled, `X-Frame-Options`
  stays absent, `POST /logout` still redirects to `/login?logout`, and `/login` is still 404.

## Verification Results

### Build and Test Status

**Java Runtime Used**:

```text
openjdk version "17.0.19" 2026-04-21
OpenJDK Runtime Environment (build 17.0.19+10-1-22.04.2-Ubuntu)
OpenJDK 64-Bit Server VM (build 17.0.19+10-1-22.04.2-Ubuntu, mixed mode, sharing)
```

- Baseline (Boot 2.7.18, before any change) `./mvnw -B clean verify`: 76 tests, 0 failures
- `./mvnw -B clean verify`: BUILD SUCCESS, 76 tests, 0 failures, 0 errors, 0 skipped
- `./mvnw -B test`: BUILD SUCCESS, 76 tests, 0 failures, 0 errors, 0 skipped

| Test class | Tests |
|------------|-------|
| `AccountApiE2ETest` | 10 |
| `ApiDocsE2ETest` | 6 |
| `CustomerApiE2ETest` | 17 |
| `DefaultSchemaBaselineE2ETest` | 17 |
| `SecurityE2ETest` | 14 |
| `TransferApiE2ETest` | 11 |
| `BankingApplicationTests` | 1 |

### Application Startup Verification

**Startup Command**: `java -jar target/bank-app-1.0.0.jar`

- Spring Boot version: 3.3.13, Hibernate ORM version: 6.5.3.Final
- Started in approximately 3.4 seconds
- Tomcat started on port 8989 with context path '/bank-api'
- H2 console available at '/h2-console'
- No ERROR messages; the only WARN is the existing `spring.jpa.open-in-view` notice

| Endpoint | Status | Response |
|----------|--------|----------|
| `/swagger-ui/index.html` | 200 OK | Swagger UI HTML |
| `/swagger-ui.html` | 302 Redirect | `/bank-api/swagger-ui/index.html` |
| `/v3/api-docs` | 200 OK | OpenAPI 3.0.1, title 'BANKING APPLICATION REST API', 7 paths |
| `/v3/api-docs/swagger-config` | 200 OK | `url` is `/bank-api/v3/api-docs` |
| `/actuator/health` | 200 OK | `{"status":"UP"}` |
| `/customers/all` | 200 OK | Empty array (expected) |
| `/h2-console/` | 200 OK | H2 Console HTML page |

## Rollback Plan

If rollback to Spring Boot 2.7 / Java 11 is needed:

1. Revert `pom.xml` (parent `2.7.18`, Java `11`, `springdoc-openapi-ui:1.6.15`, JAXB runtime)
2. Revert the `jakarta.persistence` imports to `javax.persistence`
3. Restore the `WebSecurityConfigurerAdapter` based `SecurityConfig`
4. Revert the CI workflow to JDK 11 and `DefaultSchemaBaselineE2ETest` to the Boot 2.7 version

---

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
