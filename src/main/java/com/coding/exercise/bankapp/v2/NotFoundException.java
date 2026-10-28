package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;

public final class NotFoundException extends ApiV2Exception {

	private static final long serialVersionUID = 1L;

	public NotFoundException(String detail) {
		super(HttpStatus.NOT_FOUND, detail);
	}
}
