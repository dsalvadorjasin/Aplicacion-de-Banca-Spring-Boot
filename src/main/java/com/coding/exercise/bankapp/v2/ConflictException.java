package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;

public final class ConflictException extends ApiV2Exception {

	private static final long serialVersionUID = 1L;

	public ConflictException(String detail) {
		super(HttpStatus.CONFLICT, detail);
	}
}
