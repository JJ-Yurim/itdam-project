package com.ssafy.c206.backend.common.logging;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

public record ExceptionSource(String exceptionType, String sourceFile, Integer sourceLine) {

	private static final String APPLICATION_PACKAGE_PREFIX = "com.ssafy.c206.";

	public static ExceptionSource from(Throwable throwable) {
		Throwable root = deepestCause(Objects.requireNonNull(throwable, "throwable"));

		for (StackTraceElement frame : root.getStackTrace()) {
			if (frame.getClassName().startsWith(APPLICATION_PACKAGE_PREFIX)) {
				Integer line = frame.getLineNumber() > 0 ? frame.getLineNumber() : null;
				return new ExceptionSource(root.getClass().getName(), frame.getFileName(), line);
			}
		}

		return new ExceptionSource(root.getClass().getName(), null, null);
	}

	private static Throwable deepestCause(Throwable throwable) {
		Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Throwable current = throwable;

		while (visited.add(current)) {
			Throwable cause = current.getCause();
			if (cause == null || visited.contains(cause)) {
				break;
			}
			current = cause;
		}

		return current;
	}
}
