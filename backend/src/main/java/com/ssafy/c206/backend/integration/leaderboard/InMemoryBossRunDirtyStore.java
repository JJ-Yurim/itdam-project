package com.ssafy.c206.backend.integration.leaderboard;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "boss.leaderboard", name = "store",
		havingValue = "in-memory", matchIfMissing = true)
public class InMemoryBossRunDirtyStore implements BossRunDirtyStore {

	private final Set<Long> pending = new LinkedHashSet<>();
	private final Set<Long> processing = new LinkedHashSet<>();

	@Override
	public synchronized void markDirty(long bossRunId) {
		pending.add(bossRunId);
	}

	@Override
	public synchronized void restoreProcessing() {
		pending.addAll(processing);
		processing.clear();
	}

	@Override
	public synchronized List<Long> claimPending() {
		List<Long> snapshot = List.copyOf(pending);
		processing.addAll(snapshot);
		pending.removeAll(snapshot);
		return snapshot;
	}

	@Override
	public synchronized void complete(long bossRunId) {
		processing.remove(bossRunId);
	}

	@Override
	public synchronized void requeue(long bossRunId) {
		pending.add(bossRunId);
		processing.remove(bossRunId);
	}
}
