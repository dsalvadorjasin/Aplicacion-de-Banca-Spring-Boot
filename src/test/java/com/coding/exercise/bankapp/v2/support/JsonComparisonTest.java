package com.coding.exercise.bankapp.v2.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class JsonComparisonTest {

	@Test
	void comparesNestedObjectsAndArraysWithWildcardPointersAndIgnoredFields() {
		String v1 = """
				{"accounts":[{"balance":10.0,"created":"old"},{"balance":20,"created":"old"}],
				 "profile":{"name":"Ada","metadata":{"version":1}}}
				""";
		String v2 = """
				{"accounts":[{"balance":10,"created":"new"},{"balance":20.00,"created":"new"}],
				 "profile":{"name":"Ada","metadata":{"version":2}}}
				""";

		List<String> differences = JsonComparison.differences(
				v1, v2, List.of("/profile/metadata/version"), List.of("created"));

		assertThat(differences).isEmpty();
	}

	@Test
	void reportsEveryChangedAndMissingFieldUsingJsonPointers() {
		String v1 = """
				{"profile":{"email":"old@example.test"},"items":[{"name":"one"},{"name":"two"}]}
				""";
		String v2 = """
				{"profile":{"email":"new@example.test","active":true},"items":[{"name":"one"},{"name":"changed"}]}
				""";

		List<String> differences = JsonComparison.differences(v1, v2, List.of(), List.of());

		assertThat(differences).containsExactly(
				"at /profile/email: V1=\"old@example.test\" V2=\"new@example.test\"",
				"at /profile/active: V1=<missing> V2=true",
				"at /items/1/name: V1=\"two\" V2=\"changed\"");
	}

	@Test
	void reportsArrayLengthAndEscapesPointerTokens() {
		List<String> differences = JsonComparison.differences(
				"{\"a/b\":{\"~key\":1},\"values\":[1,2]}",
				"{\"a/b\":{\"~key\":2},\"values\":[1]}",
				List.of(), List.of());

		assertThat(differences).containsExactly(
				"at /a~1b/~0key: V1=1 V2=2",
				"at /values: V1=2 items V2=1 items");
	}

	@Test
	void identifiesNonJsonSideAndShowsItsBody() {
		List<String> differences = JsonComparison.differences(
				"not-json-response", "{\"ok\":true}", List.of(), List.of());

		assertThat(differences).singleElement()
				.asString()
				.contains("V1=<non-JSON body: not-json-response>")
				.contains("V2={\"ok\":true}");
		assertThatThrownBy(() -> JsonComparison.assertEquivalent(
				"not-json-response", "{\"ok\":true}", List.of(), List.of()))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("not-json-response");
	}
}
