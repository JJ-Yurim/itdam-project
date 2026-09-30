package com.ssafy.c206.backend.sentence.evaluation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.service.DailyWordReader;
import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.DailyWordCard;
import com.ssafy.c206.backend.integration.evaluation.SentenceEvaluationRequestMessage;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발행 전 준비와 발행 후 마킹을 각각 짧은 트랜잭션으로 처리한다.
 *
 * <p>발행(confirm 최대 5초)이 이 둘 사이의 트랜잭션 밖에서 일어나게 하기 위한 분리다.
 * 책 생성 발행이 행 잠금을 잡은 채 confirm을 기다리는 결함을 반복하지 않는다.
 */
@Component
@RequiredArgsConstructor
class EvaluationDispatchTransaction {

	private final SentenceVersionRepository versionRepository;
	private final DailyWordReader dailyWordReader;
	private final ServiceClock serviceClock;

	/**
	 * 발행할 메시지를 만든다. 대상이 아니면(이미 발행됨·종결됨·상관 ID 없음) null.
	 *
	 * <p>상관 ID가 없는 행은 마이그레이션 이전 동기 시대의 것이라 발행할 수 없고,
	 * 그 행들은 stale 회수가 정리한다. 재료(오늘의 단어)는 사용자·서비스 날짜로
	 * 결정적이므로 접수 시점과 같은 세트가 다시 나온다.
	 */
	@Transactional(readOnly = true)
	public SentenceEvaluationRequestMessage prepare(long versionId) {
		SentenceVersion version = versionRepository.findWithSentenceById(versionId).orElse(null);
		if (version == null || version.getEvaluationStatus() != EvaluationStatus.EVALUATING
				|| version.isEvaluationDispatched() || version.getEvaluationCorrelationId() == null) {
			return null;
		}
		EvaluationAttempt attempt = reconstructAttempt(version);
		return SentenceEvaluationRequestMessage.from(attempt.command(), version.getEvaluationCorrelationId());
	}

	/** confirm 이후에만 부른다. 그 사이 상태가 바뀐 행(회수·재시도)은 건드리지 않는다. */
	@Transactional
	public void markDispatched(long versionId, UUID correlationId) {
		versionRepository.findWithSentenceByIdForUpdate(versionId)
				.filter(version -> version.getEvaluationStatus() == EvaluationStatus.EVALUATING)
				.filter(version -> correlationId.equals(version.getEvaluationCorrelationId()))
				.ifPresent(SentenceVersion::markEvaluationDispatched);
	}

	/**
	 * 접수 시점의 시도 정보를 버전 행에서 되살린다. 결과 적용도 같은 재구성을 쓴다.
	 *
	 * <p>serviceDate는 접수 시각(evaluationStartedAt)의 서비스 날짜다. 자정을 넘겨 처리돼도
	 * 차감된 날짜의 사용량이 반환되고, 그 날짜의 단어 세트가 다시 조회된다.
	 */
	EvaluationAttempt reconstructAttempt(SentenceVersion version) {
		Long userId = version.getSentence().getUser().getId();
		LocalDate serviceDate = serviceClock.dateAt(version.getEvaluationStartedAt());
		var set = dailyWordReader.findSet(userId, serviceDate)
				.orElseThrow(() -> new IllegalStateException(
						"Daily word set disappeared for an accepted evaluation: version " + version.getId()));
		var items = dailyWordReader.findItems(set.getId());
		Map<Long, Long> itemIdsByWordId = items.stream().collect(Collectors.toMap(
				item -> item.getWord().getId(), item -> item.getId()));
		List<DailyWordCard> cards = items.stream().map(item -> new DailyWordCard(item.getWord().getId(),
				item.getWord().getWord(), item.getWord().getPartOfSpeech(), item.getWord().getDefinition())).toList();
		return new EvaluationAttempt(userId, version.getId(), serviceDate,
				new AiEvaluationCommand(version.getId(), version.getContent(), serviceDate, cards), itemIdsByWordId);
	}
}
