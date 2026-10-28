package com.coding.exercise.bankapp.v2;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public final class ApiV2PathExceptionResolver implements HandlerExceptionResolver, Ordered {

	private static final String V2_PACKAGE = "com.coding.exercise.bankapp.v2";

	private final ObjectMapper objectMapper;

	public ApiV2PathExceptionResolver(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception exception) {
		if (!isV2Request(request) || !(exception instanceof ErrorResponse errorResponse) || isV2Handler(handler)) {
			return null;
		}

		ProblemDetail problem = errorResponse.getBody();
		problem.setStatus(errorResponse.getStatusCode().value());
		if (problem.getInstance() == null) {
			problem.setInstance(URI.create(request.getRequestURI()));
		}

		response.setStatus(errorResponse.getStatusCode().value());
		errorResponse.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		try {
			objectMapper.writeValue(response.getOutputStream(), problem);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to serialize V2 ProblemDetail", e);
		}
		return new ModelAndView();
	}

	private boolean isV2Request(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty()) {
			if (!requestUri.startsWith(contextPath)) {
				return false;
			}
			requestUri = requestUri.substring(contextPath.length());
		}
		return requestUri.equals(ApiV2.BASE_PATH) || requestUri.startsWith(ApiV2.BASE_PATH + "/");
	}

	private boolean isV2Handler(Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return false;
		}
		String packageName = handlerMethod.getBeanType().getPackageName();
		return packageName.equals(V2_PACKAGE) || packageName.startsWith(V2_PACKAGE + ".");
	}
}
