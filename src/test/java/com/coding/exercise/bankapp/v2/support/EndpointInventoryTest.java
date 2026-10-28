package com.coding.exercise.bankapp.v2.support;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:apiv2-inventory;DB_CLOSE_DELAY=-1"})
class EndpointInventoryTest {
    private static final String BASE = "src/main/java/com/coding/exercise/bankapp/v2/";

    @Autowired @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;
    @Autowired ObjectMapper mapper;

    @Test
    void inventoryPartitionsEveryV1AndV2MappingAndRespectsOwnedPaths() throws IOException {
        JsonNode inventory = mapper.readTree(Path.of("docs/api-v2/endpoint-groups.json").toFile());
        Map<String, Integer> v1Membership = new HashMap<>();
        Set<String> declaredV2 = new HashSet<>();
        for (JsonNode group : inventory.get("groups")) {
            String id = group.get("id").asText();
            for (JsonNode endpoint : group.get("endpoints")) {
                v1Membership.merge(endpoint.asText(), 1, Integer::sum);
            }
            for (JsonNode endpoint : group.get("v2_endpoints")) {
                declaredV2.add(endpoint.asText());
            }
            assertOwnedPaths(group, id);
        }

        Set<String> actualV1 = new HashSet<>();
        Set<String> actualV2 = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            if (isFrameworkHandler(entry.getValue())) {
                continue;
            }
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            Set<String> patterns = entry.getKey().getPatternValues();
            for (RequestMethod method : methods) {
                for (String pattern : patterns) {
                    String operation = method.name() + " " + pattern;
                    if (pattern.startsWith("/v2/")) {
                        actualV2.add(operation);
                    } else {
                        actualV1.add(operation);
                    }
                }
            }
        }

        assertThat(v1Membership).allSatisfy((endpoint, count) ->
                assertThat(count).as(endpoint + " group memberships").isEqualTo(1));
        assertThat(actualV1).as("V1 mappings missing from inventory").isEqualTo(v1Membership.keySet());
        assertThat(actualV2).as("V2 mappings absent from inventory").isSubsetOf(declaredV2);
        assertThat(declaredV2).containsExactlyInAnyOrderElementsOf(expectedV2Endpoints(inventory));
    }

    private void assertOwnedPaths(JsonNode group, String id) {
        String packageDirectory = id.replace("-", "");
        List<String> expected = List.of(
                BASE + packageDirectory + "/",
                "src/main/resources/openapi/v2/" + id + ".yaml",
                "src/test/java/com/coding/exercise/bankapp/v2/" + packageDirectory + "/");
        List<String> owned = new ArrayList<>();
        group.get("owned_paths").forEach(path -> owned.add(path.asText()));
        assertThat(owned).as(id + " owned_paths").containsExactlyElementsOf(expected);
    }

    private Set<String> expectedV2Endpoints(JsonNode inventory) {
        Set<String> expected = new HashSet<>();
        for (JsonNode group : inventory.get("groups")) {
            List<String> v1Endpoints = new ArrayList<>();
            group.get("endpoints").forEach(endpoint -> v1Endpoints.add(endpoint.asText()));
            List<String> v2Endpoints = new ArrayList<>();
            group.get("v2_endpoints").forEach(endpoint -> v2Endpoints.add(endpoint.asText()));
            assertThat(v2Endpoints).hasSameSizeAs(v1Endpoints);
            for (int i = 0; i < v1Endpoints.size(); i++) {
                int separator = v1Endpoints.get(i).indexOf(' ');
                String expectedEndpoint = v1Endpoints.get(i).substring(0, separator + 1)
                        + "/v2" + v1Endpoints.get(i).substring(separator + 1);
                assertThat(v2Endpoints.get(i)).isEqualTo(expectedEndpoint);
                expected.add(expectedEndpoint);
            }
        }
        return expected;
    }

    private boolean isFrameworkHandler(HandlerMethod handler) {
        String packageName = handler.getBeanType().getPackageName();
        return packageName.startsWith("org.springdoc") || packageName.startsWith("org.springframework");
    }
}
