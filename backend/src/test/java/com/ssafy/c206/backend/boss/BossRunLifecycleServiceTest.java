package com.ssafy.c206.backend.boss;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossPhaseStatus;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.service.BossRunLifecycleService;
import com.ssafy.c206.backend.common.time.ServiceClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 지연 전이의 의미를 고정한다. 시간이 흘렀다고 상태가 바뀌는 것이 아니라 조회 시점에 맞춰지므로,
 * "언제 바뀌는가"가 아니라 "동기화를 돌리면 어떤 상태가 되는가"를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(BossRunLifecycleServiceTest.FixedTimeConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BossRunLifecycleServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
	private static final Instant CREATED = NOW.minusSeconds(86_400);

	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunLifecycleService lifecycleService;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;

	@Test
	void activatesScheduledRunAtExactStartBoundaryTogetherWithItsFirstPhase() {
		// 시작 시각과 현재가 같은 순간도 "시작했다"로 본다. 만료가 endsAt <= now로 경계를 포함하는
		// 것과 맞춘다. 회차만 ACTIVE로 올리고 페이즈를 두고 가면 제출 데미지가 들어가지 않는다.
		BossRun run = bossFixture.createScheduledThreePhaseRun(NOW, NOW.plusSeconds(3_600), CREATED);

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ACTIVE);
		assertThat(bossPhaseRepository.findByBossRunIdOrderByPhaseNoAsc(run.getId()))
				.extracting(phase -> phase.getStatus())
				.containsExactly(BossPhaseStatus.ACTIVE, BossPhaseStatus.WAITING, BossPhaseStatus.WAITING);
	}

	@Test
	void keepsStartedAtAtThePlannedTimeInsteadOfTheSyncTime() {
		// 예정 시각이 지난 뒤 아무도 조회하지 않다가 한참 만에 동기화되어도, 기록에 남는 시작
		// 시각은 관리자가 정한 시각이어야 한다. 조회 시각으로 덮으면 회차 길이가 달라진다.
		Instant plannedStart = NOW.minusSeconds(7_200);
		BossRun run = bossFixture.createScheduledThreePhaseRun(plannedStart, NOW.plusSeconds(3_600), CREATED);

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getStartedAt()).isEqualTo(plannedStart);
	}

	@Test
	void doesNotActivateAScheduledRunWhoseWindowAlreadyClosed() {
		// 트래픽이 없어 아무도 조회하지 않은 채 종료 시각까지 넘어간 회차다. 여기서 ACTIVE로
		// 올리면 만료는 이미 지나간 뒤라 같은 패스에서 정리되지 않고, 끝난 레이드가 진행 중으로
		// 응답에 실린다. 제출 가능 문장은 status만 보고 통과시키지만 실제 제출은 endsAt 검사에
		// 걸려 거부되므로, 사용자는 이유를 알 수 없는 실패를 겪는다.
		BossRun missed = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600),
				CREATED);

		lifecycleService.syncAllRunStates(NOW);
		lifecycleService.syncStateIfNeeded(missed.getId(), NOW);

		assertThat(bossRunRepository.findById(missed.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.SCHEDULED);
	}

	@Test
	void leavesScheduledRunAloneBeforeItsStartTime() {
		BossRun future = bossFixture.createScheduledThreePhaseRun(NOW.plusSeconds(60), NOW.plusSeconds(3_600), CREATED);

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(future.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.SCHEDULED);
	}

	@Test
	void leavesScheduledRunWithoutAStartTimeAlone() {
		// 시작 시각이 비어 있으면 예약이 아니라 "관리자가 직접 시작할 회차"라는 뜻이다.
		BossRun openEnded = bossFixture.createScheduledThreePhaseRun(null, NOW.plusSeconds(3_600), CREATED);

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(openEnded.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.SCHEDULED);
	}

	@Test
	void expiresTheEndedRunFirstSoTheSameBossNextRunCanTakeTheActiveSlot() {
		// 순서가 뒤집히면 다음 회차가 유일 제약에 막혀 예정으로 남고, 한 번 더 조회할 때까지
		// 아무 레이드도 진행되지 않는 공백이 생긴다.
		BossRun ending = bossFixture.createThreePhaseRun(NOW.minusSeconds(7_200), NOW.minusSeconds(60),
				NOW.minusSeconds(7_200));
		BossRun next = bossFixture.scheduleThreePhaseRunFor(ending.getBoss(), NOW.minusSeconds(30),
				NOW.plusSeconds(3_600), CREATED);

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(ending.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ENDED_UNCLEARED);
		assertThat(bossRunRepository.findById(next.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ACTIVE);
	}

	@Test
	void listSyncActivatesOnlyTheEarliestStartedRunWhenOneBossHasSeveralWaiting() {
		// 보스당 ACTIVE는 하나뿐이다. 둘 다 시작 시각이 지났다면 먼저 시작하기로 한 쪽이 진행되고,
		// 나머지는 예정으로 남아 앞선 회차가 끝난 뒤의 조회에서 활성화된다. 이 순서 보장은 목록
		// 경로 한정이다. 단건 경로는 요청된 회차를 올린다.
		BossRun earlier = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(600), NOW.plusSeconds(3_600),
				CREATED);
		BossRun later = bossFixture.scheduleThreePhaseRunFor(earlier.getBoss(), NOW.minusSeconds(60),
				NOW.plusSeconds(7_200), CREATED);

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(earlier.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ACTIVE);
		assertThat(bossRunRepository.findById(later.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.SCHEDULED);
	}

	@Test
	void singleRunSyncActivatesThatRunAndIsIdempotent() {
		BossRun run = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(60), NOW.plusSeconds(3_600), CREATED);

		lifecycleService.syncStateIfNeeded(run.getId(), NOW);
		Instant firstUpdatedAt = bossRunRepository.findById(run.getId()).orElseThrow().getUpdatedAt();
		lifecycleService.syncStateIfNeeded(run.getId(), NOW);
		BossRun synced = bossRunRepository.findById(run.getId()).orElseThrow();

		assertThat(synced.getStatus()).isEqualTo(BossRunStatus.ACTIVE);
		// 두 번째 호출이 updatedAt을 다시 올리면 관리자 화면의 낙관적 잠금이 매 조회마다 깨진다.
		assertThat(synced.getUpdatedAt()).isEqualTo(firstUpdatedAt);
	}

	@Test
	void guardReportsPendingWorkOnlyWhileATransitionIsActuallyDue() {
		// 전이 대상이 없는 대부분의 조회가 쓰기 트랜잭션을 열지 않게 막는 가드다.
		BossRun run = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(60), NOW.plusSeconds(3_600), CREATED);
		assertThat(bossRunRepository.isActivationDue(run.getId(), NOW)).isTrue();
		assertThat(bossRunRepository.existsPendingLifecycleTransition(NOW)).isTrue();

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.isActivationDue(run.getId(), NOW)).isFalse();
		assertThat(bossRunRepository.findActivatableRunIds(NOW, Pageable.unpaged())).doesNotContain(run.getId());
	}

	@Test
	void guardStaysQuietForAStartedRunThatCannotBeActivatedYet() {
		// 같은 보스에 진행 중 회차가 있으면 시작 시각이 지난 예정 회차가 있어도 지금 할 일은 없다.
		// 가드가 이걸 참으로 보면 앞선 회차가 도는 내내 모든 목록 요청이 쓰기 트랜잭션(두 번째
		// 커넥션)과 행 잠금을 헛되이 잡는다. 가드를 둔 이유가 사라진다.
		BossRun running = bossFixture.createThreePhaseRun(NOW.minusSeconds(600), NOW.plusSeconds(3_600),
				NOW.minusSeconds(600));
		BossRun waiting = bossFixture.scheduleThreePhaseRunFor(running.getBoss(), NOW.minusSeconds(60),
				NOW.plusSeconds(7_200), CREATED);

		assertThat(bossRunRepository.existsPendingLifecycleTransition(NOW)).isFalse();
		assertThat(bossRunRepository.isActivationDue(waiting.getId(), NOW)).isFalse();
		assertThat(bossRunRepository.findActivatableRunIds(NOW, Pageable.unpaged())).doesNotContain(waiting.getId());
	}

	@Test
	void guardStaysQuietForAStartedRunWithoutPhases() {
		// 페이즈 없는 회차는 activate()가 거부하므로 영원히 활성화되지 않는다. 가드가 참을 돌려주면
		// 그 회차가 정리될 때까지 무기한으로 헛도는 동기화가 매 조회마다 붙는다.
		BossRun empty = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(60), NOW.plusSeconds(3_600), CREATED);
		empty.replaceScheduledPhases(NOW.minusSeconds(30));
		bossRunRepository.saveAndFlush(empty);

		assertThat(bossRunRepository.existsPendingLifecycleTransition(NOW)).isFalse();

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(empty.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.SCHEDULED);
	}

	@Test
	void singleRunSyncActivatesTheRequestedRunEvenWhenAnEarlierOneIsWaiting() {
		// "가장 이른 회차 우선"은 목록 경로에서만 성립한다. 사용자가 연 회차를 두고 다른 회차를
		// 올리면 정작 그 화면은 여전히 예정으로 남아 더 혼란스럽기 때문이다.
		BossRun earlier = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(600), NOW.plusSeconds(3_600),
				CREATED);
		BossRun requested = bossFixture.scheduleThreePhaseRunFor(earlier.getBoss(), NOW.minusSeconds(60),
				NOW.plusSeconds(7_200), CREATED);

		lifecycleService.syncStateIfNeeded(requested.getId(), NOW);

		assertThat(bossRunRepository.findById(requested.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ACTIVE);
		assertThat(bossRunRepository.findById(earlier.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.SCHEDULED);
	}

	@Test
	void doesNotTouchCancelledRunsWhoseStartTimeHasPassed() {
		BossRun cancelled = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(600), NOW.plusSeconds(3_600),
				CREATED);
		cancelled.cancel(NOW.minusSeconds(300));
		bossRunRepository.saveAndFlush(cancelled);

		lifecycleService.syncAllRunStates(NOW);

		assertThat(bossRunRepository.findById(cancelled.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.CANCELLED);
	}

	@Test
	void activatesEveryStartedRunAcrossDifferentBossesInOnePass() {
		List<BossRun> runs = List.of(
				bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(600), NOW.plusSeconds(3_600), CREATED),
				bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(300), NOW.plusSeconds(3_600), CREATED),
				bossFixture.createScheduledThreePhaseRun(NOW, NOW.plusSeconds(3_600), CREATED));

		lifecycleService.syncAllRunStates(NOW);

		assertThat(runs).allSatisfy(run -> assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ACTIVE));
	}

	@TestConfiguration
	static class FixedTimeConfiguration {
		@Bean
		@Primary
		ServiceClock fixedServiceClock() {
			return new ServiceClock(Clock.fixed(NOW, ZoneId.of("UTC")), ZoneId.of("Asia/Seoul"));
		}
	}
}
