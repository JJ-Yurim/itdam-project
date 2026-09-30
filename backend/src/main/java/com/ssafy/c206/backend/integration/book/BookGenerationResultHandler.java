package com.ssafy.c206.backend.integration.book;

/** Application boundary reserved for a future Result Queue/Callback adapter. */
public interface BookGenerationResultHandler {
	void handle(BookGenerationResult result);
}
