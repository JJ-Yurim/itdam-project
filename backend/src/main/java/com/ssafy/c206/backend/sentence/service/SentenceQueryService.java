package com.ssafy.c206.backend.sentence.service;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.sentence.api.SentenceResponse;
import com.ssafy.c206.backend.sentence.api.SentenceVersionResponse;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SentenceQueryService {

	private final SentenceRepository sentenceRepository;
	private final SentenceVersionRepository sentenceVersionRepository;
	private final UserQueryService userQueryService;

	@Transactional(readOnly = true)
	public SentenceResponse get(Long userId, Long sentenceId) {
		userQueryService.getActiveUser(userId);
		Sentence sentence = sentenceRepository.findById(sentenceId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		ensureOwner(userId, sentence);
		return SentenceResponse.from(sentence,
				sentenceVersionRepository.findBySentenceIdOrderByVersionNoAsc(sentenceId).stream()
						.map(SentenceVersionResponse::from).toList());
	}

	@Transactional(readOnly = true)
	public SentenceVersionResponse getVersion(Long userId, Long versionId) {
		userQueryService.getActiveUser(userId);
		SentenceVersion version = sentenceVersionRepository.findWithSentenceById(versionId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		ensureOwner(userId, version.getSentence());
		return SentenceVersionResponse.from(version);
	}

	private void ensureOwner(Long userId, Sentence sentence) {
		if (!sentence.belongsTo(userId)) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
	}
}
