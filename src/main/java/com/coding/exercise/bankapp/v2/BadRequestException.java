package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;

public final class BadRequestException extends ApiV2Exception {

	private static final long serialVersionUID = 1L;

	public BadRequestException(String detail) {
		super(HttpStatus.BAD_REQUEST, detail);
	}
}
