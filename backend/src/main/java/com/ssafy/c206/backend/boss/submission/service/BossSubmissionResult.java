package com.ssafy.c206.backend.boss.submission.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;

public record BossSubmissionResult(Long submissionId, Long bossRunId, Long bossPhaseId, LocalDate serviceDate,
		BigDecimal aiBaseScore, BigDecimal wordBonus, BigDecimal moodBonus, BigDecimal weaknessBonus,
		BigDecimal phaseModifier, long finalDamage, int bossPhaseNo, long remainingHp, BossPhaseStatus phaseStatus,
		BossRunStatus bossRunStatus) {
	static BossSubmissionResult from(BossSubmission submission, BossPhase phase, BossRun run, DamageCalculation damage,
			LocalDate serviceDate) {
		return new BossSubmissionResult(submission.getId(), run.getId(), phase.getId(), serviceDate, damage.baseScore(),
				damage.wordBonus(), damage.moodBonus(), damage.weaknessBonus(), damage.phaseModifier(),
				damage.finalDamage(), phase.getPhaseNo(), phase.getRemainingHp(), phase.getStatus(), run.getStatus());
	}
}
