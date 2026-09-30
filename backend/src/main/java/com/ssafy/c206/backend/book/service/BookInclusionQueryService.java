package com.ssafy.c206.backend.book.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.book.api.BookInclusionSummary;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인(보관함 등)이 책 수록 정보를 조회할 때 사용하는 경계 서비스. */
@Service
@RequiredArgsConstructor
public class BookInclusionQueryService {

	private final BookContentRepository contentRepository;

	/** 제출 식별자 목록의 책 수록 요약을 문장 버전별로 묶어 반환한다. */
	@Transactional(readOnly = true)
	public Map<Long, List<BookInclusionSummary>> getInclusionsGroupedByVersion(Collection<Long> submissionIds) {
		if (submissionIds.isEmpty()) {
			return Map.of();
		}
		return contentRepository.findBySourceSubmissionIdIn(submissionIds).stream()
				.collect(Collectors.groupingBy(content -> content.getSourceSubmission().getSentenceVersion().getId(),
						Collectors.mapping(BookInclusionSummary::from, Collectors.toList())));
	}
}
