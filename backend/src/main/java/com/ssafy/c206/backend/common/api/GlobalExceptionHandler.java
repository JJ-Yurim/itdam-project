package com.ssafy.c206.backend.common.api;

import java.util.Comparator;
import java.util.List;

import com.ssafy.c206.backend.common.logging.ExceptionSource;
import com.ssafy.c206.backend.common.time.ServiceClock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

	private final ServiceClock serviceClock;

	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ApiError> handleDomain(DomainException exception, HttpServletRequest request) {
		logDomain(exception, request);
		return response(exception.getErrorCode(), exception.getMessage(), List.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		logExpected(ErrorCode.VALIDATION_ERROR, exception, ErrorCode.VALIDATION_ERROR.defaultMessage(), request);
		List<ApiError.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
				.sorted(Comparator.comparing(ApiError.FieldError::field))
				.toList();
		return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fieldErrors);
	}

	// 컨트롤러 파라미터의 @Min/@Max 등 제약 위반은 Spring 내장 메서드 검증이 이 예외로 던진다.
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiError> handleHandlerMethodValidation(HandlerMethodValidationException exception,
			HttpServletRequest request) {
		logExpected(ErrorCode.VALIDATION_ERROR, exception, ErrorCode.VALIDATION_ERROR.defaultMessage(), request);
		List<ApiError.FieldError> fieldErrors = exception.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new ApiError.FieldError(
								String.valueOf(result.getMethodParameter().getParameterName()),
								error.getDefaultMessage())))
				.sorted(Comparator.comparing(ApiError.FieldError::field))
				.toList();
		return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fieldErrors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception,
			HttpServletRequest request) {
		logExpected(ErrorCode.VALIDATION_ERROR, exception, ErrorCode.VALIDATION_ERROR.defaultMessage(), request);
		List<ApiError.FieldError> fieldErrors = exception.getConstraintViolations().stream()
				.map(violation -> new ApiError.FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
				.sorted(Comparator.comparing(ApiError.FieldError::field))
				.toList();
		return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fieldErrors);
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
	public ResponseEntity<ApiError> handleMalformedRequest(Exception exception, HttpServletRequest request) {
		logExpected(ErrorCode.VALIDATION_ERROR, exception, ErrorCode.VALIDATION_ERROR.defaultMessage(), request);
		return response(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), List.of());
	}

	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<ApiError> handleMultipart(MultipartException exception, HttpServletRequest request) {
		String message = "The uploaded image is missing or too large.";
		logExpected(ErrorCode.VALIDATION_ERROR, exception, message, request);
		return response(ErrorCode.VALIDATION_ERROR, message, List.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {
		logExpected(ErrorCode.METHOD_NOT_ALLOWED, exception, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), request);
		return response(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), List.of());
	}

	// 매핑되지 않은 경로는 핸들러 부재(NoHandlerFound)와 정적 리소스 부재(NoResourceFound) 두 경로로 들어온다.
	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	public ResponseEntity<ApiError> handleNoHandler(Exception exception, HttpServletRequest request) {
		logExpected(ErrorCode.NOT_FOUND, exception, ErrorCode.NOT_FOUND.defaultMessage(), request);
		return response(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), List.of());
	}

	// 시큐리티 예외가 최광범위 Exception 핸들러에 잡혀 500으로 변환되지 않도록 상태 코드를 보존한다.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
		logExpected(ErrorCode.FORBIDDEN, exception, ErrorCode.FORBIDDEN.defaultMessage(), request);
		return response(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage(), List.of());
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
		logExpected(ErrorCode.UNAUTHORIZED, exception, ErrorCode.UNAUTHORIZED.defaultMessage(), request);
		return response(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), List.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
		logInternal(ErrorCode.INTERNAL_ERROR, exception, ErrorCode.INTERNAL_ERROR.defaultMessage(), request);
		return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of());
	}

	private void logExpected(ErrorCode errorCode, Exception exception, String errorMessage,
			HttpServletRequest request) {
		log.atWarn()
				.addKeyValue("error_code", errorCode.name())
				.addKeyValue("exception_type", exception.getClass().getName())
				.addKeyValue("error_message", errorMessage)
				.addKeyValue("http_method", request.getMethod())
				.addKeyValue("request_path", request.getRequestURI())
				.log("Expected API exception");
	}

	private void logDomain(DomainException exception, HttpServletRequest request) {
		ErrorCode errorCode = exception.getErrorCode();
		if (errorCode == ErrorCode.INTERNAL_ERROR) {
			logInternal(errorCode, exception, exception.getMessage(), request);
			return;
		}

		logExpected(errorCode, exception, exception.getMessage(), request);
	}

	private void logInternal(ErrorCode errorCode, Exception exception, String errorMessage,
			HttpServletRequest request) {
		ExceptionSource source = ExceptionSource.from(exception);
		log.atError()
				.addKeyValue("error_code", errorCode.name())
				.addKeyValue("exception_type", exception.getClass().getName())
				.addKeyValue("root_cause_type", source.exceptionType())
				.addKeyValue("source_file", source.sourceFile())
				.addKeyValue("source_line", source.sourceLine())
				.addKeyValue("error_message", errorMessage)
				.addKeyValue("http_method", request.getMethod())
				.addKeyValue("request_path", request.getRequestURI())
				.setCause(exception)
				.log("Internal API exception");
	}

	private ResponseEntity<ApiError> response(ErrorCode errorCode, String message,
			List<ApiError.FieldError> fieldErrors) {
		ApiError body = new ApiError(serviceClock.now(), errorCode.name(), message, fieldErrors);
		return ResponseEntity.status(errorCode.status()).body(body);
	}
}
