package com.ssafy.c206.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

	private final RequestIdFilter filter = new RequestIdFilter();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void preservesAValidUuidInTheMdcAndResponse() throws Exception {
		UUID supplied = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestId.HEADER, supplied.toString());
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<String> duringChain = new AtomicReference<>();

		filter.doFilter(request, response,
				(req, res) -> duringChain.set(MDC.get(RequestId.MDC_KEY)));

		assertThat(duringChain).hasValue(supplied.toString());
		assertThat(response.getHeader(RequestId.HEADER)).isEqualTo(supplied.toString());
		assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
	}

	@Test
	void replacesMissingAndMalformedRequestIds() throws Exception {
		for (String candidate : new String[] {null, "not-a-uuid"}) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			if (candidate != null) {
				request.addHeader(RequestId.HEADER, candidate);
			}
			MockHttpServletResponse response = new MockHttpServletResponse();
			AtomicReference<String> duringChain = new AtomicReference<>();

			filter.doFilter(request, response,
					(req, res) -> duringChain.set(MDC.get(RequestId.MDC_KEY)));

			UUID generated = UUID.fromString(duringChain.get());
			assertThat(response.getHeader(RequestId.HEADER)).isEqualTo(generated.toString());
			assertThat(response.getHeader(RequestId.HEADER)).isNotEqualTo(candidate);
			assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
		}
	}

	@Test
	void clearsTheMdcWhenTheFilterChainThrows() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
			assertThat(MDC.get(RequestId.MDC_KEY)).isNotBlank();
			throw new ServletException("downstream failure");
		})).isInstanceOf(ServletException.class);

		assertThat(MDC.get(RequestId.MDC_KEY)).isNull();
	}
}
