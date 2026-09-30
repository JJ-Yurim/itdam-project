package com.ssafy.c206.backend.boss.stream.api;

import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "보스전 실시간", description = "진행 중 보스전의 HP 변화를 SSE로 구독합니다.")
public interface BossRunStreamApi {

	@Operation(
			summary = "보스전 HP 스트림 구독",
			description = "진행 중 보스전의 HP 변화를 Server-Sent Events로 받습니다. "
					+ "GET /api/v1/boss-runs/{bossRunId}/events를 EventSource로 열면 text/event-stream 응답이 유지되고, "
					+ "다른 참가자의 제출이 반영될 때마다 hp 이벤트가 도착합니다. "
					+ "JSESSIONID 세션 쿠키 인증이 필요하며 같은 오리진의 EventSource는 쿠키를 자동 전송합니다. "
					+ "연결 직후 현재 상태 스냅숏이 첫 hp 이벤트(appliedDamage 0)로 도착합니다. "
					+ "각 이벤트의 id는 회차 단위로 1씩 증가하는 시퀀스입니다. 수신한 id가 연속하지 않으면 이벤트가 "
					+ "유실된 것이므로 보스전 상세 조회 API로 재동기화하세요. "
					+ "runStatus가 ACTIVE가 아닌 이벤트가 도착하면 회차가 끝난 것이고 서버가 곧 연결을 닫습니다. "
					+ "연결은 30분 후 서버가 끊으며 EventSource가 자동 재연결합니다. "
					+ "401이면 로그인 후 재시도하고, 409면 이 회차는 진행 중이 아니므로 구독 대신 상세 조회를 쓰세요.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "text/event-stream 연결"),
			@ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	@SecurityRequirement(name = "sessionCookie")
	ResponseEntity<SseEmitter> subscribe(
			@Parameter(hidden = true) AuthenticatedUser principal,
			@Parameter(description = "구독할 진행 중 보스전 식별자입니다.", example = "12") Long bossRunId);
}
