package com.ssafy.c206.backend.boss.api;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record MyBossRunResponse(
		@Schema(description = "보스런 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "보스 원형 식별자입니다.", example = "3") Long bossId,
		@Schema(description = "보스 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "보스런 상태입니다.", example = "CLEARED") BossRunStatus status,
		@Schema(description = "현재 페이즈 번호입니다.", minimum = "1", example = "3") int currentPhaseNo,
		@Schema(description = "보스런 시작 시각입니다.", format = "date-time", nullable = true) Instant startedAt,
		@Schema(description = "보스런 종료 예정 시각입니다.", format = "date-time") Instant endsAt,
		@Schema(description = "보스런 클리어 시각입니다.", format = "date-time", nullable = true) Instant clearedAt,
		@Schema(description = "내가 이 보스런에 마지막으로 문장을 제출한 시각입니다.", format = "date-time")
		Instant lastSubmittedAt,
		@Schema(description = "보스런에서 생성된 책 식별자입니다. 책이 없으면 null입니다.", nullable = true)
		Long bookId,
		@Schema(description = "보스런에서 생성된 책 상태입니다. 책이 없으면 null입니다.", nullable = true)
		BookStatus bookStatus) {

	public static MyBossRunResponse from(BossRun run, Instant lastSubmittedAt, Long bookId, BookStatus bookStatus) {
		return new MyBossRunResponse(run.getId(), run.getBoss().getId(), run.getBoss().getName(), run.getStatus(),
				run.getCurrentPhaseNo(), run.getStartedAt(), run.getEndsAt(), run.getClearedAt(), lastSubmittedAt,
				bookId, bookStatus);
	}
}
