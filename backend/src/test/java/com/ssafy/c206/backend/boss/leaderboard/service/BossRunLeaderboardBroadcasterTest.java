package com.ssafy.c206.backend.boss.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ssafy.c206.backend.integration.leaderboard.InMemoryBossRunDirtyStore;
import com.ssafy.c206.backend.integration.stream.BossRunEventStream;
import com.ssafy.c206.backend.integration.stream.BossRunLeaderboardStreamEvent;
import com.ssafy.c206.backend.user.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BossRunLeaderboardBroadcasterTest {

	@Mock private BossRunLeaderboardService leaderboardService;
	@Mock private BossRunEventStream eventStream;
	@Mock private UserQueryService userQueryService;

	@Test
	void successfulRefreshPublishesAndCompletesTheClaim() {
		InMemoryBossRunDirtyStore dirtyStore = new InMemoryBossRunDirtyStore();
		dirtyStore.markDirty(11L);
		when(leaderboardService.refreshFromDatabase(11L)).thenReturn(List.of());
		BossRunLeaderboardBroadcaster broadcaster = new BossRunLeaderboardBroadcaster(
				leaderboardService, eventStream, userQueryService, dirtyStore);

		broadcaster.broadcastDirtyRuns();

		verify(eventStream).publishLeaderboard(new BossRunLeaderboardStreamEvent(11L, List.of()));
		assertThat(dirtyStore.claimPending()).isEmpty();
	}

	@Test
	void failedRefreshIsRequeuedForTheNextScheduledPass() {
		InMemoryBossRunDirtyStore dirtyStore = new InMemoryBossRunDirtyStore();
		dirtyStore.markDirty(11L);
		when(leaderboardService.refreshFromDatabase(11L))
				.thenThrow(new IllegalStateException("database unavailable"));
		BossRunLeaderboardBroadcaster broadcaster = new BossRunLeaderboardBroadcaster(
				leaderboardService, eventStream, userQueryService, dirtyStore);

		broadcaster.broadcastDirtyRuns();

		verifyNoInteractions(eventStream);
		assertThat(dirtyStore.claimPending()).containsExactly(11L);
	}
}
