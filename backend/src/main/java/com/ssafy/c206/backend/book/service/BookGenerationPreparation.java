package com.ssafy.c206.backend.book.service;

import com.ssafy.c206.backend.integration.book.BookGenerationRequest;

public record BookGenerationPreparation(BookGenerationRequest request, boolean requestRequired) { }
