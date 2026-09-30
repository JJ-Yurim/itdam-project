package com.ssafy.c206.backend.sentence.evaluation.service;

import java.time.Instant;

/** 제출 후보 집합이 바뀌었는지 판별하는 가벼운 버전 값. */
public record EligibleSentenceRevision(long count, Instant latestEvaluatedAt) { }
