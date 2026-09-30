package com.ssafy.c206.backend.dailyword.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "오늘의 단어", description = "서비스 날짜별 오늘의 단어와 예문을 조회합니다.")
public interface DailyWordApi {

	@Operation(
			summary = "오늘의 단어 조회",
			description = "글쓰기와 평가에 사용할 서비스 날짜별 단어 세트를 조회합니다. JSESSIONID 세션 쿠키로 인증한 뒤 GET /api/v1/daily-words/today를 본문·쿼리 없이 호출합니다. 성공하면 200과 DailyWordResponse를 받습니다. 401이면 로그인하여 새 세션 쿠키를 발급받고, 403이면 계정을 활성화한 뒤 다시 호출하세요. serviceDate는 서버가 정한 서비스 날짜이며 클라이언트 시간이나 배포 환경 정보가 아닙니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "오늘의 단어 세트"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
	})
	DailyWordResponse today(@Parameter(hidden = true) AuthenticatedUser principal);

	@Operation(
			summary = "오늘의 단어 미리보기 조회",
			description = "로그인하지 않은 방문자가 서비스를 살펴보도록 서비스 날짜별 미리보기 단어 5개를 조회합니다. "
					+ "인증 없이 GET /api/v1/daily-words/preview를 본문·쿼리 없이 호출합니다. 성공하면 200과 "
					+ "DailyWordPreviewResponse를 받습니다. 성공 응답은 다음 서비스 날짜가 시작되기 전까지 "
					+ "브라우저 공개 캐시를 사용할 수 있습니다. 같은 서비스 날짜에는 항상 같은 단어가 제공되지만, "
					+ "회원에게 지급되는 개인 세트와는 무관한 열람 전용 데이터이므로 문장 작성·평가에는 쓸 수 없습니다. "
					+ "가입 후에는 GET /api/v1/daily-words/today로 나만의 단어 세트를 받으세요. "
					+ "409이면 활성 단어가 5개 미만인 것이므로 운영자에게 단어 시딩을 요청하세요.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "미리보기 단어 목록"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	ResponseEntity<DailyWordPreviewResponse> preview();
}
