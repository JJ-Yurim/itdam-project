package com.ssafy.c206.backend.common.logging;

import java.util.UUID;

import org.slf4j.MDC;

public final class RequestId {

	public static final String HEADER = "X-Request-ID";
	public static final String MDC_KEY = "request_id";

	private RequestId() {
	}

	public static UUID parseOrCreate(String candidate) {
		if (candidate != null) {
			try {
				return UUID.fromString(candidate);
			}
			catch (IllegalArgumentException ignored) {
				// An untrusted or malformed header is replaced with a server-generated ID.
			}
		}
		return UUID.randomUUID();
	}

	public static UUID currentOrCreate() {
		return parseOrCreate(MDC.get(MDC_KEY));
	}
}
