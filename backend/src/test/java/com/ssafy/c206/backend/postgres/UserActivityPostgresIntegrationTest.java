package com.ssafy.c206.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.MyLibraryQueryService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.service.MyBossRunQueryService;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserActivityPostgresIntegrationTest extends PostgresIntegrationTestSupport {

	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private MyBossRunQueryService myBossRunQueryService;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private MyLibraryQueryService myLibraryQueryService;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private ServiceClock serviceClock;

	@Test
	void groupedUserActivityQueriesRunOnPostgres() {
		String suffix = String.valueOf(System.nanoTime());
		User user = userRepository.save(User.createFixture("activity-" + suffix + "@postgres.test", "hash",
				"activity-" + suffix));
		AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail());
		Instant now = serviceClock.now();

		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now.minusSeconds(60));
		BossSubmission first = submissionFor(run, user, "first", now.minusSeconds(20));
		submissionFor(run, user, "second", now.minusSeconds(10));

		var bossRuns = myBossRunQueryService.getMyBossRuns(principal, null, 0, 20);
		assertThat(bossRuns.totalElements()).isOne();
		assertThat(bossRuns.content()).singleElement().satisfies(item -> {
			assertThat(item.bossRunId()).isEqualTo(run.getId());
			assertThat(item.lastSubmittedAt()).isNotNull();
		});

		run.markCleared(now);
		bossRunRepository.saveAndFlush(run);
		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		contentRepository.save(BookContent.create(book, 1, ContentType.AI_GENERATED, "opening", null, null, now));
		contentRepository.save(BookContent.create(book, 2, ContentType.USER_ORIGINAL, "first", user, first, now));
		book.complete(now.plusSeconds(1), "본문 전문입니다.", "테스트용 요약입니다.");
		bookRepository.saveAndFlush(book);

		var books = myLibraryQueryService.getMyContributedBooks(principal, 0, 20);
		assertThat(books.totalElements()).isOne();
		assertThat(books.content()).singleElement().satisfies(item -> {
			assertThat(item.bookId()).isEqualTo(book.getId());
			assertThat(item.contentCount()).isEqualTo(2);
			assertThat(item.myIncludedContentCount()).isOne();
		});
	}

	private BossSubmission submissionFor(BossRun run, User author, String text, Instant submittedAt) {
		var phase = bossPhaseRepository.findDetailsByBossRunIdIn(List.of(run.getId())).getFirst();
		Sentence sentence = sentenceRepository.save(Sentence.create(author, submittedAt));
		SentenceVersion version = sentenceVersionRepository.save(SentenceVersion.draft(sentence, 1, text, submittedAt));
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		return submissionRepository.saveAndFlush(
				BossSubmission.create(run, phase, author, version, serviceClock.today(), damage, submittedAt));
	}
}
