package com.ssafy.c206.backend.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.domain.BossStatus;
import com.ssafy.c206.backend.boss.domain.MoodRole;
import com.ssafy.c206.backend.boss.repository.BossMoodRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.sentence.evaluation.domain.MoodCategory;
import com.ssafy.c206.backend.sentence.evaluation.repository.MoodCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossPersistenceTest {

	private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

	@Autowired private BossFixture bossFixture;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossMoodRepository bossMoodRepository;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private MoodCategoryRepository moodCategoryRepository;

	@Test
	void localTestFixtureCreatesActiveRunWithThreeSequentialPhases() {
		BossRun run = bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);

		assertThat(run.getStatus()).isEqualTo(BossRunStatus.ACTIVE);
		assertThat(run.getCurrentPhaseNo()).isOne();
		assertThat(bossRepository.count()).isOne();
		assertThat(bossMoodRepository.count()).isEqualTo(2);
		assertThat(bossMoodRepository.findAll()).extracting(mood -> mood.getMoodRole())
				.containsExactlyInAnyOrder(MoodRole.THEME, MoodRole.WEAKNESS);

		List<com.ssafy.c206.backend.boss.domain.BossPhase> phases = bossPhaseRepository.findAll().stream()
				.sorted(Comparator.comparingInt(com.ssafy.c206.backend.boss.domain.BossPhase::getPhaseNo)).toList();
		assertThat(phases).extracting(phase -> phase.getPhaseNo()).containsExactly(1, 2, 3);
		assertThat(phases).extracting(phase -> phase.getMaxHp()).containsExactly(500L, 750L, 1000L);
		assertThat(phases).extracting(phase -> phase.getRemainingHp()).containsExactly(500L, 750L, 1000L);
		assertThat(phases).extracting(phase -> phase.getStatus()).containsExactly(
				BossPhaseStatus.ACTIVE, BossPhaseStatus.WAITING, BossPhaseStatus.WAITING);
		assertThat(phases).filteredOn(phase -> phase.getStatus() == BossPhaseStatus.ACTIVE).hasSize(1);
	}

	@Test
	void bossMoodReferencesSharedMoodCategoryTaxonomy() {
		BossRun run = bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);

		MoodCategory joy = moodCategoryRepository.findByCode("JOY").orElseThrow();
		assertThat(bossMoodRepository.findAll()).anySatisfy(mood -> {
			assertThat(mood.getBoss().getId()).isEqualTo(run.getBoss().getId());
			assertThat(mood.getMoodCategory().getId()).isEqualTo(joy.getId());
			assertThat(mood.getMoodCategory().getCode()).isEqualTo("JOY");
		});
		assertThat(moodCategoryRepository.count()).isEqualTo(10);
	}

	@Test
	void validatesPositivePhaseAndMoodValues() {
		Boss boss = Boss.create("Boss", "description", NOW);
		BossRun run = BossRun.schedule(boss, NOW, NOW.plusSeconds(60), NOW);
		MoodCategory joy = moodCategoryRepository.findByCode("JOY").orElseThrow();

		assertThatThrownBy(() -> run.addPhase(0, null, null, BigDecimal.ONE))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> run.addPhase(500, null, null, BigDecimal.ZERO))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> com.ssafy.c206.backend.boss.domain.BossMood.of(
					boss, joy, MoodRole.THEME, BigDecimal.ZERO))
				.isInstanceOf(DomainException.class);
	}

	@Test
	void runTransitionsAreExplicitAndOnlyAllowValidTerminalClose() {
		Boss boss = Boss.create("Boss", "description", NOW);
		assertThat(boss.getStatus()).isEqualTo(BossStatus.ACTIVE);
		BossRun cleared = BossRun.schedule(boss, NOW, NOW.plusSeconds(60), NOW);
		cleared.addPhase(500, null, null, BigDecimal.ONE);
		cleared.activate(NOW);
		cleared.markCleared(NOW.plusSeconds(10));
		cleared.close(NOW.plusSeconds(20));
		assertThat(cleared.getStatus()).isEqualTo(BossRunStatus.CLOSED);
		assertThat(cleared.getClearedAt()).isEqualTo(NOW.plusSeconds(10));

		BossRun expired = BossRun.schedule(boss, NOW, NOW.plusSeconds(60), NOW);
		expired.addPhase(500, null, null, BigDecimal.ONE);
		expired.activate(NOW);
		expired.markEndedUncleared(NOW.plusSeconds(60));
		assertThat(expired.getStatus()).isEqualTo(BossRunStatus.ENDED_UNCLEARED);
		assertThatThrownBy(() -> expired.markCleared(NOW.plusSeconds(61))).isInstanceOf(DomainException.class);
	}
}
