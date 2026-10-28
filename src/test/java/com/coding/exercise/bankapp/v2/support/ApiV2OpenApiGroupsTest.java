package com.coding.exercise.bankapp.v2.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

class ApiV2OpenApiGroupsTest extends ApiV2TestSupport {

	@Test
	void servesRootAndVersionedOpenApiDocumentsWithExpectedGroups() {
		ResponseEntity<String> rootResponse = get("/v3/api-docs");
		ResponseEntity<String> v1Response = get("/v3/api-docs/v1");
		ResponseEntity<String> v2Response = get("/v3/api-docs/v2");
		ResponseEntity<String> configResponse = get("/v3/api-docs/swagger-config");
		ResponseEntity<String> swaggerResponse = get("/swagger-ui/index.html");

		assertThat(rootResponse.getStatusCode().value()).isEqualTo(200);
		assertThat(v1Response.getStatusCode().value()).isEqualTo(200);
		assertThat(v2Response.getStatusCode().value()).isEqualTo(200);
		assertThat(configResponse.getStatusCode().value()).isEqualTo(200);
		assertThat(swaggerResponse.getStatusCode().value()).isEqualTo(200);

		JsonNode rootPaths = json(rootResponse).path("paths");
		JsonNode v1Paths = json(v1Response).path("paths");
		assertThat(pathNames(rootPaths)).contains(
				"/customers/all", "/customers/add", "/customers/{customerNumber}",
				"/accounts/{accountNumber}", "/accounts/add/{customerNumber}",
				"/accounts/transfer/{customerNumber}", "/accounts/transactions/{accountNumber}");
		assertThat(pathNames(rootPaths)).doesNotContain("/v2/customers/all");
		assertThat(pathNames(v1Paths)).containsExactlyInAnyOrderElementsOf(pathNames(rootPaths));
		assertThat(operationCount(v1Paths)).isEqualTo(9);
		assertThat(pathNames(json(v2Response).path("paths")))
				.doesNotContain("/customers/all", "/accounts/{accountNumber}");

		List<String> groupNames = new ArrayList<>();
		json(configResponse).path("urls").forEach(group -> groupNames.add(group.path("name").asText()));
		assertThat(groupNames).containsExactlyInAnyOrder("v1", "v2");
	}

	private static List<String> pathNames(JsonNode paths) {
		List<String> names = new ArrayList<>();
		paths.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static int operationCount(JsonNode paths) {
		int count = 0;
		for (JsonNode path : paths) {
			for (JsonNode operation : path) {
				if (operation.isObject()) {
					count++;
				}
			}
		}
		return count;
	}
}
