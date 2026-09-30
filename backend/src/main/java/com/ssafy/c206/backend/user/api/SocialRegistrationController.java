package com.ssafy.c206.backend.user.api;

import static com.ssafy.c206.backend.common.config.SecurityConfiguration.PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedSessionService;
import com.ssafy.c206.backend.user.service.PendingSocialRegistration;
import com.ssafy.c206.backend.user.service.SocialRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/social-registration")
@RequiredArgsConstructor
public class SocialRegistrationController implements SocialRegistrationApi {

	private final SocialRegistrationService socialRegistrationService;
	private final AuthenticatedSessionService authenticatedSessionService;

	@GetMapping
	@Override
	public SocialRegistrationPendingResponse pending(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return new SocialRegistrationPendingResponse(session != null
				&& session.getAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE)
				instanceof PendingSocialRegistration);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Override
	public UserResponse complete(@RequestBody SocialRegistrationRequest registrationRequest,
			HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE)
				instanceof PendingSocialRegistration pending)) {
			throw new DomainException(ErrorCode.UNAUTHORIZED);
		}

		var result = socialRegistrationService.register(pending, registrationRequest);
		session.removeAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE);
		authenticatedSessionService.install(result.authentication(), request, response);
		return result.user();
	}
}
