package com.ssafy.c206.backend.word.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;

/** Counters produced during a streaming dictionary run. */
@Getter
public final class EtlStatistics {

	private long entriesSeen;
	private long sensesSeen;
	private long emitted;
	private long dropped;
	private long malformed;
	private final Map<String, Long> droppedByReason = new LinkedHashMap<>();

	void entrySeen() {
		entriesSeen++;
	}

	void senseSeen() {
		sensesSeen++;
	}

	void emitted() {
		emitted++;
	}

	void dropped(String reason) {
		dropped++;
		droppedByReason.merge(reason, 1L, Long::sum);
	}

	void malformed() {
		malformed++;
	}

	void merge(EtlStatistics other) {
		entriesSeen += other.entriesSeen;
		sensesSeen += other.sensesSeen;
		emitted += other.emitted;
		dropped += other.dropped;
		malformed += other.malformed;
		other.droppedByReason.forEach((reason, count) -> droppedByReason.merge(reason, count, Long::sum));
	}

	public Map<String, Long> droppedByReason() {
		return Collections.unmodifiableMap(droppedByReason);
	}
}
