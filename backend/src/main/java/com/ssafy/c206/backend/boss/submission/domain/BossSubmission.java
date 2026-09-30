package com.ssafy.c206.backend.boss.submission.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boss_submissions", uniqueConstraints = @UniqueConstraint(
		name = "uk_boss_submission_run_version", columnNames = {"boss_run_id", "sentence_version_id"}), indexes = {
			@Index(name = "idx_boss_submission_run_user_date", columnList = "boss_run_id,user_id,service_date"),
			@Index(name = "idx_boss_submission_phase", columnList = "boss_phase_id"),
			@Index(name = "idx_boss_submission_user_created_at", columnList = "user_id,boss_run_id,created_at DESC")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BossSubmission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "boss_run_id", nullable = false) private BossRun bossRun;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "boss_phase_id", nullable = false) private BossPhase bossPhase;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sentence_version_id", nullable = false) private SentenceVersion sentenceVersion;
	@Column(name = "service_date", nullable = false) private LocalDate serviceDate;
	@Column(name = "ai_base_score_snapshot", nullable = false, precision = 6, scale = 2) private BigDecimal aiBaseScoreSnapshot;
	@Column(name = "word_bonus_snapshot", nullable = false, precision = 6, scale = 2) private BigDecimal wordBonusSnapshot;
	@Column(name = "mood_bonus_snapshot", nullable = false, precision = 6, scale = 2) private BigDecimal moodBonusSnapshot;
	@Column(name = "weakness_bonus_snapshot", nullable = false, precision = 6, scale = 2) private BigDecimal weaknessBonusSnapshot;
	@Column(name = "phase_modifier_snapshot", nullable = false, precision = 6, scale = 3) private BigDecimal phaseModifierSnapshot;
	@Column(name = "final_damage", nullable = false) private long finalDamage;
	@Column(name = "created_at", nullable = false) private Instant createdAt;

	private BossSubmission(BossRun run, BossPhase phase, User user, SentenceVersion version, LocalDate serviceDate,
			DamageCalculation damage, Instant now) {
		this.bossRun = run; this.bossPhase = phase; this.user = user; this.sentenceVersion = version;
		this.serviceDate = serviceDate;
		this.aiBaseScoreSnapshot = damage.baseScore(); this.wordBonusSnapshot = damage.wordBonus();
		this.moodBonusSnapshot = damage.moodBonus(); this.weaknessBonusSnapshot = damage.weaknessBonus();
		this.phaseModifierSnapshot = damage.phaseModifier(); this.finalDamage = damage.finalDamage(); this.createdAt = now;
	}

	public static BossSubmission create(BossRun run, BossPhase phase, User user, SentenceVersion version,
			LocalDate serviceDate, DamageCalculation damage, Instant now) {
		return new BossSubmission(run, phase, user, version, serviceDate, damage, now);
	}
}
