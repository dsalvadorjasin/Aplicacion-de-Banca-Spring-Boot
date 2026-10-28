package com.coding.exercise.bankapp.v2.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;

import com.coding.exercise.bankapp.v2.ApiV2PathExceptionResolver;

class ApiV2PathExceptionResolverTest extends ApiV2TestSupport {

	@Test
	void returnsProblemDetailForUnknownV2Url() throws Exception {
		ResponseEntity<String> response = rest.getForEntity("/v2/does-not-exist", String.class);

		assertV2Problem(response, 404, "/v2/does-not-exist");
	}

	@Test
	void returnsProblemDetailForV2Root() throws Exception {
		ResponseEntity<String> response = rest.getForEntity("/v2", String.class);

		assertV2Problem(response, 404, "/v2");
	}

	@Test
	void keepsBootDefaultJsonShapeForUnknownV1Url() throws Exception {
		ResponseEntity<String> response = rest.getForEntity("/customers/does-not-exist/extra", String.class);

		assertBootError(response, 404, "/bank-api/customers/does-not-exist/extra");
	}

	@Test
	void keepsBootDefaultJsonShapeForV1WrongMethod() throws Exception {
		ResponseEntity<String> response = rest.exchange(
				"/accounts/add/123", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

		assertBootError(response, 405, "/bank-api/accounts/add/123");
	}

	@Test
	void keepsBootDefaultJsonShapeForV1DeleteWithInvalidPathVariable() throws Exception {
		ResponseEntity<String> response = rest.exchange(
				"/customers/all", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

		assertBootError(response, 400, "/bank-api/customers/all");
	}

	@Test
	void writesProblemDetailAndAllowHeaderForV2MethodNotSupported() throws Exception {
		ApiV2PathExceptionResolver resolver = new ApiV2PathExceptionResolver(objectMapper);
		MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/bank-api/v2/x");
		request.setContextPath("/bank-api");
		MockHttpServletResponse response = new MockHttpServletResponse();
		HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException(
				"DELETE", List.of("GET"));

		ModelAndView result = resolver.resolveException(request, response, null, exception);

		assertThat(result).isNotNull();
		assertThat(result.isEmpty()).isTrue();
		assertThat(response.getStatus()).isEqualTo(405);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.getHeader(HttpHeaders.ALLOW)).isEqualTo("GET");
		assertThat(objectMapper.readTree(response.getContentAsString()).path("status").asInt()).isEqualTo(405);
	}

	@Test
	void doesNotHandleNonV2PathOrWriteAResponse() throws Exception {
		ApiV2PathExceptionResolver resolver = new ApiV2PathExceptionResolver(objectMapper);
		MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/bank-api/customers/all");
		request.setContextPath("/bank-api");
		MockHttpServletResponse response = new MockHttpServletResponse();
		HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException(
				"DELETE", List.of("GET"));

		ModelAndView result = resolver.resolveException(request, response, null, exception);

		assertThat(result).isNull();
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsByteArray()).isEmpty();
		assertThat(response.getContentType()).isNull();
	}

	private void assertV2Problem(ResponseEntity<String> response, int status, String requestPath) throws Exception {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		var problem = objectMapper.readTree(response.getBody());
		assertThat(problem.path("status").asInt()).isEqualTo(status);
		assertThat(problem.path("instance").asText()).endsWith(requestPath);
	}

	private void assertBootError(ResponseEntity<String> response, int status, String expectedPath) throws Exception {
		assertThat(response.getStatusCode().value()).isEqualTo(status);
		assertThat(response.getHeaders().getContentType()).isNotNull();
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.isFalse();
		var body = objectMapper.readTree(response.getBody());
		assertThat(body.path("status").asInt()).isEqualTo(status);
		assertThat(body.path("error").asText()).isNotBlank();
		assertThat(body.path("path").asText()).isEqualTo(expectedPath);
	}
}
