package com.ssafy.c206.backend.sentence.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.ssafy.c206.backend.sentence.api.SentenceVersionResponse;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.dailyword.api.DailyWordResponse;
import com.ssafy.c206.backend.dailyword.service.DailyWordService;
import com.ssafy.c206.backend.integration.ai.AiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.AiEvaluationCommand;
import com.ssafy.c206.backend.integration.ai.AiEvaluationResult;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationClient;
import com.ssafy.c206.backend.integration.ai.FakeAiEvaluationScenario;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.archive.service.ArchiveQueryService;
import com.ssafy.c206.backend.sentence.evaluation.service.SentenceEvaluationService;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.sentence.service.SentenceCommandService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.service.UserFixture;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(ArchiveQueryServiceTest.AiClientConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ArchiveQueryServiceTest {

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired private ArchiveQueryService archiveQueryService;
	@Autowired private SentenceEvaluationService evaluationService;
	@Autowired private SentenceCommandService sentenceCommandService;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private DailyWordService dailyWordService;
	@Autowired private UserFixture userFixture;
	@Autowired private WordRepository wordRepository;
	@Autowired private MutableAiClient aiClient;
	@Autowired private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void setUp() {
		aiClient.setUsedWordIds(Set.of());
		seedWords();
	}

	@Test
	void returnsOnlyMyActiveEvaluatedAssetsWithEvaluationFieldsAndPagination() {
		AuthenticatedUser owner = principal("owner");
		var first = evaluate(owner, "첫 번째 보관 문장", 1);
		var second = evaluate(owner, "두 번째 보관 문장", 2);
		var deleted = evaluate(owner, "삭제된 보관 문장", 0);
		sentenceCommandService.delete(owner.userId(), deleted.sentenceId());
		sentenceCommandService.create(owner.userId(), "평가 전 초안");
		evaluate(principal("other"), "다른 사용자의 문장", 0);

		var firstPage = archiveQueryService.getMyArchive(owner, 0, 1);
		var secondPage = archiveQueryService.getMyArchive(owner, 1, 1);

		assertThat(firstPage.page()).isZero();
		assertThat(firstPage.size()).isEqualTo(1);
		assertThat(firstPage.totalElements()).isEqualTo(2);
		assertThat(firstPage.totalPages()).isEqualTo(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(firstPage.content()).hasSize(1);
		assertThat(secondPage.content()).hasSize(1);
		assertThat(firstPage.content().getFirst().sentenceVersionId())
				.isNotEqualTo(secondPage.content().getFirst().sentenceVersionId());
		assertThat(firstPage.content()).allSatisfy(asset -> {
			assertThat(asset.evaluationStatus()).isEqualTo(EvaluationStatus.EVALUATED);
			assertThat(asset.createdAt()).isNotNull();
			assertThat(asset.baseScore()).isEqualByComparingTo("78.35");
			assertThat(asset.wordBonus()).isIn(new java.math.BigDecimal("0.00"), new java.math.BigDecimal("5.00"));
			assertThat(asset.gameBaseAttack()).isNotNull();
			assertThat(asset.moods()).hasSize(1);
		});

		var detail = archiveQueryService.getMyArchiveDetail(owner, second.id());
		assertThat(detail.content()).isEqualTo("두 번째 보관 문장");
		assertThat(detail.wordBonus()).isEqualByComparingTo("5.00");
		assertThat(detail.usedDailyWords()).hasSize(2);
		assertThat(detail.moods()).extracting("code").containsExactly("NOSTALGIA");
		assertThat(archiveQueryService.getMyArchive(owner, 0, 20).content())
				.extracting("sentenceVersionId").containsExactlyInAnyOrder(first.id(), second.id());
	}

	@Test
	void deniesAnotherUsersArchiveDetail() {
		AuthenticatedUser owner = principal("owner-detail");
		var version = evaluate(owner, "소유자 전용 보관 문장", 0);

		assertThatThrownBy(() -> archiveQueryService.getMyArchiveDetail(principal("intruder"), version.id()))
				.isInstanceOf(DomainException.class)
				.extracting("errorCode").isEqualTo(com.ssafy.c206.backend.common.api.ErrorCode.NOT_FOUND);
	}

	@Test
	void usesAConstantNumberOfQueriesForArchiveRows() {
		AuthenticatedUser owner = principal("query");
		evaluate(owner, "첫 쿼리 문장", 1);
		evaluate(owner, "둘 쿼리 문장", 2);
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		var page = archiveQueryService.getMyArchive(owner, 0, 1);

		assertThat(page.content()).hasSize(1);
		assertThat(statistics.getPrepareStatementCount())
				.as("active-user check + page query + count query + bulk moods + bulk used-word + bulk submission query")
				.isLessThanOrEqualTo(6);
	}

	@Test
	void returnsOnlyMyActiveUnevaluatedVersionsInStableNewestFirstPages() {
		AuthenticatedUser owner = principal("unevaluated-owner");
		var draft = createWithStatus(owner, "미평가 초안", EvaluationStatus.DRAFT);
		var evaluating = createWithStatus(owner, "평가 진행 중", EvaluationStatus.EVALUATING);
		var failed = createWithStatus(owner, "평가 실패", EvaluationStatus.EVALUATION_FAILED);
		createWithStatus(owner, "평가 거절", EvaluationStatus.REJECTED);
		evaluate(owner, "평가 완료", 0);
		var deleted = createWithStatus(owner, "삭제된 초안", EvaluationStatus.DRAFT);
		sentenceCommandService.delete(owner.userId(), deleted.sentenceId());
		createWithStatus(principal("unevaluated-other"), "다른 사용자 초안", EvaluationStatus.DRAFT);

		var firstPage = archiveQueryService.getMyUnevaluatedArchive(owner, 0, 2);
		var secondPage = archiveQueryService.getMyUnevaluatedArchive(owner, 1, 2);

		assertThat(firstPage.totalElements()).isEqualTo(3);
		assertThat(firstPage.totalPages()).isEqualTo(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(firstPage.content()).extracting("sentenceVersionId")
				.containsExactly(failed.id(), evaluating.id());
		assertThat(secondPage.content()).extracting("sentenceVersionId").containsExactly(draft.id());
		assertThat(firstPage.content()).allSatisfy(item -> {
			assertThat(item.createdAt()).isNotNull();
			assertThat(item.evaluationStatus()).isIn(
					EvaluationStatus.DRAFT,
					EvaluationStatus.EVALUATING,
					EvaluationStatus.EVALUATION_FAILED);
		});
	}

	@Test
	void usesAConstantNumberOfQueriesForUnevaluatedArchiveRows() {
		AuthenticatedUser owner = principal("unevaluated-query");
		createWithStatus(owner, "첫 미평가 문장", EvaluationStatus.DRAFT);
		createWithStatus(owner, "둘째 미평가 문장", EvaluationStatus.EVALUATION_FAILED);
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		var page = archiveQueryService.getMyUnevaluatedArchive(owner, 0, 2);

		assertThat(page.content()).hasSize(2);
		assertThat(statistics.getPrepareStatementCount())
				.as("active-user check + page query + count query")
				.isLessThanOrEqualTo(3);
	}

	@Test
	void breaksCreatedAtTiesByDescendingIdAcrossPageBoundaries() {
		AuthenticatedUser owner = principal("unevaluated-tie");
		var seed = sentenceCommandService.create(owner.userId(), "정렬 기준 문장");
		var sentence = sentenceVersionRepository.findWithSentenceById(seed.id()).orElseThrow().getSentence();
		Instant sameCreatedAt = Instant.parse("2030-01-01T00:00:00Z");
		var lowId = sentenceVersionRepository.saveAndFlush(
				SentenceVersion.draft(sentence, 2, "동일 시각 첫 문장", sameCreatedAt));
		var middleId = sentenceVersionRepository.saveAndFlush(
				SentenceVersion.draft(sentence, 3, "동일 시각 둘째 문장", sameCreatedAt));
		var highId = sentenceVersionRepository.saveAndFlush(
				SentenceVersion.draft(sentence, 4, "동일 시각 셋째 문장", sameCreatedAt));

		var firstPage = archiveQueryService.getMyUnevaluatedArchive(owner, 0, 2);
		var secondPage = archiveQueryService.getMyUnevaluatedArchive(owner, 1, 2);

		assertThat(firstPage.content()).extracting("sentenceVersionId")
				.containsExactly(highId.getId(), middleId.getId());
		assertThat(secondPage.content().getFirst().sentenceVersionId()).isEqualTo(lowId.getId());
	}

	private AuthenticatedUser principal(String prefix) {
		int sequence = SEQUENCE.incrementAndGet();
		var user = userFixture.createFixture(prefix + sequence + "@archive.test", "hash", prefix + sequence);
		return new AuthenticatedUser(user.getId(), user.getEmail());
	}

	private SentenceVersionResponse evaluate(AuthenticatedUser principal, String content,
			int usedWordCount) {
		var version = sentenceCommandService.create(principal.userId(), content);
		DailyWordResponse words = dailyWordService.getToday(principal);
		aiClient.setUsedWordIds(words.items().stream().limit(usedWordCount).map(DailyWordResponse.Item::wordId)
				.collect(java.util.stream.Collectors.toSet()));
		evaluationService.evaluate(principal, version.id());
		return version;
	}

	private SentenceVersionResponse createWithStatus(
			AuthenticatedUser principal, String content, EvaluationStatus status) {
		var response = sentenceCommandService.create(principal.userId(), content);
		if (status == EvaluationStatus.DRAFT) return response;

		var version = sentenceVersionRepository.findWithSentenceById(response.id()).orElseThrow();
		Instant at = Instant.parse("2026-08-03T00:00:00Z");
		version.startEvaluation(at);
		if (status == EvaluationStatus.EVALUATION_FAILED) version.markEvaluationFailed();
		else if (status == EvaluationStatus.REJECTED) version.markRejected(at);
		else if (status != EvaluationStatus.EVALUATING) {
			throw new IllegalArgumentException("Unsupported fixture status: " + status);
		}
		sentenceVersionRepository.saveAndFlush(version);
		return response;
	}

	private void seedWords() {
		int sequence = SEQUENCE.incrementAndGet();
		for (int index = 0; index < 5; index++) {
			wordRepository.save(Word.from(DictionarySense.builder().word("archive-" + sequence + "-" + index)
					.partOfSpeech("명사").definition("Archive 테스트 단어 " + sequence + '-' + index).build()));
		}
	}

	@TestConfiguration
	static class AiClientConfiguration {
		@Bean @Primary MutableAiClient mutableAiClient() {
			return new MutableAiClient();
		}
	}

	static class MutableAiClient implements AiEvaluationClient {
		private volatile Set<Long> usedWordIds = Set.of();

		void setUsedWordIds(Set<Long> usedWordIds) {
			this.usedWordIds = Set.copyOf(usedWordIds);
		}

		@Override
		public AiEvaluationResult evaluate(AiEvaluationCommand command) {
			return new FakeAiEvaluationClient(FakeAiEvaluationScenario.SUCCESS, usedWordIds).evaluate(command);
		}
	}
}
