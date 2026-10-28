package com.coding.exercise.bankapp.v2;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContext;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;

@Configuration
public class ApiV2OpenApiConfig {
    @Bean
    public GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/**")
                .pathsToExclude("/v2/**")
                .build();
    }

    @Bean
    public GroupedOpenApi v2Api() {
        return GroupedOpenApi.builder()
                .group("v2")
                .pathsToMatch("/v2/**")
                .build();
    }

    @Bean
    public OpenApiCustomizer defaultApiDocumentCustomizer(ObjectMapper objectMapper) {
        return openApi -> pruneDefaultDocument(openApi, objectMapper);
    }

    @Bean
    public ApplicationRunner restoreSwaggerDefaultUrl(
            SwaggerUiConfigProperties properties,
            SwaggerUiConfigParameters parameters,
            ServletContext servletContext) {
        return args -> {
            String url = servletContext.getContextPath() + "/v3/api-docs";
            properties.setUrl(url);
            parameters.setUrl(url);
        };
    }

    private void pruneDefaultDocument(OpenAPI openApi, ObjectMapper objectMapper) {
        if (openApi.getPaths() == null) {
            return;
        }

        openApi.getPaths().keySet().removeIf(path -> path.startsWith(ApiV2.BASE_PATH));
        Set<String> usedTags = new HashSet<>();
        JsonNode remainingPaths = objectMapper.valueToTree(openApi.getPaths());
        collectUsedTags(openApi.getPaths().values(), usedTags);

        if (openApi.getTags() != null) {
            openApi.getTags().removeIf(tag -> !usedTags.contains(tag.getName()));
            if (openApi.getTags().isEmpty()) {
                openApi.setTags(null);
            }
        }

        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        Set<String> reachableSchemas = reachableSchemas(remainingPaths, schemas, objectMapper);
        schemas.keySet().removeIf(schema -> !reachableSchemas.contains(schema));
    }

    private void collectUsedTags(Iterable<PathItem> paths, Set<String> tags) {
        for (PathItem path : paths) {
            for (Operation operation : path.readOperations()) {
                if (operation.getTags() != null) {
                    tags.addAll(operation.getTags());
                }
            }
        }
    }

    private Set<String> reachableSchemas(
            JsonNode paths, Map<String, Schema> schemas, ObjectMapper objectMapper) {
        Set<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        collectSchemaRefs(paths, pending);
        while (!pending.isEmpty()) {
            String schema = pending.removeFirst();
            if (reachable.add(schema) && schemas.containsKey(schema)) {
                collectSchemaRefs(objectMapper.valueToTree(schemas.get(schema)), pending);
            }
        }
        return reachable;
    }

    private void collectSchemaRefs(JsonNode node, ArrayDeque<String> refs) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("$ref".equals(field.getKey()) && field.getValue().isTextual()) {
                    String reference = field.getValue().asText();
                    String prefix = "#/components/schemas/";
                    if (reference.startsWith(prefix)) {
                        refs.addLast(reference.substring(prefix.length()));
                    }
                } else {
                    collectSchemaRefs(field.getValue(), refs);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectSchemaRefs(child, refs);
            }
        }
    }
}
