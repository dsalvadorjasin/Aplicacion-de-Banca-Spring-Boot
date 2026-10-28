package com.coding.exercise.bankapp.v2.customerwrites;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.Yaml;

class CustomerWritesSpecTest extends CustomerWritesTestSupport {

	private static final Set<String> EXPECTED_OPERATIONS = Set.of(
			"POST /v2/customers/add",
			"PUT /v2/customers/{customerNumber}",
			"DELETE /v2/customers/{customerNumber}");

	@Test
	void specificationIsSelfContainedAndDocumentsEveryWriteOperation() {
		Map<?, ?> specification = loadSpecification();

		assertThat(specification.get("openapi")).isEqualTo("3.0.3");
		assertThat(specification.get("servers")).isEqualTo(List.of(Map.of("url", "/")));
		Map<?, ?> paths = asMap(specification.get("paths"));
		Set<String> operations = new LinkedHashSet<>();
		for (Map.Entry<?, ?> pathEntry : paths.entrySet()) {
			Map<?, ?> pathItem = asMap(pathEntry.getValue());
			for (Map.Entry<?, ?> operationEntry : pathItem.entrySet()) {
				String method = String.valueOf(operationEntry.getKey()).toUpperCase();
				if (Set.of("POST", "PUT", "DELETE").contains(method)) {
					String operation = method + " " + pathEntry.getKey();
					operations.add(operation);
					assertThat(asMap(operationEntry.getValue()).get("x-v1-differences"))
							.as("V1 differences for %s", operation)
							.isInstanceOf(List.class);
					assertThat((List<?>) asMap(operationEntry.getValue()).get("x-v1-differences"))
							.as("V1 differences for %s", operation)
							.isNotEmpty();
				}
			}
		}
		assertThat(operations).isEqualTo(EXPECTED_OPERATIONS);

		List<String> references = new ArrayList<>();
		collectReferences(specification, references);
		assertThat(references).isNotEmpty().allMatch(reference -> reference.startsWith("#/"));
		Map<?, ?> components = asMap(specification.get("components"));
		Map<?, ?> schemas = asMap(components.get("schemas"));
		assertThat(schemas.containsKey("ProblemDetail")).isTrue();
	}

	@Test
	void generatedV2OpenApiDocumentContainsCustomerWritePaths() {
		ResponseEntity<String> response = get("/v3/api-docs/v2");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(json(response).path("paths").has("/v2/customers/add")).isTrue();
		assertThat(json(response).path("paths").has("/v2/customers/{customerNumber}")).isTrue();
	}

	private Map<?, ?> loadSpecification() {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(
				"openapi/v2/customer-writes.yaml")) {
			assertThat(input).isNotNull();
			return asMap(new Yaml().load(input));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to read customer-writes OpenAPI specification", exception);
		}
	}

	private static Map<?, ?> asMap(Object value) {
		return (Map<?, ?>) value;
	}

	private static void collectReferences(Object value, List<String> references) {
		if (value instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if ("$ref".equals(entry.getKey()) && entry.getValue() instanceof String reference) {
					references.add(reference);
				} else {
					collectReferences(entry.getValue(), references);
				}
			}
		} else if (value instanceof Iterable<?> values) {
			for (Object item : values) {
				collectReferences(item, references);
			}
		}
	}
}
