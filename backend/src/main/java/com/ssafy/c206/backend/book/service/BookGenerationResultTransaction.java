package com.ssafy.c206.backend.book.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.domain.AiJob;
import com.ssafy.c206.backend.book.domain.AiJobStatus;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.integration.book.BookGenerationResult;
import com.ssafy.c206.backend.book.repository.AiJobRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.c206.backend.integration.book.BookGenerationMqContract;

/**
 * 책 생성 결과를 확정하는 단일 트랜잭션.
 *
 * <p>성공 결과는 본문 검증을 통과해야만 COMPLETED가 된다(ISSUE-96, 정책 §6.14).
 * 검증 실패는 예외가 아니라 FAILED 확정으로 처리해, 같은 트랜잭션에서 실패 상태가
 * 커밋되고 재전달 메시지는 터미널 가드에 걸리게 한다.
 *
 * <p>여기서 보는 것은 <b>DB를 알아야만 판정할 수 있는 것</b>으로 좁혀져 있다
 * (ISSUE-212): 제출 식별자의 유효성과 소속 보스런, 중복 참조, 커버리지, 순서.
 * 반대로 원문 보존과 body 일치 판정은 ai-service 한 곳에만 둔다. 같은 규칙을 Java로
 * 다시 구현하면 두 기준이 어긋나는 순간 정상 생성물이 저장 단계에서 버려지는데,
 * 이 경로에는 재시도가 없어 곧 영구 실패이기 때문이다. v3에서는 라벨과 body가 모두
 * ai-service가 원문과 대조·조립해 만든 파생값이라 애초에 어긋날 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookGenerationResultTransaction {

	private final AiJobRepository aiJobRepository;
	private final BookRepository bookRepository;
	private final BookContentRepository contentRepository;
	private final BossSubmissionRepository submissionRepository;
	private final ServiceClock serviceClock;

	@Transactional
	public void apply(BookGenerationResult result) {
		var job = aiJobRepository.findByIdForUpdate(result.jobId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (!job.getCorrelationId().equals(result.correlationId())) {
			throw new DomainException(ErrorCode.CONFLICT, "Book result correlationId does not match the job.");
		}
		var book = bookRepository.findByBossRunIdForUpdate(job.getBossRun().getId())
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (job.getStatus() == AiJobStatus.SUCCESS || job.getStatus() == AiJobStatus.FAILED) {
			if ((job.getStatus() == AiJobStatus.SUCCESS) == result.success()) {
				return;
			}
			throw new DomainException(ErrorCode.CONFLICT, "A terminal book generation result cannot be replaced.");
		}
		var now = serviceClock.now();
		if (!result.success()) {
			job.markFailed(now, result.errorMessage());
			book.fail(now);
			return;
		}
		try {
			validateSubmissionCoverage(job, result);
			List<BookContent> contents = buildContents(job, book, result.contents(), now);
			contentRepository.saveAll(contents);
			job.markSuccess(now, result.modelName(), result.promptVersion());
			book.complete(now, resolveBody(result, contents), result.summary());
		} catch (DomainException exception) {
			// 성공 분기 안의 DomainException은 전부 본문 계약 위반이다. 재시도해도 같은
			// 결과이므로 예외를 올리지 않고 이 트랜잭션에서 FAILED로 확정한다.
			log.warn("book_generation_content_rejected job_id={} reason={}", job.getId(), exception.getMessage());
			job.markFailed(now, exception.getMessage());
			book.fail(now);
		}
	}

	/**
	 * 독자가 읽을 전문을 정한다.
	 *
	 * <p>body는 ai-service가 블록을 이어 붙여 조립한 결과다(ISSUE-212). 조립 규칙과
	 * 문단 구성은 이야기 구조를 아는 쪽이 알고 있으므로 그대로 싣는다.
	 *
	 * <p>여기서 body와 블록의 일치를 다시 검증하지 않는다. 예전에는 모델이 body를 따로
	 * 써서 보냈기에 대조가 필요했지만, 이제 body는 블록에서 파생된 값이라 어긋날 수 없다.
	 * 오히려 같은 규칙(특히 공백 정규화)을 Java로 다시 구현하면 기준이 미세하게 어긋나는
	 * 순간 정상 생성물을 저장 단계에서 통째로 버리게 된다 — 재시도가 없는 경로라 곧 영구
	 * 실패다. 검증은 ai-service 한 곳에 두고, 여기서는 DB만 아는 것을 본다.
	 *
	 * <p>body가 없는 경우는 조립 이전 버전의 ai-service가 보낸 결과뿐이라, 배포 순서가
	 * 어긋나도 책을 잃지 않도록 블록 조합으로 되돌린다.
	 */
	private String resolveBody(BookGenerationResult result, List<BookContent> contents) {
		if (result.body() == null || result.body().isBlank()) {
			log.warn("book_generation_body_absent job_id={} reason=falling_back_to_block_join", result.jobId());
			return contents.stream().map(BookContent::getContent).collect(Collectors.joining("\n\n"));
		}
		return result.body();
	}

	private void validateSubmissionCoverage(AiJob job, BookGenerationResult result) {
		Set<Long> expected = submissionRepository.findBookGenerationMaterials(job.getBossRun().getId(),
				PageRequest.of(0, BookGenerationMqContract.MAX_SUBMISSIONS)).stream()
				.map(BossSubmission::getId).collect(Collectors.toSet());
		Set<Long> reported = new HashSet<>(result.usedSubmissionIds());
		reported.addAll(result.excludedSubmissionIds());
		if (!reported.equals(expected)) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR,
					"Book result must account for every submitted sentence selected for generation.");
		}
	}

	private List<BookContent> buildContents(AiJob job, Book book, List<BookGenerationResult.Content> blocks,
			Instant now) {
		if (blocks == null || blocks.isEmpty()) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "A successful result must include contents.");
		}
		Map<Long, BossSubmission> submissions = loadSubmissions(job, blocks);
		Set<Long> usedSubmissionIds = new HashSet<>();
		List<BookContent> contents = new ArrayList<>(blocks.size());
		int expectedSequence = 1;
		for (BookGenerationResult.Content block : blocks) {
			if (block.sequenceNo() != expectedSequence++) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR,
						"Content sequence numbers must run from 1 without gaps.");
			}
			ContentType contentType = parseContentType(block.contentType());
			BossSubmission submission = resolveSubmission(contentType, block, submissions, usedSubmissionIds);
			contents.add(BookContent.create(book, block.sequenceNo(), contentType, block.content(),
					submission == null ? null : submission.getUser(), submission, now));
		}
		return contents;
	}

	private Map<Long, BossSubmission> loadSubmissions(AiJob job, List<BookGenerationResult.Content> blocks) {
		List<Long> ids = blocks.stream().map(BookGenerationResult.Content::sourceSubmissionId)
				.filter(id -> id != null).toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, BossSubmission> submissions = submissionRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(BossSubmission::getId, Function.identity()));
		Long bossRunId = job.getBossRun().getId();
		for (Long id : ids) {
			BossSubmission submission = submissions.get(id);
			if (submission == null) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR,
						"Content references an unknown submission: " + id);
			}
			// 다른 보스런의 제출을 참조하면 엉뚱한 사용자가 기여자로 실린다.
			if (!submission.getBossRun().getId().equals(bossRunId)) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR,
						"Content references a submission from another boss run: " + id);
			}
		}
		return submissions;
	}

	private ContentType parseContentType(String value) {
		try {
			return ContentType.valueOf(value);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR, "Unknown content type: " + value);
		}
	}

	private BossSubmission resolveSubmission(ContentType contentType, BookGenerationResult.Content block,
			Map<Long, BossSubmission> submissions, Set<Long> usedSubmissionIds) {
		if (contentType == ContentType.AI_GENERATED) {
			if (block.sourceSubmissionId() != null) {
				throw new DomainException(ErrorCode.VALIDATION_ERROR,
						"AI generated content must not reference a submission.");
			}
			return null;
		}
		if (block.sourceSubmissionId() == null) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR,
					"User-origin content must reference its submission.");
		}
		if (!usedSubmissionIds.add(block.sourceSubmissionId())) {
			throw new DomainException(ErrorCode.VALIDATION_ERROR,
					"A submission must not appear in more than one content block: " + block.sourceSubmissionId());
		}
		return submissions.get(block.sourceSubmissionId());
	}

}
