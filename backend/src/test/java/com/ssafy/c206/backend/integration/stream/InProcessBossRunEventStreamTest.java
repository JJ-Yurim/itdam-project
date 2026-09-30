package com.ssafy.c206.backend.integration.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class InProcessBossRunEventStreamTest {

	@Test
	void sequencesIncreaseIndependentlyPerRun() {
		InProcessBossRunEventStream stream = new InProcessBossRunEventStream();

		assertThat(stream.currentSequence(1L)).isZero();
		assertThat(stream.nextSequence(1L)).isEqualTo(1L);
		assertThat(stream.nextSequence(1L)).isEqualTo(2L);
		// 다른 회차의 발급이 이 회차의 시퀀스에 틈을 만들면 구독자가 유실로 오인한다.
		assertThat(stream.nextSequence(2L)).isEqualTo(1L);
		assertThat(stream.currentSequence(1L)).isEqualTo(2L);
	}

	@Test
	void aFailingListenerDoesNotBlockOtherListeners() {
		InProcessBossRunEventStream stream = new InProcessBossRunEventStream();
		List<Long> received = new ArrayList<>();
		stream.addListener(event -> {
			throw new IllegalStateException("first listener fails");
		});
		stream.addListener(event -> received.add(event.sequence()));

		stream.publish(new BossRunHpStreamEvent(1L, 1L, 1, 90L, 100L, 10L,
				BigDecimal.valueOf(84), "ACTIVE"));

		assertThat(received).containsExactly(1L);
	}
}
