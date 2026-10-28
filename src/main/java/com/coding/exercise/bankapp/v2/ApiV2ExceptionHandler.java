package com.coding.exercise.bankapp.v2;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackages = "com.coding.exercise.bankapp.v2")
public class ApiV2ExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiV2Exception.class)
    public ResponseEntity<Object> handleApiV2Exception(ApiV2Exception exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getMessage(), request);
    }

    @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
    public ResponseEntity<Object> handleDuplicateRecords(HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Duplicate records found for the requested identifier.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error.", request);
    }

    @Override
    protected ProblemDetail createProblemDetail(
            Exception exception,
            HttpStatusCode status,
            String defaultDetail,
            String detailMessageCode,
            Object[] detailMessageArguments,
            WebRequest request) {
        ProblemDetail problem = super.createProblemDetail(
                exception, status, defaultDetail, detailMessageCode, detailMessageArguments, request);
        setInstance(problem, request);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        if (body instanceof ProblemDetail problem) {
            setInstance(problem, request);
            responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return super.createResponseEntity(body, responseHeaders, status, request);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private void setInstance(ProblemDetail problem, WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        if (problem.getTitle() == null) {
            problem.setTitle(HttpStatus.valueOf(problem.getStatus()).getReasonPhrase());
        }
    }
}
