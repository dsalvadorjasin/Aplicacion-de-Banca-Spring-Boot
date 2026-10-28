package com.coding.exercise.bankapp.v2;

import java.net.URI;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class ApiV2 {
    public static final String BASE_PATH = "/v2";

    private ApiV2() {
    }

    public static URI location(String pathTemplate, Object... uriVariables) {
        if (pathTemplate == null || !pathTemplate.startsWith(BASE_PATH + "/")) {
            throw new IllegalArgumentException("V2 locations must start with " + BASE_PATH + "/");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(pathTemplate)
                .buildAndExpand(uriVariables)
                .toUri();
    }
}
