package com.coding.exercise.bankapp.v2.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiV2ComparisonTest {
    @Test
    void equalJsonAndObjectFieldOrderingPass() {
        ApiV2Comparison.assertJsonEquals("{\"a\":1,\"b\":{\"x\":true}}", "{\"b\":{\"x\":true},\"a\":1}");
    }

    @Test
    void valueDifferenceNamesPointerAndBothValues() {
        assertThatThrownBy(() -> ApiV2Comparison.assertJsonEquals(
                "{\"name\":\"Ada\"}", "{\"name\":\"Grace\"}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/name")
                .hasMessageContaining("\"Ada\"")
                .hasMessageContaining("\"Grace\"");
    }

    @Test
    void missingFieldIsReported() {
        assertThatThrownBy(() -> ApiV2Comparison.assertJsonEquals("{\"id\":1}", "{}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/id")
                .hasMessageContaining("<missing>");
    }

    @Test
    void arrayLengthAndMissingEntriesAreReported() {
        assertThatThrownBy(() -> ApiV2Comparison.assertJsonEquals("[1,2]", "[1]"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/length")
                .hasMessageContaining("/1")
                .hasMessageContaining("<missing>");
    }

    @Test
    void exactPointerCanIgnoreAField() {
        ApiV2Comparison.assertJsonEquals(
                "{\"created\":\"one\",\"id\":1}", "{\"created\":\"two\",\"id\":1}", "/created");
    }

    @Test
    void wildcardPointerMatchesAnyArrayIndexAndField() {
        ApiV2Comparison.assertJsonEquals(
                "{\"items\":[{\"created\":\"one\"},{\"created\":\"two\"}]}",
                "{\"items\":[{\"created\":\"three\"},{\"created\":\"four\"}]}",
                "/items/*/created");
    }

    @Test
    void bareFieldNameCanIgnoreThatFieldAtAnyDepth() {
        ApiV2Comparison.assertJsonEquals(
                "{\"a\":{\"created\":\"one\"},\"b\":{\"created\":\"two\"}}",
                "{\"a\":{\"created\":\"three\"},\"b\":{\"created\":\"four\"}}",
                "created");
    }

    @Test
    void invalidJsonShowsTheBody() {
        assertThatThrownBy(() -> ApiV2Comparison.assertJsonEquals("{", "{}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Invalid V1 JSON body")
                .hasMessageContaining("{");
    }
}
