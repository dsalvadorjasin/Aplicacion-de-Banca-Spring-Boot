package com.coding.exercise.bankapp.v2.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

class ApiV2SpecConventionsTest {
    @Test
    void everyApiV2SpecificationFollowsTheSharedConventions() throws IOException {
        Resource[] specifications = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:openapi/v2/*.yaml");
        assertThat(specifications).isNotEmpty();
        for (Resource specification : specifications) {
            verifySpecification(specification);
        }
    }

    private void verifySpecification(Resource resource) throws IOException {
        String location = resource.getURL().toExternalForm();
        SwaggerParseResult parse = new OpenAPIV3Parser().readLocation(location, null, new ParseOptions());
        assertThat(parse.getMessages()).as(location + " parse messages").isEmpty();
        OpenAPI openApi = parse.getOpenAPI();
        assertThat(openApi).as(location).isNotNull();
        assertThat(openApi.getOpenapi()).isEqualTo("3.0.3");
        assertThat(openApi.getServers()).extracting("url").containsExactly("/");
        assertThat(openApi.getPaths()).isNotEmpty();
        for (Map.Entry<String, PathItem> path : openApi.getPaths().entrySet()) {
            assertThat(path.getKey()).startsWith("/v2/");
            for (Operation operation : path.getValue().readOperations()) {
                Object differences = operation.getExtensions() == null
                        ? null
                        : operation.getExtensions().get("x-v1-differences");
                assertThat(differences).as(path.getKey() + " x-v1-differences").isInstanceOf(List.class);
                assertThat((List<?>) differences).allMatch(String.class::isInstance);
            }
        }
        assertThat(openApi.getComponents()).isNotNull();
        assertThat(openApi.getComponents().getSchemas()).containsKey("ProblemDetail");
        assertNoExternalReferences(resource);
    }

    private void assertNoExternalReferences(Resource resource) throws IOException {
        String yaml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String line : yaml.split("\\R")) {
            if (line.contains("$ref:")) {
                String reference = line.substring(line.indexOf("$ref:") + "$ref:".length()).trim();
                reference = reference.replace("\"", "").replace("'", "");
                assertThat(reference).as(line).startsWith("#/");
            }
        }
    }
}
