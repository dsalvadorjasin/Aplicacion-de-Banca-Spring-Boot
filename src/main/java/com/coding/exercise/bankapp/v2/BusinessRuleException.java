package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;

public final class BusinessRuleException extends ApiV2Exception {

	private static final long serialVersionUID = 1L;

	public BusinessRuleException(String detail) {
		super(HttpStatus.UNPROCESSABLE_ENTITY, detail);
	}
}
