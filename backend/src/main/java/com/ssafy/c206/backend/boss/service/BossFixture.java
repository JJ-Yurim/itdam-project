package com.ssafy.c206.backend.boss.service;

import com.ssafy.c206.backend.sentence.evaluation.domain.MoodCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossMood;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.MoodRole;
import com.ssafy.c206.backend.boss.repository.BossMoodRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.evaluation.repository.MoodCategoryRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A non-bootstrapping fixture factory available only to local and test profiles. */
@Service
@Profile({"local", "test", "postgres-test"})
@RequiredArgsConstructor
public class BossFixture {

	private final BossRepository bossRepository;
	private final BossMoodRepository bossMoodRepository;
	private final BossPhaseRepository bossPhaseRepository;
	private final BossRunRepository bossRunRepository;
	private final MoodCategoryRepository moodCategoryRepository;
	private final BossSubmissionRepository bossSubmissionRepository;
	private final SentenceRepository sentenceRepository;
	private final SentenceVersionRepository sentenceVersionRepository;
	private final UserRepository userRepository;

	@Transactional
	public BossRun createThreePhaseRun(Instant startsAt, Instant endsAt, Instant now) {
		BossRun run = createScheduledThreePhaseRun(startsAt, endsAt, now);
		run.activate(now);
		bossPhaseRepository.saveAll(run.getPhases());
		return run;
	}

	/** 지연 활성화 검증처럼 시작 시각 도달 전 상태가 그대로 필요한 곳을 위해 활성화하지 않고 돌려준다. */
	@Transactional
	public BossRun createScheduledThreePhaseRun(Instant startsAt, Instant endsAt, Instant now) {
		Boss boss = bossRepository.save(Boss.create("Fixture Boss", "Local/test only boss fixture.", now));
		bossMoodRepository.save(BossMood.of(boss, mood("JOY"), MoodRole.THEME, BigDecimal.ONE));
		bossMoodRepository.save(BossMood.of(boss, mood("MYSTERY"), MoodRole.WEAKNESS, BigDecimal.ONE));
		return scheduleThreePhaseRunFor(boss, startsAt, endsAt, now);
	}

	/** 보스당 한 회차만 ACTIVE일 수 있다는 규칙을 검증하려면 같은 보스에 회차를 여러 개 걸어야 한다. */
	@Transactional
	public BossRun scheduleThreePhaseRunFor(Boss boss, Instant startsAt, Instant endsAt, Instant now) {
		BossRun run = bossRunRepository.save(BossRun.schedule(boss, startsAt, endsAt, now));
		run.addPhase(500, new BigDecimal("40.00"), new BigDecimal("60.00"), BigDecimal.ONE);
		run.addPhase(750, new BigDecimal("50.00"), new BigDecimal("70.00"), BigDecimal.ONE);
		run.addPhase(1000, new BigDecimal("60.00"), new BigDecimal("80.00"), BigDecimal.ONE);
		bossPhaseRepository.saveAll(run.getPhases());
		return run;
	}

	/** 평가를 통과한 문장을 만들어 보스런에 제출한다. 조회 응답 검증용 픽스처다. */
	@Transactional
	public BossSubmission submitEvaluatedSentence(BossRun run, Long userId, String content) {
		Instant now = Instant.now();
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Fixture user is missing: " + userId));
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = sentenceVersionRepository.save(SentenceVersion.draft(sentence, 1, content, now));
		BossPhase phase = bossPhaseRepository.findDetailsByBossRunIdIn(java.util.List.of(run.getId())).getFirst();
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		return bossSubmissionRepository.save(
				BossSubmission.create(run, phase, user, version, now.atZone(ZoneOffset.UTC).toLocalDate(), damage, now));
	}

	private MoodCategory mood(String code) {
		return moodCategoryRepository.findByCode(code)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Fixture mood category is missing: " + code));
	}
}
