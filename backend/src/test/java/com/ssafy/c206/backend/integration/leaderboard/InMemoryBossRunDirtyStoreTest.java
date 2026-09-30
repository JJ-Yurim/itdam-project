package com.ssafy.c206.backend.integration.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryBossRunDirtyStoreTest {

	private final InMemoryBossRunDirtyStore store = new InMemoryBossRunDirtyStore();

	@Test
	void claimsOneDeduplicatedSnapshot() {
		store.markDirty(7L);
		store.markDirty(7L);
		store.markDirty(8L);

		assertThat(store.claimPending()).containsExactlyInAnyOrder(7L, 8L);
		assertThat(store.claimPending()).isEmpty();
	}

	@Test
	void failureReturnsWorkToTheNextSnapshot() {
		store.markDirty(7L);
		assertThat(store.claimPending()).containsExactly(7L);
		store.requeue(7L);

		assertThat(store.claimPending()).containsExactly(7L);
	}

	@Test
	void crashRecoveryRestoresProcessingWork() {
		store.markDirty(7L);
		assertThat(store.claimPending()).containsExactly(7L);
		store.restoreProcessing();

		assertThat(store.claimPending()).containsExactly(7L);
	}

	@Test
	void aNewMarkDuringProcessingSurvivesCompletion() {
		store.markDirty(7L);
		assertThat(store.claimPending()).containsExactly(7L);
		store.markDirty(7L);
		store.complete(7L);

		assertThat(store.claimPending()).containsExactly(7L);
	}
}
