package com.ssafy.c206.backend.common.api;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
		name = "API 정보",
		description = "인증된 호출자에게 최소한의 API 식별 정보만 제공합니다.")
public interface ApiRootApi {

	@Operation(
			summary = "API 루트 조회",
			description = "클라이언트가 연결한 API의 최소 식별 정보를 확인하도록 루트 정보를 조회합니다. JSESSIONID 세션 쿠키로 인증한 뒤 GET /api/v1을 본문·쿼리 없이 호출합니다. 성공하면 200과 name, version을 담은 ApiRootResponse를 받습니다. 401이면 로그인하여 새 세션 쿠키를 발급받은 뒤 다시 호출하세요. 배포 환경, 호스트, 빌드 정보 같은 운영 메타데이터는 노출하지 않습니다.")
	@SecurityRequirement(name = "sessionAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "API 이름과 버전"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
	})
	ApiRootResponse root();
}
