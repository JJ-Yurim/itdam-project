package com.ssafy.c206.backend.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.ssafy.c206.backend.boss.api.BossRunSummaryResponse;
import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.service.BossQueryService;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@ActiveProfiles("test")
@Import(BossQueryServiceTest.FixedTimeConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BossQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

	@Autowired private BossFixture bossFixture;
	@Autowired private BossQueryService bossQueryService;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossRepository bossRepository;
	@Autowired private ImageAssetRepository imageAssetRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EmailVerificationStore emailVerificationStore;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;

	private AuthenticatedUser principal(String prefix) {
		User user = userRepository.save(
				User.createFixture(prefix + System.nanoTime() + "@bossquery.test", "hash", prefix + System.nanoTime()));
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	@Test
	void excludesCancelledRunsFromPublicListsEvenWhenRequested() {
		Boss boss = bossRepository.save(Boss.create("cancelled-public-" + System.nanoTime(), "cancelled", NOW));
		BossRun cancelled = BossRun.schedule(boss, NOW.plusSeconds(60), NOW.plusSeconds(3600), NOW);
		cancelled.addPhase(100, BigDecimal.ZERO, null, BigDecimal.ONE);
		cancelled.cancel(NOW.plusSeconds(1));
		bossRunRepository.saveAndFlush(cancelled);

		assertThat(bossQueryService.getRuns(null, null, 0, 20).content())
				.extracting(BossRunSummaryResponse::bossRunId).doesNotContain(cancelled.getId());
		assertThat(bossQueryService.getRuns(null, List.of(BossRunStatus.CANCELLED), 0, 20).content()).isEmpty();
	}

	@Test
	void listLazilyExpiresActiveRunAtExactEndBoundaryAndIsIdempotent() {
		BossRun expired = bossFixture.createThreePhaseRun(NOW.minusSeconds(3_600), NOW, NOW.minusSeconds(3_600));

		AuthenticatedUser user = principal("expiry");
		var result = bossQueryService.getRuns(user, null, 0, 20);

		assertThat(result.content()).singleElement().extracting(response -> response.status())
				.isEqualTo(BossRunStatus.ENDED_UNCLEARED);
		assertThat(bossRunRepository.findById(expired.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ENDED_UNCLEARED);
		bossQueryService.getRun(user, expired.getId());
		assertThat(bossRunRepository.findById(expired.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ENDED_UNCLEARED);
	}

	@Test
	void listAndDetailLazilyActivateAScheduledRunWhoseStartTimePassed() {
		// 예정 회차는 시작 시각이 지나도 스스로 바뀌지 않는다. 조회가 상태를 맞춰 주지 않으면
		// 관리자와 사용자 모두 시작한 레이드를 "예정"으로 보고 제출도 막힌다.
		BossRun started = bossFixture.createScheduledThreePhaseRun(NOW.minusSeconds(60), NOW.plusSeconds(3_600),
				NOW.minusSeconds(7_200));
		AuthenticatedUser user = principal("activation");

		assertThat(bossQueryService.getRuns(user, List.of(BossRunStatus.ACTIVE), 0, 20).content())
				.singleElement().extracting(response -> response.bossRunId()).isEqualTo(started.getId());
		assertThat(bossQueryService.getRun(user, started.getId()).status()).isEqualTo(BossRunStatus.ACTIVE);
		assertThat(bossRunRepository.findById(started.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ACTIVE);
	}

	@Test
	void doesNotExpireClearedRunAndSupportsStatusFilterAndPagination() {
		BossRun cleared = bossFixture.createThreePhaseRun(NOW.minusSeconds(3_600), NOW.minusSeconds(1), NOW.minusSeconds(3_600));
		cleared.markCleared(NOW.minusSeconds(60));
		bossRunRepository.save(cleared);
		bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);
		bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(7_200), NOW);

		var activePage = bossQueryService.getRuns(principal("filter"), List.of(BossRunStatus.ACTIVE), 1, 1);

		assertThat(bossRunRepository.findById(cleared.getId()).orElseThrow().getStatus()).isEqualTo(BossRunStatus.CLEARED);
		assertThat(activePage.content()).hasSize(1);
		assertThat(activePage.totalElements()).isEqualTo(2);
		assertThat(activePage.page()).isOne();
		assertThat(activePage.content().getFirst().currentPhase().phaseNo()).isOne();
		assertThat(activePage.content().getFirst().moods()).extracting(mood -> mood.code())
				.containsExactlyInAnyOrder("JOY", "MYSTERY");
	}

	@Test
	void statusFilterAcceptsSeveralStatusesInOnePage() {
		// 화면의 "종료" 탭 하나가 세 상태에 걸쳐 있다. 한 번의 조회로 한 페이지가 나와야
		// 클라이언트가 페이지네이션을 다시 맞추지 않는다.
		BossRun cleared = bossFixture.createThreePhaseRun(NOW.minusSeconds(7_200), NOW.minusSeconds(3_600),
				NOW.minusSeconds(7_200));
		cleared.markCleared(NOW.minusSeconds(3_700));
		bossRunRepository.save(cleared);
		BossRun expired = bossFixture.createThreePhaseRun(NOW.minusSeconds(7_200), NOW.minusSeconds(1),
				NOW.minusSeconds(7_200));
		bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);

		var endedPage = bossQueryService.getRuns(principal("multi"),
				List.of(BossRunStatus.CLEARED, BossRunStatus.ENDED_UNCLEARED, BossRunStatus.CLOSED), 0, 20);

		assertThat(endedPage.totalElements()).isEqualTo(2);
		assertThat(endedPage.content()).extracting(response -> response.bossRunId())
				.containsExactlyInAnyOrder(cleared.getId(), expired.getId());
		assertThat(endedPage.content()).extracting(response -> response.status())
				.containsExactlyInAnyOrder(BossRunStatus.CLEARED, BossRunStatus.ENDED_UNCLEARED);
	}

	@Test
	void blankStatusValuesAreIgnoredInsteadOfFailingTheRequest() throws Exception {
		// Spring은 빈 문자열을 enum null로 변환한다. 걸러 내지 않으면 EnumSet에서 NPE가 나
		// 400이 아니라 500으로 응답한다. 단일 값이던 이전 계약에서는 흡수되던 입력이다.
		bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();

		mockMvc.perform(get("/api/v1/boss-runs").param("status", "").param("status", "ACTIVE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
	}

	@Test
	void anonymousQueryReturnsRunsWithEmptyPersonalFields() {
		BossRun run = bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);

		var list = bossQueryService.getRuns(null, List.of(BossRunStatus.ACTIVE), 0, 20);
		var detail = bossQueryService.getRun(null, run.getId());

		assertThat(list.content()).singleElement().satisfies(response -> {
			assertThat(response.mySubmissionCount()).isZero();
			// 잔여 횟수는 "제출할 수 있다"는 행동 가능성 진술이므로 제출 권한이 없는 익명에게는 0이다.
			assertThat(response.remainingSubmissionCount()).isZero();
		});
		assertThat(detail.currentPhase().phaseNo()).isOne();
		assertThat(detail.currentPhase().imageUrl()).isEqualTo("/images/system/boss-phase-placeholder.svg");
		assertThat(detail.mySubmissions()).isEmpty();
	}

	@Test
	void submissionSummariesCarryContentWithoutPerRowQueries() {
		// 본문이 목록에 없으면 화면이 항목마다 문장 상세를 다시 부른다(제출 수만큼 요청 증가).
		// 그렇다고 본문을 담느라 서버에서 제출 건마다 SentenceVersion을 지연 로딩하면
		// 문제를 옮긴 것에 불과하므로, 쿼리 수가 제출 수에 비례하지 않는 것까지 고정한다.
		BossRun run = bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);
		AuthenticatedUser author = principal("summary-content");
		List<String> contents = List.of("첫 번째 제출 문장입니다.", "두 번째 제출 문장입니다.",
				"세 번째 제출 문장입니다.");
		contents.forEach(content -> bossFixture.submitEvaluatedSentence(run, author.userId(), content));

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		var detail = bossQueryService.getRun(author, run.getId());

		assertThat(detail.mySubmissions()).hasSize(3)
				.extracting(summary -> summary.content())
				.containsExactlyInAnyOrderElementsOf(contents);
		// 제출 3건에도 조회 쿼리가 건수에 비례해 늘지 않는다(fetch join 유지 확인).
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(8);
	}

	@Test
	void returnsSnapshotImageForCurrentPhaseAndChangesUrlAfterPhaseTransition() {
		User owner = userRepository.save(User.createFixture("image-owner@bossquery.test", "hash", "image-owner"));
		ImageAsset firstImage = activeImage(owner, "phase-one");
		ImageAsset secondImage = activeImage(owner, "phase-two");
		Boss boss = bossRepository.save(Boss.create("image boss", "image response", NOW));
		BossRun run = BossRun.schedule(boss, NOW, NOW.plusSeconds(3600), NOW);
		var firstPhase = run.addPhase(100, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		var secondPhase = run.addPhase(100, BigDecimal.ZERO, null, BigDecimal.ONE, secondImage);
		run.activate(NOW);
		run = bossRunRepository.saveAndFlush(run);

		assertThat(bossQueryService.getRun(null, run.getId()).currentPhase().imageUrl())
				.isEqualTo("/images/boss-phases/phase-one.png");

		firstPhase.applyDamage(100);
		run.advanceAfterCurrentPhaseClear(firstPhase, NOW.plusSeconds(1), secondPhase);
		bossRunRepository.saveAndFlush(run);

		assertThat(bossQueryService.getRun(null, run.getId()).currentPhase().imageUrl())
				.isEqualTo("/images/boss-phases/phase-two.png");
	}

	private ImageAsset activeImage(User owner, String name) {
		ImageAsset image = ImageAsset.temporary("boss-phases/" + name + ".png", "image/png", 10,
				ImagePurpose.BOSS_PHASE, owner, NOW);
		image.activate();
		return imageAssetRepository.save(image);
	}

	@Test
	void listUsesBulkPhaseAndMoodQueriesInsteadOfPerRunQueries() {
		bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);
		bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(7_200), NOW);
		bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(10_800), NOW);
		AuthenticatedUser user = principal("bulk");
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		var result = bossQueryService.getRuns(user, List.of(BossRunStatus.ACTIVE), 0, 20);

		assertThat(result.content()).hasSize(3);
		// active-user check, lazy-expiry guard, page + count, phase, mood, counter: 회차 수와 무관하다.
		// 제출 이력 조회는 목록에 없다. 그 쿼리는 회차가 아니라 사용자의 누적 제출 건수만큼 커진다.
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(7);
	}

	@Test
	void bossRunReadApiAllowsAnonymousReadAndReturnsCurrentPhase() throws Exception {
		BossRun run = bossFixture.createThreePhaseRun(NOW, NOW.plusSeconds(3_600), NOW);
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();

		// 목록·상세는 비회원 열람이 가능하고, 사용자별 필드는 0과 빈 배열로 내려간다(ISSUE-115).
		mockMvc.perform(get("/api/v1/boss-runs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].mySubmissionCount").value(0))
				.andExpect(jsonPath("$.content[0].remainingSubmissionCount").value(0))
				// 제출 이력은 목록 계약에서 빠졌다. 카드가 쓰지 않는데 사용자의 제출 전문을 끌어온다.
				.andExpect(jsonPath("$.content[0].mySubmissions").doesNotExist());
		mockMvc.perform(get("/api/v1/boss-runs/{bossRunId}", run.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPhase.phaseNo").value(1));
		// 개인 데이터 하위 경로는 비회원에게 열리지 않는다.
		mockMvc.perform(get("/api/v1/boss-runs/{bossRunId}/eligible-sentences", run.getId()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/boss-runs/{bossRunId}/submissions/me", run.getId()))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
				.content(EmailVerificationTestSupport.verifiedSignupBody(
						emailVerificationStore, "boss-query@example.com", "password123", "boss-query")))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"boss-query@example.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

		mockMvc.perform(get("/api/v1/boss-runs/{bossRunId}", run.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.currentPhase.phaseNo").value(1))
				.andExpect(jsonPath("$.moods.length()").value(2));
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
