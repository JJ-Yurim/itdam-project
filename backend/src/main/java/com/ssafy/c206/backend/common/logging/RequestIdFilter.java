package com.ssafy.c206.backend.common.logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		UUID requestId = RequestId.parseOrCreate(request.getHeader(RequestId.HEADER));
		String value = requestId.toString();
		MDC.put(RequestId.MDC_KEY, value);
		response.setHeader(RequestId.HEADER, value);

		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove(RequestId.MDC_KEY);
		}
	}
}
