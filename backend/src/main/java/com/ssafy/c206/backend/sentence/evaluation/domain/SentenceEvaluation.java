package com.ssafy.c206.backend.sentence.evaluation.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sentence_evaluations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceEvaluation {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sentence_version_id", nullable = false, unique = true) private SentenceVersion sentenceVersion;
	@Enumerated(EnumType.STRING) @Column(name = "safety_status", nullable = false, length = 30) private EvaluationSafetyStatus safetyStatus;
	@Column(name = "safety_reason", columnDefinition = "text") private String safetyReason;
	@Column(name = "completeness_score") private Integer completenessScore;
	@Column(name = "originality_score") private Integer originalityScore;
	@Column(name = "expression_score") private Integer expressionScore;
	@Column(name = "imagery_score") private Integer imageryScore;
	@Column(name = "emotional_impact_score") private Integer emotionalImpactScore;
	@Column(name = "base_score", precision = 6, scale = 2) private BigDecimal baseScore;
	@Column(name = "word_bonus", nullable = false, precision = 6, scale = 2) private BigDecimal wordBonus;
	@Column(name = "game_base_attack", nullable = false, precision = 6, scale = 2) private BigDecimal gameBaseAttack;
	@Column(name = "overall_feedback", columnDefinition = "text") private String overallFeedback;
	@Column(name = "model_name", length = 100) private String modelName;
	@Column(name = "prompt_version", length = 50) private String promptVersion;
	@Column(name = "rubric_version", length = 50) private String rubricVersion;
	@Column(name = "evaluated_at", nullable = false) private Instant evaluatedAt;

	private SentenceEvaluation(SentenceVersion version, Integer completeness, Integer originality, Integer expression,
			Integer imagery, Integer emotionalImpact, BigDecimal baseScore, BigDecimal wordBonus, BigDecimal gameBaseAttack,
			String feedback, String modelName, String promptVersion, String rubricVersion, Instant evaluatedAt) {
		this.sentenceVersion = version;
		this.safetyStatus = EvaluationSafetyStatus.PASSED;
		this.completenessScore = completeness;
		this.originalityScore = originality;
		this.expressionScore = expression;
		this.imageryScore = imagery;
		this.emotionalImpactScore = emotionalImpact;
		this.baseScore = baseScore;
		this.wordBonus = wordBonus;
		this.gameBaseAttack = gameBaseAttack;
		this.overallFeedback = feedback;
		this.modelName = modelName;
		this.promptVersion = promptVersion;
		this.rubricVersion = rubricVersion;
		this.evaluatedAt = evaluatedAt;
	}

	public static SentenceEvaluation passed(SentenceVersion version, Integer completeness, Integer originality,
			Integer expression, Integer imagery, Integer emotionalImpact, BigDecimal baseScore, BigDecimal wordBonus,
			BigDecimal gameBaseAttack, String feedback, String modelName, String promptVersion, String rubricVersion,
			Instant evaluatedAt) {
		return new SentenceEvaluation(version, completeness, originality, expression, imagery, emotionalImpact,
				baseScore, wordBonus, gameBaseAttack, feedback, modelName, promptVersion, rubricVersion, evaluatedAt);
	}
}
