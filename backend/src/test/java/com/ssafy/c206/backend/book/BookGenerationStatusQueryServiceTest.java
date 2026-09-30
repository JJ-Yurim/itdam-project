package com.ssafy.c206.backend.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.integration.book.BookGenerationResult;
import com.ssafy.c206.backend.integration.book.BookGenerationResultHandler;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.BookGenerationStatusQueryService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookGenerationStatusQueryServiceTest {
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookGenerationStatusQueryService statusService;
	@Autowired private BookGenerationResultHandler resultHandler;
	@Autowired private UserRepository userRepository;
	@Autowired private ServiceClock serviceClock;
	@Autowired private AiJobRepository aiJobRepository;

	@Test
	@Transactional
	void statusBoundaryExposesPendingThenFixtureResultTransitions() {
		BossRun run = clearedRun();
		preparationService.requestAfterCommit(preparationService.prepareAfterClear(run.getId()));
		AuthenticatedUser principal = user();

		assertThat(statusService.getStatus(principal, run.getId()))
				.satisfies(status -> {
					assertThat(status.bookStatus()).isEqualTo(BookStatus.GENERATING);
					assertThat(status.jobStatus()).isEqualTo(AiJobStatus.PENDING);
				});

		Long jobId = statusService.getStatus(principal, run.getId()).jobId();
		resultHandler.handle(success(jobId));
		assertThat(statusService.getStatus(principal, run.getId()))
				.satisfies(status -> {
					assertThat(status.bookStatus()).isEqualTo(BookStatus.COMPLETED);
					assertThat(status.jobStatus()).isEqualTo(AiJobStatus.SUCCESS);
				});
		resultHandler.handle(success(jobId));
		assertThat(statusService.getStatus(principal, run.getId()).jobStatus()).isEqualTo(AiJobStatus.SUCCESS);
	}

	@Test
	@Transactional
	void fixtureFailureBoundaryMapsBookAndJobToFailed() {
		BossRun run = clearedRun();
		preparationService.requestAfterCommit(preparationService.prepareAfterClear(run.getId()));
		AuthenticatedUser principal = user();
		Long jobId = statusService.getStatus(principal, run.getId()).jobId();

		var job = aiJobRepository.findById(jobId).orElseThrow();
		resultHandler.handle(BookGenerationResult.failure(jobId, job.getCorrelationId(), "fixture result failed"));
		assertThat(statusService.getStatus(principal, run.getId()))
				.satisfies(status -> {
					assertThat(status.bookStatus()).isEqualTo(BookStatus.FAILED);
					assertThat(status.jobStatus()).isEqualTo(AiJobStatus.FAILED);
					assertThat(status.updatedAt()).isNotNull();
				});
	}

	private BossRun clearedRun() {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		return bossRunRepository.save(run);
	}

	private AuthenticatedUser user() {
		User user = userRepository.save(User.createFixture("book-status-" + System.nanoTime() + "@test.local", "hash", "book-status-" + System.nanoTime()));
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	/** 성공 결과는 본문 검증을 통과해야 하므로(ISSUE-96) 최소 유효 본문을 함께 보낸다. */
	private static List<BookGenerationResult.Content> aiOnlyContents() {
		return List.of(new BookGenerationResult.Content(1, "AI_GENERATED", "상태 전이 검증용 본문", null));
	}

	private BookGenerationResult success(Long jobId) {
		var job = aiJobRepository.findById(jobId).orElseThrow();
		var contents = aiOnlyContents();
		return BookGenerationResult.success(jobId, job.getCorrelationId(), contents, contents.getFirst().content(), "테스트용 요약입니다.", List.of(), List.of(),
				"test-model", "test-prompt");
	}
}
