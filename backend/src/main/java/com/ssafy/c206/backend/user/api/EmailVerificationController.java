package com.ssafy.c206.backend.user.api;

import com.ssafy.c206.backend.user.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController implements EmailVerificationApi {

	private final EmailVerificationService emailVerificationService;

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public EmailVerificationSendResponse send(
			@RequestBody EmailVerificationSendRequest request,
			HttpServletRequest httpRequest) {
		return emailVerificationService.sendCode(request.email(), clientIp(httpRequest));
	}

	@PostMapping("/confirm")
	public EmailVerificationConfirmResponse confirm(@RequestBody EmailVerificationConfirmRequest request) {
		return EmailVerificationConfirmResponse.from(
				emailVerificationService.confirm(request.email(), request.code()));
	}

	// IP 상한 키. X-Forwarded-For는 최좌측 값을 클라이언트가 조작할 수 있어 쓰지 않고,
	// nginx가 $remote_addr로 덮어쓰는 X-Real-IP를 우선한다(백엔드는 127.0.0.1 바인딩이라 직접 접근 불가).
	private static String clientIp(HttpServletRequest request) {
		String realIp = request.getHeader("X-Real-IP");
		return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
	}
}
