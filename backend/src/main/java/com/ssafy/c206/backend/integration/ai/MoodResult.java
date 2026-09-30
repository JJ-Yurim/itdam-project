package com.ssafy.c206.backend.integration.ai;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// AI Service의 선택적 필드 추가가 평가 파싱을 깨지 않도록 한다(AiEvaluationResult 참고).
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoodResult(String categoryCode, BigDecimal strength, int rank) {
}
