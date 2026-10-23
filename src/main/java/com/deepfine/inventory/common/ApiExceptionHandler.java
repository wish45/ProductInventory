package com.deepfine.inventory.common;

import com.deepfine.inventory.domain.InsufficientStockException;
import com.deepfine.inventory.domain.ProductNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러 응답을 RFC 9457 ProblemDetail + code로 통일한다.
 * Spring MVC 표준 예외(검증 실패, 본문 형식 오류 등)는 부모 클래스가 처리하고 여기서 code만 덧붙인다.
 * 예상하지 못한 예외도 같은 형식의 500으로 나간다. 호출하는 쪽이 프로그램(디바이스, 연동 서버)이라 모든 실패를 code로 분기할 수 있어야 한다.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String INVALID_REQUEST_DETAIL = "요청 값이 올바르지 않습니다.";

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setProperty("code", ErrorCode.PRODUCT_NOT_FOUND);
        problem.setProperty("productId", ex.getProductId());
        return problem;
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", ErrorCode.INSUFFICIENT_STOCK);
        problem.setProperty("productId", ex.getProductId());
        problem.setProperty("requested", ex.getRequested());
        problem.setProperty("available", ex.getAvailable());
        return problem;
    }

    /** 그 밖의 모든 예외. 부모가 처리하는 Spring MVC 예외와 위의 도메인 예외가 먼저 매칭되고, 남는 것만 여기로 온다. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "요청을 처리하지 못했습니다.");
        problem.setProperty("code", ErrorCode.INTERNAL_ERROR);
        return problem;
    }

    /**
     * 메서드 검증 실패. 이 프로젝트의 컨트롤러는 헤더와 쿼리 파라미터에 제약이 있어 Spring MVC가 메서드 검증을 적용하므로,
     * {@code @Valid} 본문 오류도 MethodArgumentNotValidException이 아니라 이 예외로 온다.
     * 본문 필드는 필드명으로, 헤더와 쿼리 파라미터는 클라이언트가 보낸 이름으로 어떤 값이 왜 틀렸는지 돌려준다.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bean) {
                bean.getFieldErrors().forEach(field -> errors.add(error(field.getField(), field.getDefaultMessage())));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(e -> errors.add(error(name, e.getDefaultMessage())));
            }
        }
        ProblemDetail problem = ex.getBody();
        problem.setDetail(INVALID_REQUEST_DETAIL);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** 본문만 검증되는 경우(메서드 파라미터에 제약이 없는 컨트롤러)의 검증 실패. 형식은 위와 같다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setDetail(INVALID_REQUEST_DETAIL);
        problem.setProperty("errors", ex.getFieldErrors().stream()
                .map(field -> error(field.getField(), field.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** 부모가 만든 400 응답에 code를 붙인다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (statusCode.equals(HttpStatus.BAD_REQUEST) && response.getBody() instanceof ProblemDetail problem) {
            problem.setProperty("code", ErrorCode.INVALID_REQUEST);
        }
        return response;
    }

    private static Map<String, String> error(String field, @Nullable String message) {
        return Map.of("field", field, "message", String.valueOf(message));
    }

    /** 헤더와 쿼리 파라미터는 클라이언트가 보낸 이름으로 알려준다. 이름을 명시하지 않았으면 자바 파라미터 이름이다. */
    private static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) {
            return header.value();
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.value().isEmpty()) {
            return param.value();
        }
        String name = parameter.getParameterName();
        return name != null ? name : "parameter" + parameter.getParameterIndex();
    }
}
