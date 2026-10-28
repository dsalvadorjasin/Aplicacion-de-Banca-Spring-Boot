package com.coding.exercise.bankapp.v2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public abstract class ApiV2Exception extends ErrorResponseException {

	private static final long serialVersionUID = 1L;

	protected ApiV2Exception(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}
}
