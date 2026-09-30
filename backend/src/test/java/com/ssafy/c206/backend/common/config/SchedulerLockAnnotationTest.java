package com.ssafy.c206.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import com.ssafy.c206.backend.book.service.BookGenerationRecoveryScheduler;
import com.ssafy.c206.backend.boss.image.service.ImageAssetCleanupScheduler;
import com.ssafy.c206.backend.boss.leaderboard.service.BossRunLeaderboardBroadcaster;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluationDispatchScheduler;
import com.ssafy.c206.backend.sentence.evaluation.service.EvaluationRecoveryScheduler;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SchedulerLockAnnotationTest {

	@ParameterizedTest
	@MethodSource("schedulerLocks")
	void everyScheduledMethodHasTheApprovedLock(Class<?> type, String methodName,
			String name, String least, String most) throws Exception {
		SchedulerLock lock = type.getMethod(methodName).getAnnotation(SchedulerLock.class);

		assertThat(lock).isNotNull();
		assertThat(lock.name()).isEqualTo(name);
		assertThat(lock.lockAtLeastFor()).isEqualTo(least);
		assertThat(lock.lockAtMostFor()).isEqualTo(most);
	}

	private static Stream<Arguments> schedulerLocks() {
		return Stream.of(
				arguments(BossRunLeaderboardBroadcaster.class, "broadcastDirtyRuns",
						"bossLeaderboardBroadcast", "PT1S", "PT30S"),
				arguments(EvaluationDispatchScheduler.class, "relayUndispatched",
						"evaluationDispatchRecovery", "PT2S", "PT1M"),
				arguments(BookGenerationRecoveryScheduler.class, "recoverPendingDispatches",
						"bookGenerationDispatchRecovery", "PT5S", "PT2M"),
				arguments(EvaluationRecoveryScheduler.class, "recover",
						"staleEvaluationRecovery", "PT1M", "PT10M"),
				arguments(ImageAssetCleanupScheduler.class, "cleanup",
						"imageAssetCleanup", "PT5M", "PT2H"));
	}
}
