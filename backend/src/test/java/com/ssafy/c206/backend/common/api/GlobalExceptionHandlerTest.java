package com.ssafy.c206.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ssafy.c206.backend.common.time.ServiceClock;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
			new ServiceClock(Clock.systemUTC(), ZoneId.of("Asia/Seoul")));

	@Test
	void mapsDomainStatusesToStableCodes() {
		MockHttpServletRequest request = request("GET", "/api/test");

		assertThat(handler.handleDomain(new DomainException(ErrorCode.NOT_FOUND), request).getStatusCode().value())
				.isEqualTo(404);
		assertThat(handler.handleDomain(new DomainException(ErrorCode.CONFLICT), request).getBody().code())
				.isEqualTo("CONFLICT");
		assertThat(handler.handleDomain(new DomainException(ErrorCode.FORBIDDEN), request).getStatusCode().value())
				.isEqualTo(403);
	}

	@Test
	void mapsValidationFieldErrorsWithoutInternalDetails() throws Exception {
		ApiError body = handler.handleMethodArgumentNotValid(
				invalidMethodArgument(), request("POST", "/api/users")).getBody();

		assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
		assertThat(body.fieldErrors()).containsExactly(new ApiError.FieldError("email", "must be a valid email"));
		assertThat(body.message()).doesNotContain("MethodArgumentNotValidException");
	}

	@Test
	void logsUnexpectedExceptionsWithSearchableSourceFieldsAndCause() {
		IOException cause = new IOException("disk unavailable");
		ILoggingEvent event = captureLog(() ->
				handler.handleUnexpected(new IllegalStateException("diagnostic failure", cause),
						request("POST", "/api/books")));
		Map<String, String> fields = keyValues(event);

		assertThat(fields).containsEntry("error_code", "INTERNAL_ERROR")
				.containsEntry("exception_type", IllegalStateException.class.getName())
				.containsEntry("root_cause_type", IOException.class.getName())
				.containsEntry("source_file", "GlobalExceptionHandlerTest.java")
				.containsEntry("error_message", ErrorCode.INTERNAL_ERROR.defaultMessage())
				.containsEntry("http_method", "POST")
				.containsEntry("request_path", "/api/books");
		assertThat(Integer.parseInt(fields.get("source_line"))).isPositive();
		assertThat(event.getThrowableProxy()).isNotNull();
	}

	@Test
	void logsFrameworkClientExceptionsWithSharedRequestContext() throws Exception {
		MethodArgumentNotValidException exception = invalidMethodArgument();
		ILoggingEvent event = captureLog(() -> handler.handleMethodArgumentNotValid(
				exception, request("POST", "/api/users")));

		assertThat(keyValues(event))
				.containsEntry("error_code", "VALIDATION_ERROR")
				.containsEntry("exception_type", MethodArgumentNotValidException.class.getName())
				.containsEntry("error_message", ErrorCode.VALIDATION_ERROR.defaultMessage())
				.containsEntry("http_method", "POST")
				.containsEntry("request_path", "/api/users");
		assertThat(event.getThrowableProxy()).isNull();
		assertThat(event.getLevel()).isEqualTo(Level.WARN);
	}

	@Test
	void logsExpectedDomainExceptionsWithRequestContextWithoutAStackTrace() {
		MDC.put("request_id", "4c534f3d-e54c-457b-8a5d-56222fbca7b2");
		ILoggingEvent event;
		try {
			event = captureLog(() -> handler.handleDomain(
					new DomainException(ErrorCode.NOT_FOUND, "Book not found."),
					request("GET", "/api/books/42")));
		}
		finally {
			MDC.remove("request_id");
		}

		assertThat(keyValues(event))
				.containsEntry("error_code", "NOT_FOUND")
				.containsEntry("exception_type", DomainException.class.getName())
				.containsEntry("error_message", "Book not found.")
				.containsEntry("http_method", "GET")
				.containsEntry("request_path", "/api/books/42");
		assertThat(event.getMDCPropertyMap())
				.containsEntry("request_id", "4c534f3d-e54c-457b-8a5d-56222fbca7b2");
		assertThat(event.getThrowableProxy()).isNull();
		assertThat(event.getLevel()).isEqualTo(Level.WARN);
	}

	@Test
	void logsInternalDomainExceptionsWithRootCauseAndSource() {
		IOException cause = new IOException("disk unavailable");
		ILoggingEvent event = captureLog(() -> handler.handleDomain(
				new DomainException(ErrorCode.INTERNAL_ERROR, "Image file could not be stored.", cause),
				request("POST", "/api/boss-images")));
		Map<String, String> fields = keyValues(event);

		assertThat(fields)
				.containsEntry("error_code", "INTERNAL_ERROR")
				.containsEntry("exception_type", DomainException.class.getName())
				.containsEntry("root_cause_type", IOException.class.getName())
				.containsEntry("source_file", "GlobalExceptionHandlerTest.java")
				.containsEntry("error_message", "Image file could not be stored.")
				.containsEntry("http_method", "POST")
				.containsEntry("request_path", "/api/boss-images");
		assertThat(Integer.parseInt(fields.get("source_line"))).isPositive();
		assertThat(event.getThrowableProxy()).isNotNull();
		assertThat(event.getLevel()).isEqualTo(Level.ERROR);
	}

	private MockHttpServletRequest request(String method, String path) {
		return new MockHttpServletRequest(method, path);
	}

	private MethodArgumentNotValidException invalidMethodArgument() throws Exception {
		MethodParameter parameter = new MethodParameter(
				ValidationFixture.class.getDeclaredMethod("submit", String.class), 0);
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(new FieldError("request", "email", "must be a valid email"));
		return new MethodArgumentNotValidException(parameter, bindingResult);
	}

	private ILoggingEvent captureLog(Runnable action) {
		Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			action.run();
			assertThat(appender.list).hasSize(1);
			return appender.list.getFirst();
		}
		finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	private Map<String, String> keyValues(ILoggingEvent event) {
		if (event.getKeyValuePairs() == null) {
			return Map.of();
		}
		return event.getKeyValuePairs().stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
	}

	private static final class ValidationFixture {
		@SuppressWarnings("unused")
		void submit(String email) {
		}
	}
}
