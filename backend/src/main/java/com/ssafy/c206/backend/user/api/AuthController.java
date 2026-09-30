package com.ssafy.c206.backend.user.api;

import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.DomainException;
import java.util.List;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.service.RegistrationService;
import com.ssafy.c206.backend.user.service.SessionAuthenticationService;
import com.ssafy.c206.backend.user.service.UserQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

	private final RegistrationService registrationService;
	private final SessionAuthenticationService authenticationService;
	private final UserQueryService userQueryService;
	private final SecurityContextRepository securityContextRepository;
	private final CsrfTokenRepository csrfTokenRepository;

	@PostMapping("/users")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@RequestBody SignupRequest request) {
		return registrationService.register(request);
	}

	@PostMapping("/auth/sessions")
	public UserResponse login(
			@RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		SessionAuthenticationService.AuthenticationResult result = authenticationService.authenticate(request);
		AuthenticatedUser principal = result.principal();
		// 권한 문자열은 ROLE_ 프리픽스 없이 역할명 그대로 쓰고, 검사도 항상 hasAuthority로 한다(hasRole 금지).
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal, null, List.of(new SimpleGrantedAuthority(result.role().name())));
		httpRequest.getSession(true);
		httpRequest.changeSessionId();
		SecurityContextHolder.getContext().setAuthentication(authentication);
		securityContextRepository.saveContext(SecurityContextHolder.getContext(), httpRequest, httpResponse);
		// Rotate the SPA token after authentication so the pre-authentication token is not reused.
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(httpRequest), httpRequest, httpResponse);
		return userQueryService.getMe(principal.userId());
	}

	@PostMapping("/auth/sessions/current")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(
			Authentication authentication,
			HttpServletRequest request,
			HttpServletResponse response) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser)) {
			throw new DomainException(ErrorCode.UNAUTHORIZED);
		}
		new CsrfLogoutHandler(csrfTokenRepository).logout(request, response, authentication);
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	@GetMapping("/users/me")
	public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
		return userQueryService.getMe(principal.userId());
	}
}
