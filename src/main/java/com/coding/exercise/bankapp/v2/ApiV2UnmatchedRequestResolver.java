package com.coding.exercise.bankapp.v2;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UrlPathHelper;

@Component
public class ApiV2UnmatchedRequestResolver implements HandlerExceptionResolver, Ordered {
    private static final String V2_PACKAGE = "com.coding.exercise.bankapp.v2";

    private final ApiV2ExceptionHandler apiV2ExceptionHandler;
    private final ObjectMapper objectMapper;

    public ApiV2UnmatchedRequestResolver(
            ApiV2ExceptionHandler apiV2ExceptionHandler, ObjectMapper objectMapper) {
        this.apiV2ExceptionHandler = apiV2ExceptionHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        if (!isV2Path(path) || isV2Handler(handler)) {
            return null;
        }

        ResponseEntity<Object> entity;
        try {
            entity = apiV2ExceptionHandler.handleException(exception, new ServletWebRequest(request, response));
        } catch (Exception ignored) {
            return null;
        }
        if (entity == null) {
            return null;
        }

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(entity.getBody());
        } catch (JsonProcessingException ignored) {
            return null;
        }

        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        try {
            response.getOutputStream().write(body);
        } catch (IOException ignored) {
            return null;
        }
        return new ModelAndView();
    }

    private boolean isV2Path(String path) {
        return path.equals(ApiV2.BASE_PATH) || path.startsWith(ApiV2.BASE_PATH + "/");
    }

    private boolean isV2Handler(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        String packageName = handlerMethod.getBeanType().getPackageName();
        return packageName.equals(V2_PACKAGE) || packageName.startsWith(V2_PACKAGE + ".");
    }
}
