package com.ssafy.c206.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExceptionSourceTest {

	@Test
	void extractsTheDeepestApplicationExceptionTypeAndSourceLocation() {
		ExceptionSource source = ExceptionSource.from(wrappedApplicationException());

		assertThat(source.exceptionType()).isEqualTo(IllegalStateException.class.getName());
		assertThat(source.sourceFile()).isEqualTo("ExceptionSourceTest.java");
		assertThat(source.sourceLine()).isPositive();
	}

	@Test
	void leavesLocationEmptyWhenTheDeepestCauseHasNoApplicationFrame() {
		IllegalStateException root = new IllegalStateException("library-only failure");
		root.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("java.lang.Thread", "run", "Thread.java", 840)
		});

		ExceptionSource source = ExceptionSource.from(new RuntimeException("wrapper", root));

		assertThat(source.exceptionType()).isEqualTo(IllegalStateException.class.getName());
		assertThat(source.sourceFile()).isNull();
		assertThat(source.sourceLine()).isNull();
	}

	private RuntimeException wrappedApplicationException() {
		try {
			throw new IllegalStateException("root failure");
		}
		catch (IllegalStateException exception) {
			return new RuntimeException("wrapper", exception);
		}
	}
}
