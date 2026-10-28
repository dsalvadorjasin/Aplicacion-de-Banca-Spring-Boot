package com.coding.exercise.bankapp.v2.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonComparison {

	private static final int MAX_VALUE_LENGTH = 240;
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private JsonComparison() {
	}

	public static List<String> differences(String v1Json, String v2Json, Collection<String> ignoredPointers,
			Collection<String> ignoredFields) {
		List<String> differences = new ArrayList<>();
		JsonNode v1 = parse(v1Json);
		JsonNode v2 = parse(v2Json);
		if (v1 == null || v2 == null) {
			String left = v1 == null ? nonJson(v1Json) : display(v1);
			String right = v2 == null ? nonJson(v2Json) : display(v2);
			differences.add(format("", left, right));
			return differences;
		}

		List<List<String>> pointers = normalizedPointers(ignoredPointers);
		Set<String> fields = ignoredFields == null ? Set.of() : Set.copyOf(ignoredFields);
		compare(v1, v2, new ArrayList<>(), pointers, fields, differences);
		return differences;
	}

	public static void assertEquivalent(String v1Json, String v2Json, Collection<String> ignoredPointers,
			Collection<String> ignoredFields) {
		List<String> differences = differences(v1Json, v2Json, ignoredPointers, ignoredFields);
		if (!differences.isEmpty()) {
			throw new AssertionError(String.join(System.lineSeparator(), differences));
		}
	}

	private static JsonNode parse(String json) {
		if (json == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readTree(json);
		} catch (IOException | RuntimeException exception) {
			return null;
		}
	}

	private static void compare(JsonNode v1, JsonNode v2, List<String> path, List<List<String>> ignoredPointers,
			Set<String> ignoredFields, List<String> differences) {
		if (isIgnored(path, ignoredPointers)) {
			return;
		}
		if (v1.isObject() && v2.isObject()) {
			compareObjects(v1, v2, path, ignoredPointers, ignoredFields, differences);
			return;
		}
		if (v1.isArray() && v2.isArray()) {
			compareArrays(v1, v2, path, ignoredPointers, ignoredFields, differences);
			return;
		}
		if (v1.isNumber() && v2.isNumber()
				&& v1.decimalValue().compareTo(v2.decimalValue()) == 0) {
			return;
		}
		if (!v1.equals(v2)) {
			differences.add(format(pointer(path), display(v1), display(v2)));
		}
	}

	private static void compareObjects(JsonNode v1, JsonNode v2, List<String> path, List<List<String>> ignoredPointers,
			Set<String> ignoredFields, List<String> differences) {
		Set<String> names = new LinkedHashSet<>();
		v1.fieldNames().forEachRemaining(names::add);
		v2.fieldNames().forEachRemaining(names::add);
		for (String name : names) {
			if (ignoredFields.contains(name)) {
				continue;
			}
			List<String> childPath = new ArrayList<>(path);
			childPath.add(name);
			JsonNode left = v1.get(name);
			JsonNode right = v2.get(name);
			if (left == null || right == null) {
				if (!isIgnored(childPath, ignoredPointers)) {
					differences.add(format(pointer(childPath), left == null ? "<missing>" : display(left),
							right == null ? "<missing>" : display(right)));
				}
			} else {
				compare(left, right, childPath, ignoredPointers, ignoredFields, differences);
			}
		}
	}

	private static void compareArrays(JsonNode v1, JsonNode v2, List<String> path, List<List<String>> ignoredPointers,
			Set<String> ignoredFields, List<String> differences) {
		if (v1.size() != v2.size()) {
			differences.add(format(pointer(path), v1.size() + " items", v2.size() + " items"));
		}
		int sharedLength = Math.min(v1.size(), v2.size());
		for (int index = 0; index < sharedLength; index++) {
			List<String> childPath = new ArrayList<>(path);
			childPath.add(Integer.toString(index));
			compare(v1.get(index), v2.get(index), childPath, ignoredPointers, ignoredFields, differences);
		}
	}

	private static List<List<String>> normalizedPointers(Collection<String> ignoredPointers) {
		if (ignoredPointers == null) {
			return List.of();
		}
		return ignoredPointers.stream()
				.filter(pointer -> pointer != null)
				.map(JsonComparison::decodePointer)
				.toList();
	}

	private static List<String> decodePointer(String pointer) {
		if (pointer.isEmpty()) {
			return List.of();
		}
		if (!pointer.startsWith("/")) {
			throw new IllegalArgumentException("JSON pointer must be empty or start with '/': " + pointer);
		}
		return Arrays.stream(pointer.substring(1).split("/", -1))
				.map(token -> token.replace("~1", "/").replace("~0", "~"))
				.toList();
	}

	private static boolean isIgnored(List<String> path, List<List<String>> ignoredPointers) {
		for (List<String> tokens : ignoredPointers) {
			if (tokens.size() != path.size()) {
				continue;
			}
			boolean matches = true;
			for (int index = 0; index < tokens.size(); index++) {
				if (!"*".equals(tokens.get(index)) && !tokens.get(index).equals(path.get(index))) {
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

	private static String pointer(List<String> path) {
		StringBuilder pointer = new StringBuilder();
		for (String token : path) {
			pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
		}
		return pointer.toString();
	}

	private static String format(String pointer, String v1, String v2) {
		return "at " + (pointer.isEmpty() ? "/" : pointer) + ": V1=" + truncate(v1) + " V2=" + truncate(v2);
	}

	private static String display(JsonNode node) {
		return node.toString();
	}

	private static String nonJson(String body) {
		return "<non-JSON body: " + String.valueOf(body) + ">";
	}

	private static String truncate(String value) {
		if (value.length() <= MAX_VALUE_LENGTH) {
			return value;
		}
		return value.substring(0, MAX_VALUE_LENGTH) + "…";
	}
}
