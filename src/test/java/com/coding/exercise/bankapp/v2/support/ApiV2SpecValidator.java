package com.coding.exercise.bankapp.v2.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

public final class ApiV2SpecValidator {

	private final OpenApiInteractionValidator validator;

	private ApiV2SpecValidator(String specification) {
		validator = OpenApiInteractionValidator.createForInlineApiSpecification(specification).build();
	}

	public static ApiV2SpecValidator forGroup(String groupId) {
		String resource = "openapi/v2/" + groupId + ".yaml";
		ClassLoader classLoader = ApiV2SpecValidator.class.getClassLoader();
		try (InputStream stream = classLoader.getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IllegalArgumentException("OpenAPI specification not found on the classpath: " + resource);
			}
			return new ApiV2SpecValidator(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to read OpenAPI specification: " + resource, exception);
		}
	}

	public ValidationReport validate(HttpMethod method, String path, String requestBody,
			ResponseEntity<String> response) {
		Request request = request(method, path, requestBody);
		Response validatorResponse = response(response);
		return validator.validate(request, validatorResponse);
	}

	public void assertValid(HttpMethod method, String path, String requestBody, ResponseEntity<String> response) {
		ValidationReport report = validate(method, path, requestBody, response);
		if (report.hasErrors()) {
			throw new AssertionError("OpenAPI validation failed:" + System.lineSeparator() + report);
		}
	}

	private static Request request(HttpMethod method, String path, String requestBody) {
		String normalizedPath = stripContextPath(path);
		UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(normalizedPath);
		URI uri = uriBuilder.build().toUri();
		String requestPath = uri.getRawPath();
		SimpleRequest.Builder builder = new SimpleRequest.Builder(method.name(), requestPath);
		if (requestBody != null) {
			builder.withHeader("Content-Type", "application/json").withBody(requestBody);
		}
		Map<String, List<String>> queryParams = uriBuilder.build().getQueryParams();
		queryParams.forEach((name, values) -> values.forEach(value -> builder.withQueryParam(name, value)));
		return builder.build();
	}

	private static String stripContextPath(String path) {
		if (path.equals("/bank-api")) {
			return "/";
		}
		if (path.startsWith("/bank-api/")) {
			return path.substring("/bank-api".length());
		}
		return path;
	}

	private static Response response(ResponseEntity<String> response) {
		SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.getStatusCode().value());
		response.getHeaders().forEach((name, values) -> values.forEach(value -> builder.withHeader(name, value)));
		if (response.getBody() != null && !response.getBody().isEmpty()) {
			builder.withBody(response.getBody());
		}
		return builder.build();
	}
}
