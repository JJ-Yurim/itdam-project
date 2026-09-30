package com.ssafy.c206.backend.integration.book;

public record BookGenerationRequest(Long jobId, Long bossRunId, Long bookId) { }
