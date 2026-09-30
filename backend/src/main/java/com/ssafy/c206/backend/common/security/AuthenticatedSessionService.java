package com.ssafy.c206.backend.common.security;

import java.util.List;

import com.ssafy.c206.backend.user.service.SessionAuthenticationService.AuthenticationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticatedSessionService {

	private final SecurityContextRepository securityContextRepository;
	private final CsrfTokenRepository csrfTokenRepository;

	public void install(AuthenticationResult result, HttpServletRequest request, HttpServletResponse response) {
		var authentication = new UsernamePasswordAuthenticationToken(
				result.principal(), null, List.of(new SimpleGrantedAuthority(result.role().name())));
		request.getSession(true);
		request.changeSessionId();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
	}
}
