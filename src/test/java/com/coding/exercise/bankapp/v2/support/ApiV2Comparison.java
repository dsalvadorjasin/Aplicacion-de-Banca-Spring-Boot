package com.coding.exercise.bankapp.v2.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ApiV2Comparison {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiV2Comparison() {
    }

    public static ResponseEntity<String> exchange(
            TestRestTemplate http, HttpMethod method, String path, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return http.exchange(path, method, new HttpEntity<>(jsonBody, headers), String.class);
    }

    public static Responses compare(
            TestRestTemplate http,
            HttpMethod method,
            String v1Path,
            String v2Path,
            String jsonBody,
            String... ignoredPointers) {
        ResponseEntity<String> v1 = exchange(http, method, v1Path, jsonBody);
        ResponseEntity<String> v2 = exchange(http, method, v2Path, jsonBody);
        assertJsonEquals(v1.getBody(), v2.getBody(), ignoredPointers);
        return new Responses(v1, v2);
    }

    public static void assertJsonEquals(String v1Json, String v2Json, String... ignoredPointers) {
        JsonNode v1 = parse(v1Json, "V1");
        JsonNode v2 = parse(v2Json, "V2");
        List<String> differences = new ArrayList<>();
        Set<String> ignores = new LinkedHashSet<>(Arrays.asList(ignoredPointers));
        compareNodes(v1, v2, "", ignores, differences);
        if (!differences.isEmpty()) {
            throw new AssertionError("JSON differs:\n" + String.join("\n", differences));
        }
    }

    private static JsonNode parse(String json, String side) {
        if (json == null) {
            throw new AssertionError("Invalid " + side + " JSON body: null");
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new AssertionError("Invalid " + side + " JSON body: " + json, exception);
        }
    }

    private static void compareNodes(
            JsonNode v1, JsonNode v2, String pointer, Set<String> ignores, List<String> differences) {
        if (isIgnored(pointer, ignores)) {
            return;
        }
        if (v1 == null || v2 == null) {
            differences.add(formatDifference(pointer, v1, v2));
            return;
        }
        if (v1.isObject() && v2.isObject()) {
            Set<String> fields = new LinkedHashSet<>();
            v1.fieldNames().forEachRemaining(fields::add);
            v2.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                String childPointer = pointer + "/" + escapePointerSegment(field);
                compareNodes(v1.get(field), v2.get(field), childPointer, ignores, differences);
            }
            return;
        }
        if (v1.isArray() && v2.isArray()) {
            if (v1.size() != v2.size()) {
                differences.add(formatDifference(pointer + "/length",
                        MAPPER.getNodeFactory().numberNode(v1.size()),
                        MAPPER.getNodeFactory().numberNode(v2.size())));
            }
            int max = Math.max(v1.size(), v2.size());
            for (int i = 0; i < max; i++) {
                compareNodes(i < v1.size() ? v1.get(i) : null,
                        i < v2.size() ? v2.get(i) : null,
                        pointer + "/" + i, ignores, differences);
            }
            return;
        }
        if (!v1.equals(v2)) {
            differences.add(formatDifference(pointer, v1, v2));
        }
    }

    private static boolean isIgnored(String pointer, Set<String> ignores) {
        List<String> pointerSegments = splitPointer(pointer);
        for (String ignore : ignores) {
            if (ignore == null || ignore.isBlank()) {
                continue;
            }
            if (!ignore.startsWith("/")) {
                if (pointerSegments.contains(ignore)) {
                    return true;
                }
                continue;
            }
            List<String> ignoredSegments = splitPointer(ignore);
            if (ignoredSegments.size() > pointerSegments.size()) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < ignoredSegments.size(); i++) {
                if (!"*".equals(ignoredSegments.get(i))
                        && !ignoredSegments.get(i).equals(pointerSegments.get(i))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitPointer(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(pointer.substring(1).split("/", -1))
                .map(segment -> segment.replace("~1", "/").replace("~0", "~"))
                .toList();
    }

    private static String escapePointerSegment(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    private static String formatDifference(String pointer, JsonNode v1, JsonNode v2) {
        String location = pointer.isEmpty() ? "/" : pointer;
        return location + " — V1: " + render(v1) + "; V2: " + render(v2);
    }

    private static String render(JsonNode node) {
        return node == null ? "<missing>" : node.toString();
    }

    public record Responses(ResponseEntity<String> v1, ResponseEntity<String> v2) {
    }
}
