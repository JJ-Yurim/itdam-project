package com.ssafy.c206.backend.boss.stream.api;

import com.ssafy.c206.backend.boss.stream.service.BossRunStreamService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/boss-runs")
@RequiredArgsConstructor
public class BossRunStreamController implements BossRunStreamApi {

	private final BossRunStreamService streamService;

	@GetMapping(value = "/{bossRunId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> subscribe(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable Long bossRunId) {
		// X-Accel-Buffering: no — EC2 호스트 nginx의 location /api/에는 버퍼링 해제 설정이 없어
		// 이벤트가 뭉쳐 늦게 도착할 수 있다. 이 응답 헤더가 nginx 설정 변경 없이 버퍼링을 끈다.
		return ResponseEntity.ok()
				.header("X-Accel-Buffering", "no")
				.header("Cache-Control", "no-cache")
				.body(streamService.subscribe(principal, bossRunId));
	}
}
