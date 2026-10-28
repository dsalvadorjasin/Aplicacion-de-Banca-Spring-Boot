package com.coding.exercise.bankapp.v2.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
class EndpointInventoryTest {

	private static final String APPLICATION_PACKAGE = "com.coding.exercise.bankapp";
	private static final String V2_PACKAGE = APPLICATION_PACKAGE + ".v2";
	private static final Path INVENTORY_PATH = Path.of("docs/api-v2/endpoint-groups.json");

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void inventoryContainsEveryApplicationV1EndpointExactlyOnce() throws IOException {
		JsonNode root = objectMapper.readTree(INVENTORY_PATH.toFile());
		JsonNode groups = root.path("groups");
		Set<String> applicationEndpoints = discoverEndpoints();
		Map<String, Integer> inventoryCounts = new HashMap<>();

		for (JsonNode group : groups) {
			for (JsonNode endpoint : group.path("endpoints")) {
				inventoryCounts.merge(endpoint.asText(), 1, Integer::sum);
			}
			assertGroupMetadata(group);
		}

		Set<String> inventoryEndpoints = inventoryCounts.keySet();
		Set<String> missing = new LinkedHashSet<>(applicationEndpoints);
		missing.removeAll(inventoryEndpoints);
		Set<String> extra = new LinkedHashSet<>(inventoryEndpoints);
		extra.removeAll(applicationEndpoints);
		Set<String> duplicated = new LinkedHashSet<>();
		inventoryCounts.forEach((endpoint, count) -> {
			if (count != 1) {
				duplicated.add(endpoint + " appears " + count + " times");
			}
		});

		assertThat(missing).as("V1 endpoints missing from %s", INVENTORY_PATH).isEmpty();
		assertThat(extra).as("Inventory endpoints that are not V1 handlers").isEmpty();
		assertThat(duplicated).as("Each V1 endpoint must occur in exactly one group").isEmpty();
		assertThat(inventoryCounts.values().stream().mapToInt(Integer::intValue).sum())
				.as("Every discovered V1 endpoint must be listed once")
				.isEqualTo(applicationEndpoints.size());
	}

	private Set<String> discoverEndpoints() {
		Set<String> endpoints = new LinkedHashSet<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
			Class<?> beanType = entry.getValue().getBeanType();
			String packageName = beanType.getPackageName();
			if (!packageName.startsWith(APPLICATION_PACKAGE) || packageName.startsWith(V2_PACKAGE)) {
				continue;
			}
			Set<String> paths = entry.getKey().getPatternValues();
			Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
			for (RequestMethod method : methods) {
				for (String path : paths) {
					endpoints.add(method.name() + " " + path);
				}
			}
		}
		return endpoints;
	}

	private static void assertGroupMetadata(JsonNode group) {
		String id = group.path("id").asText();
		String packageName = id.replace("-", "");
		Set<String> endpoints = textValues(group.path("endpoints"));
		Set<String> expectedV2Endpoints = new HashSet<>();
		for (String endpoint : endpoints) {
			int separator = endpoint.indexOf(' ');
			expectedV2Endpoints.add(endpoint.substring(0, separator + 1)
					+ "/v2" + endpoint.substring(separator + 1));
		}
		assertThat(textList(group.path("v2_endpoints")))
				.as("V2 endpoint mapping for group %s", id)
				.containsExactlyInAnyOrderElementsOf(expectedV2Endpoints);

		Set<String> expectedOwnedPaths = Set.of(
				"src/main/java/com/coding/exercise/bankapp/v2/" + packageName + "/",
				"src/main/resources/openapi/v2/" + id + ".yaml",
				"src/test/java/com/coding/exercise/bankapp/v2/" + packageName + "/");
		assertThat(textList(group.path("owned_paths")))
				.as("Owned paths for group %s", id)
				.containsExactlyInAnyOrderElementsOf(expectedOwnedPaths);
	}

	private static Set<String> textValues(JsonNode node) {
		return textList(node).stream().collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static List<String> textList(JsonNode node) {
		List<String> values = new ArrayList<>();
		node.forEach(value -> values.add(value.asText()));
		return values;
	}
}
