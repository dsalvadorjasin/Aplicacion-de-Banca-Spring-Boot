package com.coding.exercise.bankapp.v2.support;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

public final class ApiV2SpecValidator {
    private final OpenApiInteractionValidator validator;

    private ApiV2SpecValidator(OpenApiInteractionValidator validator) {
        this.validator = validator;
    }

    public static ApiV2SpecValidator forGroup(String groupId) {
        return forSpec("openapi/v2/" + groupId + ".yaml");
    }

    public static ApiV2SpecValidator forSpec(String classpathLocation) {
        String location = classpathLocation.startsWith("classpath:")
                ? classpathLocation.substring("classpath:".length())
                : classpathLocation;
        while (location.startsWith("/")) {
            location = location.substring(1);
        }
        URL specification = Thread.currentThread().getContextClassLoader().getResource(location);
        if (specification == null) {
            throw new IllegalArgumentException("OpenAPI specification not found: " + classpathLocation);
        }
        try {
            return new ApiV2SpecValidator(
                    OpenApiInteractionValidator.createForSpecificationUrl(specification.toExternalForm()).build());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Could not load OpenAPI specification: " + classpathLocation, exception);
        }
    }

    public void assertValid(
            HttpMethod method, String path, String requestJsonBody, ResponseEntity<String> response) {
        SimpleRequest request = request(method, path, requestJsonBody);
        SimpleResponse simpleResponse = response(response);
        ValidationReport report = validator.validate(request, simpleResponse);
        assertNoErrors(report, method + " " + path);
    }

    public void assertValidResponse(HttpMethod method, String path, ResponseEntity<String> response) {
        URI uri = URI.create(path);
        ValidationReport report = validator.validateResponse(
                uri.getRawPath(), Request.Method.valueOf(method.name()), response(response));
        assertNoErrors(report, method + " " + path + " response");
    }

    private SimpleRequest request(HttpMethod method, String path, String body) {
        URI uri = URI.create(path);
        String requestPath = uri.getRawPath();
        SimpleRequest.Builder builder = new SimpleRequest.Builder(
                Request.Method.valueOf(method.name()), requestPath)
                .withAccept("application/json");
        if (body != null) {
            builder.withContentType("application/json").withBody(body);
        }
        if (uri.getRawQuery() != null) {
            for (String pair : uri.getRawQuery().split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                String[] keyValue = pair.split("=", 2);
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length == 1
                        ? ""
                        : URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                builder.withQueryParam(key, value);
            }
        }
        return builder.build();
    }

    private SimpleResponse response(ResponseEntity<String> response) {
        SimpleResponse.Builder builder = new SimpleResponse.Builder(response.getStatusCode().value());
        HttpHeaders headers = response.getHeaders();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            builder.withHeader(header.getKey(), header.getValue());
        }
        if (response.getBody() != null) {
            builder.withBody(response.getBody());
        }
        return builder.build();
    }

    private void assertNoErrors(ValidationReport report, String description) {
        if (report.hasErrors()) {
            throw new AssertionError(description + " does not conform to its OpenAPI specification:\n"
                    + SimpleValidationReportFormat.getInstance().apply(report));
        }
    }
}
