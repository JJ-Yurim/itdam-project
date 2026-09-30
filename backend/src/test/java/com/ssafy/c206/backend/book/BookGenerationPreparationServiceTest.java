package com.ssafy.c206.backend.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.book.domain.AiJobType;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparation;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.BookAdminService;
import com.ssafy.c206.backend.book.api.AdminBookCoverUpdateRequest;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.book.service.BookGenerationDispatchService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.integration.book.FakeBookGenerationRequester;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookGenerationPreparationServiceTest {
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BookRepository bookRepository;
	@Autowired private AiJobRepository aiJobRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private FakeBookGenerationRequester requester;
	@Autowired private ServiceClock serviceClock;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private BookGenerationDispatchService dispatchService;
	@Autowired private BossRepository bossRepository;
	@Autowired private ImageAssetRepository imageAssetRepository;
	@Autowired private BookAdminService bookAdminService;

	@AfterEach
	void clearRequester() {
		requester.clear();
	}

	@Test
	@Transactional
	void createsOneBookAndJobAndRequestsOnlyAfterPreparation() {
		BossRun run = clearedRun();

		BookGenerationPreparation preparation = preparationService.prepareAfterClear(run.getId());
		assertThat(preparation.requestRequired()).isTrue();
		assertThat(bookRepository.findByBossRunId(run.getId())).isPresent();
		assertThat(aiJobRepository.findByBossRunIdAndJobType(run.getId(), AiJobType.BOOK_GENERATION)).isPresent();

		preparationService.requestAfterCommit(preparation);
		var job = aiJobRepository.findByBossRunIdAndJobType(run.getId(), AiJobType.BOOK_GENERATION).orElseThrow();
		assertThat(requester.requests()).hasSize(1).first().satisfies(request -> {
			// 재료는 싣지 않는다(ADR-007). 실행 지시와 대조 식별자만 나른다.
			assertThat(request.jobId()).isEqualTo(job.getId());
			assertThat(request.correlationId()).isEqualTo(job.getCorrelationId());
		});
	}

	@Test
	@Transactional
	void duplicateClearPreparationIsIdempotent() {
		BossRun run = clearedRun();
		BookGenerationPreparation first = preparationService.prepareAfterClear(run.getId());
		preparationService.requestAfterCommit(first);
		BookGenerationPreparation second = preparationService.prepareAfterClear(run.getId());
		preparationService.requestAfterCommit(second);

		assertThat(second.requestRequired()).isFalse();
		assertThat(bookRepository.findByBossRunId(run.getId())).hasValueSatisfying(book -> assertThat(book.getStatus()).isEqualTo(BookStatus.GENERATING));
		assertThat(aiJobRepository.findByBossRunIdAndJobType(run.getId(), AiJobType.BOOK_GENERATION))
				.hasValueSatisfying(job -> assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING));
		assertThat(bookRepository.count()).isOne();
		assertThat(aiJobRepository.count()).isOne();
		assertThat(requester.requests()).hasSize(1);
	}

	@Test
	@Transactional
	void requesterFailureLeavesRecoverablePendingRows() {
		BossRun run = clearedRun();
		requester.failWith(new IllegalStateException("fake unavailable"));
		BookGenerationPreparation preparation = preparationService.prepareAfterClear(run.getId());
		preparationService.requestAfterCommit(preparation);

		assertThat(aiJobRepository.findByBossRunIdAndJobType(run.getId(), AiJobType.BOOK_GENERATION))
				.hasValueSatisfying(job -> {
					assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
					assertThat(job.isRequestDispatched()).isFalse();
				});
		assertThat(bookRepository.findByBossRunId(run.getId()))
				.hasValueSatisfying(book -> assertThat(book.getStatus()).isEqualTo(BookStatus.GENERATING));

		requester.clear();
		Long jobId = aiJobRepository.findByBossRunIdAndJobType(run.getId(), AiJobType.BOOK_GENERATION)
				.orElseThrow().getId();
		assertThat(dispatchService.dispatch(jobId)).isTrue();
		assertThat(requester.requests()).hasSize(1);
		assertThat(aiJobRepository.findById(jobId)).hasValueSatisfying(
				job -> assertThat(job.isRequestDispatched()).isTrue());
	}

	@Test
	@Transactional
	void bookKeepsCoverCapturedAtCreationWhenBossDefaultChanges() {
		BossRun run = clearedRun();
		Instant now = serviceClock.now();
		User admin = userRepository.save(User.createFixture(
				"cover-admin-" + System.nanoTime() + "@test.local", "hash", "cover-admin"));
		ImageAsset original = activeCover(admin, "original");
		ImageAsset replacement = activeCover(admin, "replacement");
		ImageAsset bookOverride = activeCover(admin, "book-override");
		run.getBoss().changeBookCover(original, now);
		bossRepository.saveAndFlush(run.getBoss());

		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		assertThat(book.getCoverImageAsset()).isEqualTo(original);

		run.getBoss().changeBookCover(replacement, now.plusSeconds(1));
		bossRepository.saveAndFlush(run.getBoss());
		assertThat(bookRepository.findById(book.getId()).orElseThrow().getCoverImageAsset()).isEqualTo(original);

		bookAdminService.changeCover(new AuthenticatedUser(admin.getId(), admin.getEmail()), book.getId(),
				new AdminBookCoverUpdateRequest(bookOverride.getId()));
		assertThat(bookRepository.findById(book.getId()).orElseThrow().getCoverImageAsset()).isEqualTo(bookOverride);
		assertThat(bossRepository.findById(run.getBoss().getId()).orElseThrow().getBookCoverImageAsset())
				.isEqualTo(replacement);
	}

	@Test
	@Transactional
	void invalidCoverChangeDoesNotMutateBook() {
		BossRun run = clearedRun();
		User admin = userRepository.save(User.createFixture(
				"cover-mutation-" + System.nanoTime() + "@test.local", "hash", "cover-mutation"));
		ImageAsset original = activeCover(admin, "mutation-original");
		ImageAsset replacement = activeCover(admin, "mutation-replacement");
		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		book.changeCover(original, serviceClock.now());

		assertThatThrownBy(() -> book.changeCover(replacement, null)).isInstanceOf(RuntimeException.class);
		assertThat(book.getCoverImageAsset()).isEqualTo(original);
	}

	private ImageAsset activeCover(User admin, String name) {
		ImageAsset asset = ImageAsset.temporary("book-covers/" + name + "-" + System.nanoTime() + ".png",
				"image/png", 10, ImagePurpose.BOOK_COVER, admin, serviceClock.now());
		asset.activate();
		return imageAssetRepository.save(asset);
	}

	private BossRun clearedRun() {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		run = bossRunRepository.save(run);
		var phase = bossPhaseRepository.findDetailsByBossRunIdIn(List.of(run.getId())).getFirst();
		User user = userRepository.save(User.createFixture(
				"book-preparation-" + System.nanoTime() + "@test.local", "hash", "book-preparation"));
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = sentenceVersionRepository.save(
				SentenceVersion.draft(sentence, 1, "책 생성 요청 재료 문장입니다.", now));
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		submissionRepository.save(BossSubmission.create(run, phase, user, version, serviceClock.today(), damage, now));
		return run;
	}
}
