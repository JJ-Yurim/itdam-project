package com.ssafy.c206.backend.boss.api;

import java.util.List;

import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.submission.api.BossSubmissionHistoryItem;
import com.ssafy.c206.backend.boss.submission.api.BossSubmissionRequest;
import com.ssafy.c206.backend.boss.submission.api.BossSubmissionResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(
		name = "보스전",
		description = "보스전 진행 상황, 제출 가능한 문장, 제출 결과와 제출 이력을 조회합니다.")
public interface BossApi {

	@Operation(
			summary = "보스전 목록 조회",
			description = "참여 가능한 보스전과 진행 결과를 찾아 다음 행동을 결정하도록 보스전을 페이지 조회합니다. "
					+ "인증 없이도 호출할 수 있는 열람 공개 API입니다. GET /api/v1/boss-runs에 status, page, size 쿼리를 보냅니다. "
					+ "status는 SCHEDULED, ACTIVE, CLEARED, ENDED_UNCLEARED, CLOSED 중 하나이며 "
					+ "status=CLEARED&status=CLOSED처럼 여러 번 보내면 그 상태를 모두 조회합니다. "
					+ "page는 0부터 시작하며 size 기본값은 20, 최대값은 100입니다. "
					+ "성공하면 200과 PageResponse<BossRunSummaryResponse>를 받습니다. 목록은 카드에 필요한 필드만 담으므로 "
					+ "보스 설명과 제출 이력(mySubmissions)은 상세 조회로 받으세요. "
					+ "JSESSIONID 세션 쿠키를 함께 보내면 내 제출 현황(mySubmissionCount, remainingSubmissionCount)이 "
					+ "채워지고, 세션이 없으면 0으로 내려갑니다. "
					+ "400이면 status/page/size 범위를 고치고, 403이면 계정을 활성화한 뒤 재시도하세요.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "보스전 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	PageResponse<BossRunSummaryResponse> getBossRuns(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "보스전 상태 필터입니다. 여러 번 보내면 그 상태를 모두 조회하고, 생략하면 전체를 조회합니다.",
					example = "ACTIVE")
			List<BossRunStatus> status,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);

	@Operation(
			summary = "보스전 상세 조회",
			description = "제출 전 현재 페이즈·분위기·남은 제출 횟수를 확인하도록 보스전 상세를 조회합니다. "
					+ "인증 없이도 호출할 수 있는 열람 공개 API입니다. GET /api/v1/boss-runs/{bossRunId}의 경로 변수로 식별자를 보냅니다. "
					+ "성공하면 200과 BossRunResponse를 받습니다. JSESSIONID 세션 쿠키를 함께 보내면 내 제출 현황이 채워지고, "
					+ "세션이 없으면 0과 빈 배열로 내려갑니다. 403이면 계정을 활성화한 뒤 다시 호출하고, "
					+ "404이면 bossRunId를 목록에서 새로 선택하세요.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "보스전 상세"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	BossRunResponse getBossRun(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "보스전 식별자입니다.", example = "7") Long bossRunId);

	@Operation(
			summary = "제출 가능 문장 조회",
			description = "현재 ACTIVE 보스전에 제출할 수 있는 평가 완료 문장을 고르도록 목록을 조회합니다. "
					+ "JSESSIONID 세션 쿠키로 인증한 뒤 GET /api/v1/boss-runs/{bossRunId}/eligible-sentences의 경로 변수와 "
					+ "page, size 쿼리를 보냅니다. page는 0부터 시작하고 size 기본값은 20, 최대값은 100입니다. "
					+ "성공하면 200과 PageResponse<EligibleSentenceResponse>를 받습니다. 400이면 페이지 값을 고치고, "
					+ "401이면 로그인, 403이면 계정을 활성화한 뒤 재시도하며, 404이면 bossRunId를 확인하세요. "
					+ "409이면 보스전이 ACTIVE가 아니거나 상태가 바뀐 것이므로 상세를 새로고침한 뒤 ACTIVE 상태에서 다시 조회하세요.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "제출 가능 문장 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	PageResponse<EligibleSentenceResponse> getEligibleSentences(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "보스전 식별자입니다.", example = "7") Long bossRunId,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100)  int size);

	@Operation(
			summary = "보스전 문장 제출",
			description = "선택한 평가 완료 문장을 보스전에 반영해 피해와 페이즈 결과를 얻습니다. "
					+ "JSESSIONID 세션 쿠키와 X-XSRF-TOKEN 헤더로 인증·CSRF 검증한 뒤 "
					+ "POST /api/v1/boss-runs/{bossRunId}/submissions의 경로 변수와 BossSubmissionRequest JSON 본문을 보냅니다. "
					+ "명령 실행 시점에 보스전 상태·문장 자격·정책을 다시 검증합니다. 성공하면 201과 BossSubmissionResponse를 받습니다. "
					+ "400이면 본문을 고치고, 401이면 로그인, 403이면 CSRF 토큰을 새로 발급받아 헤더에 넣으세요. "
					+ "404이면 bossRunId 또는 문장 버전을 확인하고, 409이면 보스전 상세를 새로고침해 최신 흐름에서 다시 제출하세요. "
					+ "422이면 제출 가능한 평가 완료 문장과 동의 여부를 확인하고, 429이면 다음 서비스 날짜에 다시 시도하세요.")
	@SecurityRequirement(name = "sessionAuth")
	@SecurityRequirement(name = "csrfToken")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "피해 계산과 제출 저장 완료"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
			@ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity"),
			@ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests")
	})
	BossSubmissionResponse submit(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "제출할 보스전 식별자입니다.", example = "7") Long bossRunId,
			@RequestBody(
					description = "제출할 문장 버전과 제출 동의입니다.",
					required = true)
			@Valid BossSubmissionRequest request);

	@Operation(
			summary = "내 보스전 제출 이력 조회",
			description = "내가 낸 문장과 피해를 확인하도록 제출 이력을 페이지 조회합니다. "
					+ "JSESSIONID 세션 쿠키로 인증한 뒤 GET /api/v1/boss-runs/{bossRunId}/submissions/me의 경로 변수와 "
					+ "page, size 쿼리를 보냅니다. page는 0부터 시작하고 size 기본값은 20, 최대값은 100입니다. "
					+ "알 수 없는 bossRunId도 빈 페이지로 응답할 수 있습니다. 성공하면 200과 PageResponse<BossSubmissionHistoryItem>을 받습니다. "
					+ "400이면 페이지 값을 고치고, 401이면 로그인하여 새 세션 쿠키로 다시 호출하세요.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 제출 이력 페이지"),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	PageResponse<BossSubmissionHistoryItem> getMySubmissions(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "보스전 식별자입니다.", example = "7") Long bossRunId,
			@Parameter(description = "0부터 시작하는 페이지 번호입니다.", example = "0") @Min(0) int page,
			@Parameter(description = "페이지 크기입니다. 기본값은 20이고 최대 100입니다.", example = "20") @Min(1) @Max(100) int size);
}
