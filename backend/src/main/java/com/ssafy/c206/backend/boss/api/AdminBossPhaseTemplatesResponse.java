package com.ssafy.c206.backend.boss.api;

import java.util.List;

public record AdminBossPhaseTemplatesResponse(Long bossId, String bossName,
		List<BossPhaseTemplateResponse> phases) {
}
