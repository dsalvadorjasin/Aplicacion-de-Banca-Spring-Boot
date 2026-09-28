package com.coding.exercise.bankapp.e2e;

import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertCreated;
import static com.coding.exercise.bankapp.e2e.ResponseEntityAssertions.assertStatusAndBody;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Pins the observed behavior of SecurityConfig: it overrides the default configuration with only
 * {@code permitAll()} rules for "/" and "/h2-console/**" and never configures
 * {@code anyRequest()}, {@code httpBasic()} or {@code formLogin()}. As a result every endpoint is
 * reachable anonymously, credentials (valid or not) are ignored, there is no Basic challenge or
 * login redirect, and CSRF protection is disabled.
 */
class SecurityE2ETest extends E2ETestSupport {

	private static final String VALID_USER = "bankapp";
	private static final String VALID_PASSWORD = "changeit";

	@Test
	void apiIsAccessibleWithoutCredentials() {
		ResponseEntity<String> response = get("/customers/all");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response)).isEmpty();
		assertThat(response.getHeaders().containsKey(HttpHeaders.WWW_AUTHENTICATE)).isFalse();
		assertThat(response.getHeaders().getLocation()).isNull();
	}

	@Test
	void apiIsAccessibleWithValidBasicCredentials() {
		ResponseEntity<String> response = rest.withBasicAuth(VALID_USER, VALID_PASSWORD)
				.getForEntity("/customers/all", String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response)).isEmpty();
	}

	@Test
	void invalidBasicCredentialsAreIgnored() {
		ResponseEntity<String> response = rest.withBasicAuth(VALID_USER, "wrong-password")
				.getForEntity("/customers/all", String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().containsKey(HttpHeaders.WWW_AUTHENTICATE)).isFalse();
	}

	@Test
	void unauthenticatedWriteWithoutCsrfTokenSucceeds() {
		assertCreated(postJson("/customers/add", customerPayload(uniqueNumber(), "John", "Doe")),
				"New Customer created successfully.");
	}

	@Test
	void unauthenticatedDeleteWithoutCsrfTokenReachesController() {
		assertStatusAndBody(delete("/customers/" + uniqueNumber()), HttpStatus.BAD_REQUEST, "Customer does not exist.");
	}

	@Test
	void rootPathIsPermittedButHasNoHandler() {
		ResponseEntity<String> response = get("/");

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).contains("\"status\":404").contains("\"path\":\"/bank-api/\"");
	}

	@Test
	void noLoginPageIsExposed() {
		ResponseEntity<String> response = nonRedirectingClient().getForEntity(url("/login"), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void defaultLogoutEndpointRedirectsToLoginPage() {
		ResponseEntity<String> response = nonRedirectingClient().postForEntity(url("/logout"), null, String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(302);
		assertThat(response.getHeaders().getLocation()).isNotNull();
		assertThat(response.getHeaders().getLocation().toString()).endsWith("/bank-api/login?logout");
	}

	@Test
	void h2ConsoleRootRedirectsToTrailingSlash() {
		ResponseEntity<String> response = nonRedirectingClient().getForEntity(url("/h2-console"), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(302);
		assertThat(response.getHeaders().getLocation().toString()).endsWith("/bank-api/h2-console/");
	}

	@Test
	void h2ConsoleIsAccessibleWithoutCredentials() {
		RestTemplate client = nonRedirectingClient();

		ResponseEntity<String> response = client.getForEntity(url("/h2-console/"), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
		assertThat(response.getBody()).contains("<title>H2 Console</title>");
	}

	@Test
	void securityHeadersArePresentButFrameOptionsAreDisabled() {
		HttpHeaders headers = get("/customers/all").getHeaders();

		assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(headers.getCacheControl()).contains("no-store");
		assertThat(headers.containsKey("X-Frame-Options")).isFalse();
	}

	@Test
	void h2ConsoleCanBeFramed() {
		ResponseEntity<String> response = nonRedirectingClient().getForEntity(url("/h2-console/"), String.class);

		assertThat(response.getHeaders().containsKey("X-Frame-Options")).isFalse();
	}

	@Test
	void actuatorHealthIsAccessibleWithoutCredentials() {
		ResponseEntity<String> response = get("/actuator/health");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response).get("status").asText()).isEqualTo("UP");
	}

	@Test
	void actuatorExposesOnlyHealthOverHttp() {
		ResponseEntity<String> index = get("/actuator");

		assertThat(index.getStatusCode().value()).isEqualTo(200);
		assertThat(json(index).get("_links").has("health")).isTrue();
		assertThat(json(index).get("_links").has("env")).isFalse();
		assertThat(get("/actuator/env").getStatusCode().value()).isEqualTo(404);
	}
}
