package com.ssafy.c206.backend.sentence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.sentence.api.SentenceVersionResponse;
import com.ssafy.c206.backend.sentence.domain.EvaluationStatus;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.sentence.service.SentenceCommandService;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.service.UserFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SentencePersistenceTest {

	@Autowired
	private SentenceCommandService sentenceCommandService;

	@Autowired
	private SentenceVersionRepository sentenceVersionRepository;

	@Autowired
	private UserFixture userFixture;

	private ExecutorService executor;

	@AfterEach
	void shutdownExecutor() {
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	@Test
	void createsVersionOneAsTrimmedDraft() {
		User user = user("draft");

		var version = sentenceCommandService.create(user.getId(), "  안녕하세요\n반갑습니다  ");

		assertThat(version.versionNo()).isEqualTo(1);
		assertThat(version.evaluationStatus()).isEqualTo(EvaluationStatus.DRAFT.name());
		assertThat(version.content()).isEqualTo("안녕하세요\n반갑습니다");
	}

	@Test
	void draftCanBeUpdatedButCompletedVersionIsImmutable() {
		User user = user("immutable");
		var version = sentenceCommandService.create(user.getId(), "초안");

		var updated = sentenceCommandService.updateDraft(user.getId(), version.id(), "수정된 초안");
		assertThat(updated.content()).isEqualTo("수정된 초안");

		SentenceVersion managed = sentenceVersionRepository.findById(version.id()).orElseThrow();
		java.time.Instant now = java.time.Instant.now();
		managed.startEvaluation(now);
		managed.markEvaluated(now);
		sentenceVersionRepository.saveAndFlush(managed);

		assertThatThrownBy(() -> sentenceCommandService.updateDraft(user.getId(), version.id(), "변경 불가"))
				.isInstanceOf(DomainException.class);
	}

	@Test
	void newVersionAndLogicalDeletePreserveReferences() {
		User user = user("delete");
		var first = sentenceCommandService.create(user.getId(), "첫 버전");
		var second = sentenceCommandService.createVersion(user.getId(), first.sentenceId(), "둘째 버전");

		sentenceCommandService.delete(user.getId(), first.sentenceId());

		List<SentenceVersion> versions = sentenceVersionRepository.findBySentenceIdOrderByVersionNoAsc(first.sentenceId());
		assertThat(versions).extracting(SentenceVersion::getVersionNo).containsExactly(1, 2);
		assertThatThrownBy(() -> sentenceCommandService.createVersion(user.getId(), first.sentenceId(), "삭제 후"))
				.isInstanceOf(DomainException.class);
		assertThat(second.id()).isNotNull();
	}

	@Test
	void concurrentVersionCreationUsesPessimisticSentenceLock() throws Exception {
		User user = user("concurrent");
		var first = sentenceCommandService.create(user.getId(), "기준 버전");
		Long sentenceId = first.sentenceId();
		executor = Executors.newFixedThreadPool(2);

		Future<SentenceVersionResponse> one = executor.submit(() -> sentenceCommandService.createVersion(user.getId(), sentenceId, "동시 버전 A"));
		Future<SentenceVersionResponse> two = executor.submit(() -> sentenceCommandService.createVersion(user.getId(), sentenceId, "동시 버전 B"));

		SentenceVersionResponse resultOne = one.get(10, TimeUnit.SECONDS);
		SentenceVersionResponse resultTwo = two.get(10, TimeUnit.SECONDS);
		assertThat(List.of(resultOne.versionNo(), resultTwo.versionNo())).containsExactlyInAnyOrder(2, 3);
		assertThat(sentenceVersionRepository.findBySentenceIdOrderByVersionNoAsc(sentenceId))
				.extracting(SentenceVersion::getVersionNo).containsExactly(1, 2, 3);
	}

	@Test
	void blankAndOverlongContentAreRejectedAfterTrim() {
		User user = user("validation");

		assertThatThrownBy(() -> sentenceCommandService.create(user.getId(), " \n\t "))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> sentenceCommandService.create(user.getId(), "a".repeat(501)))
				.isInstanceOf(DomainException.class);
	}

	private User user(String suffix) {
		return userFixture.createFixture(suffix + "@example.com", "hash", "nick-" + suffix);
	}
}
