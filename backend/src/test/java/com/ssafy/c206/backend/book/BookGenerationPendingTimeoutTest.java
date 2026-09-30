package com.ssafy.c206.backend.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.BookGenerationStatusQueryService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.UserFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 결과가 오지 않는 고아 PENDING job의 지연 실패 판정(ISSUE-95).
 * 임계 시간을 0으로 두면 발행 직후의 폴링 조회가 곧바로 시간 초과로 판정한다.
 */
// 고유 프로퍼티로 별도 컨텍스트가 만들어지므로, 공유 H2(backend)를 쓰면 이 컨텍스트의
// create-drop이 캐시된 다른 컨텍스트의 테이블을 지운다. 전용 인메모리 DB로 격리한다.
@SpringBootTest(properties = {
		"book.generation.pending-timeout-seconds=0",
		"spring.datasource.url=jdbc:h2:mem:pending-timeout;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookGenerationPendingTimeoutTest {

	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookGenerationStatusQueryService statusQueryService;
	@Autowired private AiJobRepository aiJobRepository;
	@Autowired private BookRepository bookRepository;
	@Autowired private UserFixture userFixture;
	@Autowired private ServiceClock serviceClock;

	@Test
	void pollingFailsTimedOutPendingJobLazily() {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		bossRunRepository.save(run);
		var preparation = preparationService.prepareAfterClear(run.getId());
		Long jobId = preparation.request().jobId();
		assertThat(aiJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(AiJobStatus.PENDING);

		var user = userFixture.createFixture("timeout-" + System.nanoTime() + "@test.local", "hash", "timeout-user");
		var response = statusQueryService.getStatus(new AuthenticatedUser(user.getId(), user.getEmail()), run.getId());

		// 폴링 응답과 저장 상태 모두 실패로 수렴한다.
		assertThat(response.jobStatus()).isEqualTo(AiJobStatus.FAILED);
		assertThat(response.bookStatus()).isEqualTo(BookStatus.FAILED);
		assertThat(aiJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(AiJobStatus.FAILED);
		assertThat(bookRepository.findByBossRunId(run.getId()).orElseThrow().getStatus()).isEqualTo(BookStatus.FAILED);
	}
}
