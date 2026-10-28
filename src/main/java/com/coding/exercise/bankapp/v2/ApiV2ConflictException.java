package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;

public class ApiV2ConflictException extends ApiV2Exception {
    private static final long serialVersionUID = 1L;

    public ApiV2ConflictException(String detail) {
        super(detail, HttpStatus.CONFLICT);
    }
}
