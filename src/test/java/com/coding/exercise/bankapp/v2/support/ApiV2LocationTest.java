package com.coding.exercise.bankapp.v2.support;

import com.coding.exercise.bankapp.v2.ApiV2;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiV2LocationTest {
    @Test
    void locationUsesCurrentHostAndContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8989);
        request.setContextPath("/bank-api");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(ApiV2.location(ApiV2.BASE_PATH + "/things/{n}", 7))
                    .hasToString("http://localhost:8989/bank-api/v2/things/7");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void locationRejectsNonV2Templates() {
        assertThatThrownBy(() -> ApiV2.location("/customers/{n}", 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
