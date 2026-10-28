package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;

public abstract class ApiV2Exception extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    protected ApiV2Exception(String detail, HttpStatus status) {
        super(detail);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
