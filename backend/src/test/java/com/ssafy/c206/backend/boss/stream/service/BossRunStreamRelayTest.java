package com.ssafy.c206.backend.boss.stream.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.submission.service.BossSubmissionAppliedEvent;
import com.ssafy.c206.backend.integration.stream.BossRunEventStream;
import com.ssafy.c206.backend.integration.stream.BossRunHpStreamEvent;
import com.ssafy.c206.backend.integration.stream.BossRunLeaderboardStreamEvent;
import org.junit.jupiter.api.Test;

class BossRunStreamRelayTest {

	@Test
	void relaysAccumulatedDamageWithTheHpEvent() {
		AtomicReference<BossRunHpStreamEvent> published = new AtomicReference<>();
		BossRunStreamRelay relay = new BossRunStreamRelay(new BossRunEventStream() {
			@Override
			public long nextSequence(long bossRunId) { return 19L; }
			@Override
			public long currentSequence(long bossRunId) { return 18L; }
			@Override
			public void publish(BossRunHpStreamEvent event) { published.set(event); }
			@Override
			public void addListener(Consumer<BossRunHpStreamEvent> listener) { }
			@Override
			public void publishLeaderboard(BossRunLeaderboardStreamEvent event) { }
			@Override
			public void addLeaderboardListener(Consumer<BossRunLeaderboardStreamEvent> listener) { }
		});

		relay.onSubmissionApplied(new BossSubmissionAppliedEvent(1L, 7L, 1, 910L, 1_000L, 380L, 90L,
				BigDecimal.valueOf(84), BossRunStatus.ACTIVE));

		assertThat(published.get()).isNotNull();
		assertThat(published.get().accumulatedDamage()).isEqualTo(380L);
	}

	@Test
	void aFailingStreamDoesNotFailTheCommittedSubmission() {
		// AFTER_COMMIT 리스너의 예외는 호출자에게 전파된다. 여기서 새어 나가면 데미지가 이미
		// 커밋된 제출이 사용자에게 500으로 보여, 재제출을 유도하는 최악의 실패 모드가 된다.
		BossRunStreamRelay relay = new BossRunStreamRelay(new BossRunEventStream() {
			@Override
			public long nextSequence(long bossRunId) {
				throw new IllegalStateException("redis is down");
			}

			@Override
			public long currentSequence(long bossRunId) {
				return 0;
			}

			@Override
			public void publish(BossRunHpStreamEvent event) {
			}

			@Override
			public void addListener(Consumer<BossRunHpStreamEvent> listener) {
			}

			@Override
			public void publishLeaderboard(BossRunLeaderboardStreamEvent event) {
			}

			@Override
			public void addLeaderboardListener(Consumer<BossRunLeaderboardStreamEvent> listener) {
			}
		});

		assertThatCode(() -> relay.onSubmissionApplied(
				new BossSubmissionAppliedEvent(1L, 7L, 1, 910L, 1_000L, 90L,
						BigDecimal.valueOf(84), BossRunStatus.ACTIVE)))
				.doesNotThrowAnyException();
	}
}
