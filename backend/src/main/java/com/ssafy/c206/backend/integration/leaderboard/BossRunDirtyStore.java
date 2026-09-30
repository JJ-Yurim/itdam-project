package com.ssafy.c206.backend.integration.leaderboard;

import java.util.List;

public interface BossRunDirtyStore {

	void markDirty(long bossRunId);

	void restoreProcessing();

	List<Long> claimPending();

	void complete(long bossRunId);

	void requeue(long bossRunId);
}
