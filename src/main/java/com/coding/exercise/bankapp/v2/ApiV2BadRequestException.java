package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;

public class ApiV2BadRequestException extends ApiV2Exception {
    private static final long serialVersionUID = 1L;

    public ApiV2BadRequestException(String detail) {
        super(detail, HttpStatus.BAD_REQUEST);
    }
}
