package com.ssafy.c206.backend.sentence.service;

import java.time.Instant;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.api.SentenceVersionResponse;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SentenceCommandService {

	private final SentenceRepository sentenceRepository;
	private final SentenceVersionRepository sentenceVersionRepository;
	private final UserQueryService userQueryService;
	private final ServiceClock serviceClock;

	@Transactional
	public SentenceVersionResponse create(Long userId, String content) {
		User user = userQueryService.getActiveUser(userId);
		Instant now = serviceClock.now();
		Sentence sentence = sentenceRepository.save(Sentence.create(user, now));
		return SentenceVersionResponse.from(
				sentenceVersionRepository.save(SentenceVersion.draft(sentence, 1, content, now)));
	}

	@Transactional
	public SentenceVersionResponse createVersion(Long userId, Long sentenceId, String content) {
		Sentence sentence = sentenceRepository.findByIdForUpdate(sentenceId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		ensureOwner(userId, sentence);
		ensureActive(sentence);
		int nextVersionNo = sentenceVersionRepository.findMaxVersionNo(sentenceId) + 1;
		try {
			return SentenceVersionResponse.from(sentenceVersionRepository.saveAndFlush(
					SentenceVersion.draft(sentence, nextVersionNo, content, serviceClock.now())));
		} catch (DataIntegrityViolationException exception) {
			throw new DomainException(ErrorCode.CONFLICT, "A sentence version number was already allocated.");
		}
	}

	@Transactional
	public SentenceVersionResponse updateDraft(Long userId, Long versionId, String content) {
		userQueryService.getActiveUser(userId);
		// Serialize draft edits with evaluation start/completion. Without this lock a
		// stale DRAFT entity can overwrite EVALUATING/EVALUATED state on commit.
		SentenceVersion version = sentenceVersionRepository.findWithSentenceByIdForUpdate(versionId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		Sentence sentence = version.getSentence();
		ensureOwner(userId, sentence);
		ensureActive(sentence);
		version.updateDraft(content);
		return SentenceVersionResponse.from(version);
	}

	@Transactional
	public void delete(Long userId, Long sentenceId) {
		userQueryService.getActiveUser(userId);
		Sentence sentence = sentenceRepository.findById(sentenceId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		ensureOwner(userId, sentence);
		sentence.delete(serviceClock.now());
	}

	private void ensureOwner(Long userId, Sentence sentence) {
		if (!sentence.belongsTo(userId)) {
			throw new DomainException(ErrorCode.NOT_FOUND);
		}
	}

	private void ensureActive(Sentence sentence) {
		if (!sentence.isActive()) {
			throw new DomainException(ErrorCode.CONFLICT, "The sentence is deleted or inactive.");
		}
	}
}
