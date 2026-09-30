package com.ssafy.c206.backend.user.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CsrfController implements CsrfApi {

	private final CsrfTokenRepository csrfTokenRepository;

	@GetMapping("/csrf")
	public CsrfTokenResponse token(HttpServletRequest request, HttpServletResponse response) {
		CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
		csrfTokenRepository.saveToken(csrfToken, request, response);
		return new CsrfTokenResponse(csrfToken.getToken());
	}
}
