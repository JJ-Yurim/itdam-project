package com.ssafy.c206.backend.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.domain.AiJob;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.AiJobType;
import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.integration.book.BookGenerationResult;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationFailureService;
import com.ssafy.c206.backend.book.service.BookGenerationResultTransaction;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 책 생성 결과·실패 처리 경로를 현재 동작 그대로 고정하는 특성화 테스트.
 * 리팩터링(-64/-65) 시 종결 상태 멱등성과 충돌 규칙이 유지되어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookGenerationResultTransactionTest {

	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private BookRepository bookRepository;
	@Autowired private AiJobRepository aiJobRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookGenerationResultTransaction resultTransaction;
	@Autowired private BookGenerationFailureService failureService;
	@Autowired private UserRepository userRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private ServiceClock serviceClock;

	@Test
	@Transactional
	void successResultCompletesJobAndBook() {
		AiJob job = preparedJob();

		resultTransaction.apply(success(job, aiOnlyContents()));

		assertThat(aiJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(AiJobStatus.SUCCESS);
		assertThat(bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow().getStatus())
				.isEqualTo(BookStatus.COMPLETED);
	}

	@Test
	@Transactional
	void successPersistsContentsWithProvenanceAndAuthor() {
		AiJob job = preparedJob();
		BossSubmission submission = submissionFor(job, "안개가 낀 새벽, 나는 인적 없는 길을 천천히 걸었다.");

		resultTransaction.apply(success(job, List.of(
				new BookGenerationResult.Content(1, "AI_GENERATED", "그 겨울, 도시는 낮게 숨을 쉬었다.", null),
				new BookGenerationResult.Content(2, "USER_ORIGINAL",
						"안개가 낀 새벽, 나는 인적 없는 길을 천천히 걸었다.", submission.getId()))));

		Long bookId = bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow().getId();
		List<BookContent> contents = contentRepository.findDetailsByBookIdIn(List.of(bookId));
		assertThat(contents).hasSize(2);
		assertThat(contents.get(0).getContentType()).isEqualTo(ContentType.AI_GENERATED);
		assertThat(contents.get(0).getAuthor()).isNull();
		assertThat(contents.get(1).getContentType()).isEqualTo(ContentType.USER_ORIGINAL);
		assertThat(contents.get(1).getAuthor().getId()).isEqualTo(submission.getUser().getId());
		assertThat(contents.get(1).getSourceSubmission().getId()).isEqualTo(submission.getId());
		assertThat(aiJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(AiJobStatus.SUCCESS);
	}

	@Test
	@Transactional
	void contractViolationsRejectResultAsFailed() {
		// 알 수 없는 제출 참조
		AiJob unknownSource = preparedJob();
		resultTransaction.apply(success(unknownSource, List.of(
				new BookGenerationResult.Content(1, "USER_ORIGINAL", "본문", Long.MAX_VALUE))));
		assertThat(aiJobRepository.findById(unknownSource.getId()).orElseThrow().getStatus())
				.isEqualTo(AiJobStatus.FAILED);

		// AI 블록이 제출을 참조
		AiJob aiWithSource = preparedJob();
		BossSubmission submission = submissionFor(aiWithSource, "제출 문장.");
		resultTransaction.apply(success(aiWithSource, List.of(
				new BookGenerationResult.Content(1, "AI_GENERATED", "본문", submission.getId()))));
		assertThat(aiJobRepository.findById(aiWithSource.getId()).orElseThrow().getStatus())
				.isEqualTo(AiJobStatus.FAILED);

		// 순서 결번
		AiJob sequenceGap = preparedJob();
		resultTransaction.apply(success(sequenceGap, List.of(
				new BookGenerationResult.Content(2, "AI_GENERATED", "본문", null))));
		assertThat(aiJobRepository.findById(sequenceGap.getId()).orElseThrow().getStatus())
				.isEqualTo(AiJobStatus.FAILED);

		// 빈 본문 배열
		AiJob emptyContents = preparedJob();
		resultTransaction.apply(success(emptyContents, List.of()));
		assertThat(aiJobRepository.findById(emptyContents.getId()).orElseThrow().getStatus())
				.isEqualTo(AiJobStatus.FAILED);
	}

	@Test
	@Transactional
	void storesTheGeneratedBodyAndSummaryAsReceived() {
		// 문단 구성은 이야기 구조를 아는 ai-service가 한다. Spring은 조합하지 않고 그대로 담는다.
		AiJob job = preparedJob();
		BossSubmission submission = submissionFor(job, "제출된 원문 그대로의 문장이다.");
		String body = "서막을 여는 서술이다.\n\n제출된 원문 그대로의 문장이다.\n\n마무리 서술이다.";

		resultTransaction.apply(success(job, List.of(
				new BookGenerationResult.Content(1, "AI_GENERATED", "서막을 여는 서술이다.", null),
				new BookGenerationResult.Content(2, "USER_ORIGINAL", "제출된 원문 그대로의 문장이다.",
						submission.getId()),
				new BookGenerationResult.Content(3, "AI_GENERATED", "마무리 서술이다.", null)), body));

		var book = bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow();
		assertThat(book.getStatus()).isEqualTo(BookStatus.COMPLETED);
		assertThat(book.getBody()).isEqualTo(body);
		assertThat(book.getSummary()).isEqualTo("테스트용 요약입니다.");
	}

	@Test
	@Transactional
	void storesTheAssembledBodyWithoutRevalidatingItAgainstTheBlocks() {
		// body는 ai-service가 블록으로 조립한 파생값이라 어긋날 수 없다(ISSUE-212).
		// 같은 규칙을 Java로 다시 구현하면 공백 기준이 어긋나는 순간 정상 생성물을 저장
		// 단계에서 버리게 되고, 이 경로에는 재시도가 없어 곧 영구 실패가 된다.
		AiJob job = preparedJob();
		BossSubmission submission = submissionFor(job, "제출된 원문 그대로의 문장이다.");
		// 블록 문자열과 공백까지 똑같지는 않은 body를 그대로 저장하는지 본다.
		String body = "서막을 여는 서술이다.\n\n제출된\u3000원문 그대로의 문장이다.";

		resultTransaction.apply(success(job, List.of(
				new BookGenerationResult.Content(1, "AI_GENERATED", "서막을 여는 서술이다.", null),
				new BookGenerationResult.Content(2, "USER_ORIGINAL", "제출된 원문 그대로의 문장이다.",
						submission.getId())), body));

		var book = bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow();
		assertThat(book.getStatus()).isEqualTo(BookStatus.COMPLETED);
		assertThat(book.getBody()).isEqualTo(body);
	}

	@Test
	@Transactional
	void storesTheEditedSentenceAsLabeledByTheAiService() {
		// 라벨은 ai-service가 원문과 대조해 판정한 결과다. Spring은 그 판정을 다시 하지
		// 않는다 — 편집 허용이 정책이고, 어절 기반 핵심부 판정은 정상 편집도 떨어뜨렸다.
		AiJob job = preparedJob();
		BossSubmission submission = submissionFor(job, "안개가 낀 새벽 나는 고요한 길을 걸었다.");
		String edited = "그렇게 낀 새벽, 나는 고요한 길을 천천히 걸어갔다.";

		resultTransaction.apply(success(job, List.of(
				new BookGenerationResult.Content(1, "USER_EDITED", edited, submission.getId()))));

		var book = bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow();
		assertThat(book.getStatus()).isEqualTo(BookStatus.COMPLETED);
		assertThat(contentRepository.findDetailsByBookIdIn(List.of(book.getId())))
				.singleElement().satisfies(content -> {
					assertThat(content.getContentType()).isEqualTo(ContentType.USER_EDITED);
					assertThat(content.getContent()).isEqualTo(edited);
					// 편집본이라도 기여자는 원문 작성자로 남는다.
					assertThat(content.getSourceSubmission().getId()).isEqualTo(submission.getId());
				});
	}

	@Test
	@Transactional
	void fallsBackToBlockJoinWhenBodyIsAbsent() {
		// 요약 계약 이전 버전의 ai-service 결과에도 책을 잃지 않는다(배포 순서 무관).
		AiJob job = preparedJob();
		List<BookGenerationResult.Content> contents = List.of(
				new BookGenerationResult.Content(1, "AI_GENERATED", "첫 번째 서술이다.", null),
				new BookGenerationResult.Content(2, "AI_GENERATED", "두 번째 서술이다.", null));

		resultTransaction.apply(BookGenerationResult.success(job.getId(), job.getCorrelationId(), contents,
				null, null, List.of(), List.of(), "test-model", "test-prompt"));

		var book = bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow();
		assertThat(book.getStatus()).isEqualTo(BookStatus.COMPLETED);
		assertThat(book.getBody()).isEqualTo("첫 번째 서술이다.\n\n두 번째 서술이다.");
		assertThat(book.getSummary()).isNull();
	}

	@Test
	@Transactional
	void rejectsSummaryLongerThanTheColumn() {
		AiJob job = preparedJob();
		List<BookGenerationResult.Content> contents = aiOnlyContents();

		resultTransaction.apply(BookGenerationResult.success(job.getId(), job.getCorrelationId(), contents,
				bodyOf(contents), "가".repeat(501), List.of(), List.of(), "test-model", "test-prompt"));

		assertThat(aiJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(AiJobStatus.FAILED);
	}

	@Test
	@Transactional
	void failureResultFailsJobAndBook() {
		AiJob job = preparedJob();

		resultTransaction.apply(failure(job, "generator crashed"));

		assertThat(aiJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(AiJobStatus.FAILED);
		assertThat(bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow().getStatus())
				.isEqualTo(BookStatus.FAILED);
	}

	@Test
	@Transactional
	void reapplyingTheSameTerminalResultIsIdempotent() {
		AiJob job = preparedJob();
		resultTransaction.apply(success(job, aiOnlyContents()));

		resultTransaction.apply(success(job, aiOnlyContents()));

		assertThat(aiJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(AiJobStatus.SUCCESS);
		assertThat(bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow().getStatus())
				.isEqualTo(BookStatus.COMPLETED);
		// 재전달 메시지가 본문을 중복 생성하지 않는다.
		Long bookId = bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow().getId();
		assertThat(contentRepository.countByBookId(bookId)).isEqualTo(1);
	}

	@Test
	@Transactional
	void conflictingTerminalResultIsRejected() {
		AiJob job = preparedJob();
		resultTransaction.apply(success(job, aiOnlyContents()));

		assertThatThrownBy(() -> resultTransaction.apply(failure(job, "late failure")))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
	}

	@Test
	@Transactional
	void unknownJobResultIsNotFound() {
		assertThatThrownBy(() -> resultTransaction.apply(BookGenerationResult.success(Long.MAX_VALUE,
				"00000000-0000-0000-0000-000000000000", List.of(), "본문 전문입니다.", "테스트용 요약입니다.", List.of(), List.of(),
				"test-model", "test-prompt")))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	@Transactional
	void failureServiceMarksJobAndBookFailedWithDefaultMessage() {
		AiJob job = preparedJob();

		failureService.markFailed(job.getId(), null);

		assertThat(aiJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(AiJobStatus.FAILED);
		assertThat(bookRepository.findByBossRunId(job.getBossRun().getId()).orElseThrow().getStatus())
				.isEqualTo(BookStatus.FAILED);
	}

	@Test
	@Transactional
	void failureServiceRejectsUnknownJobAsNotFound() {
		assertThatThrownBy(() -> failureService.markFailed(Long.MAX_VALUE, "no such job"))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	private AiJob preparedJob() {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		run = bossRunRepository.save(run);
		preparationService.prepareAfterClear(run.getId());
		return aiJobRepository.findByBossRunIdAndJobType(run.getId(), AiJobType.BOOK_GENERATION).orElseThrow();
	}

	/** job의 보스런에 주어진 원문으로 실제 제출을 만든다. USER_ORIGINAL 검증의 비교 대상이 된다. */
	private BossSubmission submissionFor(AiJob job, String sentenceText) {
		Instant now = serviceClock.now();
		BossRun run = job.getBossRun();
		var phase = bossPhaseRepository.findDetailsByBossRunIdIn(List.of(run.getId())).getFirst();
		User user = userRepository.save(User.createFixture(
				"book-author-" + System.nanoTime() + "@test.local", "hash", "author-" + System.nanoTime()));
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		SentenceVersion version = sentenceVersionRepository.save(SentenceVersion.draft(sentence, 1, sentenceText, now));
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		return submissionRepository.save(
				BossSubmission.create(run, phase, user, version, serviceClock.today(), damage, now));
	}

	private static List<BookGenerationResult.Content> aiOnlyContents() {
		return List.of(new BookGenerationResult.Content(1, "AI_GENERATED", "특성화 검증용 본문", null));
	}

	private static BookGenerationResult success(AiJob job, List<BookGenerationResult.Content> contents) {
		return success(job, contents, bodyOf(contents));
	}

	private static BookGenerationResult success(AiJob job, List<BookGenerationResult.Content> contents, String body) {
		List<Long> usedIds = contents.stream().map(BookGenerationResult.Content::sourceSubmissionId)
				.filter(id -> id != null).toList();
		return BookGenerationResult.success(job.getId(), job.getCorrelationId(), contents, body, "테스트용 요약입니다.",
				usedIds, List.of(), "test-model", "test-prompt");
	}

	/** body는 USER_ORIGINAL 원문을 그대로 담아야 저장 단계 검증을 통과한다. */
	private static String bodyOf(List<BookGenerationResult.Content> contents) {
		return contents.isEmpty() ? "본문 전문입니다."
				: contents.stream().map(BookGenerationResult.Content::content)
						.collect(Collectors.joining("\n\n"));
	}

	private static BookGenerationResult failure(AiJob job, String message) {
		return BookGenerationResult.failure(job.getId(), job.getCorrelationId(), message);
	}
}
