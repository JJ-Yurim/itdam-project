package com.ssafy.c206.backend.sentence.archive.service;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.sentence.archive.api.ArchiveSentenceResponse;
import com.ssafy.c206.backend.sentence.archive.api.UnevaluatedArchiveSentenceResponse;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceMood;
import com.ssafy.c206.backend.sentence.evaluation.domain.SentenceUsedDailyWord;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceEvaluationRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceMoodRepository;
import com.ssafy.c206.backend.sentence.evaluation.repository.SentenceUsedDailyWordRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.user.service.UserQueryService;
import com.ssafy.c206.backend.boss.api.BossSubmissionSummary;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionService;
import com.ssafy.c206.backend.book.api.BookInclusionSummary;
import com.ssafy.c206.backend.book.service.BookInclusionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveQueryService {

	private static final Set<EvaluationStatus> UNEVALUATED_STATUSES = EnumSet.allOf(EvaluationStatus.class).stream()
			.filter(EvaluationStatus::isUnevaluatedArchiveVisible)
			.collect(Collectors.toUnmodifiableSet());

	private final SentenceEvaluationRepository evaluationRepository;
	private final SentenceVersionRepository sentenceVersionRepository;
	private final SentenceMoodRepository moodRepository;
	private final SentenceUsedDailyWordRepository usedDailyWordRepository;
	private final UserQueryService userQueryService;
	private final BossSubmissionService bossSubmissionService;
	private final BookInclusionQueryService bookInclusionQueryService;

	@Transactional(readOnly = true)
	public PageResponse<ArchiveSentenceResponse> getMyArchive(AuthenticatedUser principal, int page, int size) {
		Long userId = requireUserId(principal);
		userQueryService.getActiveUser(userId);
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("evaluatedAt"), Sort.Order.desc("id")));
		var evaluations = evaluationRepository.findArchiveByUserId(userId, pageable);
		List<Long> versionIds = evaluations.getContent().stream()
				.map(evaluation -> evaluation.getSentenceVersion().getId())
				.toList();
		Map<Long, List<SentenceMood>> moods = groupMoods(versionIds);
		Map<Long, List<SentenceUsedDailyWord>> usedDailyWords = groupUsedDailyWords(versionIds);
		Map<Long, List<BossSubmissionSummary>> submissions = groupSubmissions(versionIds);
		Map<Long, List<BookInclusionSummary>> bookInclusions = groupBookInclusions(submissions);
		return PageResponse.from(evaluations.map(evaluation -> ArchiveSentenceResponse.from(evaluation,
				moods.getOrDefault(evaluation.getSentenceVersion().getId(), List.of()),
				usedDailyWords.getOrDefault(evaluation.getSentenceVersion().getId(), List.of()),
				submissions.getOrDefault(evaluation.getSentenceVersion().getId(), List.of()),
				bookInclusions.getOrDefault(evaluation.getSentenceVersion().getId(), List.of()))));
	}

	@Transactional(readOnly = true)
	public PageResponse<UnevaluatedArchiveSentenceResponse> getMyUnevaluatedArchive(
			AuthenticatedUser principal, int page, int size) {
		Long userId = requireUserId(principal);
		userQueryService.getActiveUser(userId);
		var pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		var versions = sentenceVersionRepository.findUnevaluatedArchiveByUserId(
				userId, UNEVALUATED_STATUSES, pageable);
		return PageResponse.from(versions.map(UnevaluatedArchiveSentenceResponse::from));
	}

	@Transactional(readOnly = true)
	public ArchiveSentenceResponse getMyArchiveDetail(AuthenticatedUser principal, Long sentenceVersionId) {
		Long userId = requireUserId(principal);
		userQueryService.getActiveUser(userId);
		var evaluation = evaluationRepository.findArchiveDetailBySentenceVersionId(sentenceVersionId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (!evaluation.getSentenceVersion().getSentence().belongsTo(userId)) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
		List<BossSubmissionSummary> submissions = bossSubmissionService.getSummariesBySentenceVersionId(sentenceVersionId);
		Map<Long, List<BookInclusionSummary>> bookInclusions = bookInclusionQueryService.getInclusionsGroupedByVersion(
				submissions.stream().map(BossSubmissionSummary::submissionId).toList());
		return ArchiveSentenceResponse.from(evaluation,
				moodRepository.findBySentenceVersionIdOrderByRankAsc(sentenceVersionId),
				usedDailyWordRepository.findDetailBySentenceVersionId(sentenceVersionId),
				submissions,
				bookInclusions.getOrDefault(sentenceVersionId, List.of()));
	}

	private Long requireUserId(AuthenticatedUser principal) {
		AuthenticatedUsers.requireUserId(principal);
		return principal.userId();
	}

	private Map<Long, List<SentenceMood>> groupMoods(List<Long> versionIds) {
		if (versionIds.isEmpty()) return Collections.emptyMap();
		return moodRepository.findDetailBySentenceVersionIdIn(versionIds).stream()
				.collect(Collectors.groupingBy(mood -> mood.getSentenceVersion().getId()));
	}

	private Map<Long, List<SentenceUsedDailyWord>> groupUsedDailyWords(List<Long> versionIds) {
		if (versionIds.isEmpty()) return Collections.emptyMap();
		return usedDailyWordRepository.findDetailBySentenceVersionIdIn(versionIds).stream()
				.collect(Collectors.groupingBy(used -> used.getSentenceVersion().getId()));
	}

	private Map<Long, List<BossSubmissionSummary>> groupSubmissions(List<Long> versionIds) {
		return bossSubmissionService.getSummariesBySentenceVersionIds(versionIds);
	}

	private Map<Long, List<BookInclusionSummary>> groupBookInclusions(Map<Long, List<BossSubmissionSummary>> submissions) {
		List<Long> submissionIds = submissions.values().stream().flatMap(List::stream)
				.map(BossSubmissionSummary::submissionId).toList();
		return bookInclusionQueryService.getInclusionsGroupedByVersion(submissionIds);
	}
}
