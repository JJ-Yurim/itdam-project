package com.ssafy.c206.backend.boss.api;

import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 보스전 목록 한 줄에 필요한 만큼만 담는 응답.
 *
 * <p>상세({@link BossRunResponse})와 나눈 이유는 내 제출 이력이다. 상세는 제출 이력을
 * 문장 원문까지 함께 내리는데, 목록이 같은 모양을 쓰면 한 페이지를 여는 것만으로 그
 * 사용자가 모든 회차에 제출한 문장 전문을 끌어오게 된다. 카드가 쓰지 않는 데이터다.
 * 보스 설명도 카드에 자리가 없어 제외한다.
 *
 * <p>오늘 제출 수와 잔여 횟수는 남긴다. "오늘 더 쓸 수 있는가"는 목록에서 다음 행동을
 * 고르는 근거이고, 제출 행이 아니라 일자별 집계 한 건만 읽으면 되는 값이다.
 */
public record BossRunSummaryResponse(
		@Schema(description = "보스전 식별자입니다.", example = "7") Long bossRunId,
		@Schema(description = "보스 식별자입니다.", example = "3") Long bossId,
		@Schema(description = "보스 이름입니다.", example = "새벽의 수호자") String bossName,
		@Schema(description = "보스전 상태입니다. SCHEDULED는 예정, ACTIVE는 진행, CLEARED는 클리어, ENDED_UNCLEARED는 미클리어 종료, CLOSED는 마감입니다.", allowableValues = {"SCHEDULED", "ACTIVE", "CLEARED", "ENDED_UNCLEARED", "CLOSED"}, example = "ACTIVE") BossRunStatus status,
		@Schema(description = "보스전 시작 시각입니다.", format = "date-time", example = "2026-07-27T01:00:00Z") Instant startedAt,
		@Schema(description = "보스전 종료 예정 시각입니다.", format = "date-time", example = "2026-07-28T01:00:00Z") Instant endsAt,
		@Schema(description = "클리어 시각입니다. 미클리어면 null입니다.", format = "date-time", nullable = true, example = "2026-07-27T12:00:00Z") Instant clearedAt,
		@Schema(description = "현재까지 누적 피해입니다.", minimum = "0", example = "1280") long accumulatedDamage,
		@Schema(description = "현재 보스 페이즈입니다.") BossPhaseResponse currentPhase,
		@Schema(description = "보스전 분위기와 약점입니다.") List<BossMoodResponse> moods,
		@Schema(description = "오늘 내가 이 보스전에 제출한 횟수입니다.", minimum = "0", example = "1") int mySubmissionCount,
		@Schema(description = "서비스 날짜별 남은 제출 가능 횟수입니다.", minimum = "0", example = "2") int remainingSubmissionCount) {

	public static BossRunSummaryResponse from(BossRun run, BossPhaseResponse currentPhase,
			List<BossMoodResponse> moods, int mySubmissionCount, int remainingSubmissionCount) {
		return new BossRunSummaryResponse(run.getId(), run.getBoss().getId(), run.getBoss().getName(),
				run.getStatus(), run.getStartedAt(), run.getEndsAt(), run.getClearedAt(), run.getAccumulatedDamage(),
				currentPhase, moods, mySubmissionCount, remainingSubmissionCount);
	}
}
