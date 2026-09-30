package com.ssafy.c206.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.common.api.ApiError;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedSessionService;
import com.ssafy.c206.backend.common.security.SocialOAuth2User;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.SocialOAuth2UserService;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.ExistingAccount;
import com.ssafy.c206.backend.user.service.SocialLoginDecision.PendingAccount;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfiguration {
	public static final String PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE =
			"PENDING_SOCIAL_REGISTRATION";

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository,
			AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler,
			CsrfTokenRepository csrfTokenRepository, CsrfTokenRequestHandler csrfTokenRequestHandler,
			SocialOAuth2UserService socialOAuth2UserService,
			AuthenticationSuccessHandler socialLoginSuccessHandler,
			AuthenticationFailureHandler socialLoginFailureHandler) throws Exception {
		http
			.securityContext(context -> context.securityContextRepository(repository))
			.csrf(csrf -> csrf
					.csrfTokenRepository(csrfTokenRepository)
					.csrfTokenRequestHandler(csrfTokenRequestHandler))
			.cors(Customizer.withDefaults())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			// 소셜 로그인. 진입은 GET /api/oauth2/authorization/{kakao|naver|google},
			// 성공 시 비밀번호 로그인과 동일한 세션 인증으로 바꿔 저장한다.
			// 배포 nginx가 /api/* 만 백엔드로 넘기므로 진입·콜백 모두 /api 아래로 매핑한다
			// (provider 콘솔에 등록된 리다이렉트 URI도 /api/login/oauth2/code/{id}).
			.oauth2Login(oauth2 -> oauth2
					.authorizationEndpoint(endpoint -> endpoint.baseUri("/api/oauth2/authorization"))
					.redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
					.userInfoEndpoint(endpoint -> endpoint.userService(socialOAuth2UserService))
					.successHandler(socialLoginSuccessHandler)
					.failureHandler(socialLoginFailureHandler))
			.sessionManagement(session -> session.sessionCreationPolicy(
					SessionCreationPolicy.IF_REQUIRED))
			.authorizeHttpRequests(auth -> auth
					.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
					.requestMatchers("/actuator/health", "/api/v1/csrf", "/api/v1/users", "/api/v1/auth/sessions",
							"/api/v1/auth/social-registration").permitAll()
					// 비회원 열람 허용 범위(ISSUE-115, ISSUE-116). 경로 변수는 한 세그먼트만
					// 매칭하므로 /eligible-sentences, /submissions/me 등 하위 개인 데이터 경로는 열리지 않는다.
					// 이메일 인증은 가입 전 단계라 익명 접근을 허용한다. 남용은 서비스의 쿨다운·IP 상한으로 막는다.
					.requestMatchers("/api/v1/email-verifications", "/api/v1/email-verifications/confirm").permitAll()
					.requestMatchers(HttpMethod.GET,
							"/api/v1/boss-runs", "/api/v1/boss-runs/{bossRunId}",
							"/api/v1/daily-words/preview",
							"/api/v1/copyright-agreements/current",
							"/api/v1/library/books", "/api/v1/library/books/{bookId}",
							// 도서관 화면이 비회원에게 열려 있으므로 그 안의 베스트 문장 탭도 열어 둔다.
							"/api/v1/sentences/feed").permitAll()
					// 운영 API는 시딩된 관리자 계정(ADMIN 역할)만 접근한다. 일반 사용자는 403.
					// 권한은 ROLE_ 프리픽스 없이 부여되므로 hasRole이 아닌 hasAuthority만 쓴다.
					.requestMatchers("/api/admin/**").hasAuthority("ADMIN")
					.requestMatchers("/api/v1/**").authenticated()
					.anyRequest().permitAll())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository(@Value("${security.csrf.cookie-secure:false}") boolean secure) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookiePath("/");
		repository.setCookieCustomizer((ResponseCookie.ResponseCookieBuilder cookie) -> cookie
				.path("/").httpOnly(false).secure(secure).sameSite("Lax"));
		return repository;
	}

	@Bean
	CsrfTokenRequestHandler csrfTokenRequestHandler() {
		return new SpaCsrfTokenRequestHandler();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim).filter(StringUtils::hasText).toList();
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Accept"));
		configuration.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	@Bean
	AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper, ServiceClock serviceClock) {
		return (request, response, exception) -> writeError(objectMapper, serviceClock, response, ErrorCode.UNAUTHORIZED);
	}

	@Bean
	AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper, ServiceClock serviceClock) {
		return (request, response, exception) -> writeError(objectMapper, serviceClock, response, ErrorCode.FORBIDDEN);
	}

	private static void writeError(ObjectMapper objectMapper, ServiceClock serviceClock, HttpServletResponse response,
			ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.status().value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(),
				new ApiError(serviceClock.now(), errorCode.name(), errorCode.defaultMessage(), List.of()));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// 소셜 로그인 성공 시 OAuth2 토큰을 비밀번호 로그인과 같은 형태(AuthenticatedUser principal)로 바꿔
	// 세션에 저장한다. 이후 모든 요청 처리(@AuthenticationPrincipal, hasAuthority)가 로그인 수단과 무관해진다.
	@Bean
	AuthenticationSuccessHandler socialLoginSuccessHandler(SecurityContextRepository repository,
			AuthenticatedSessionService authenticatedSessionService,
			@Value("${app.oauth2.success-redirect:/}") String successRedirect,
			@Value("${app.oauth2.signup-redirect:/oauth/signup}") String signupRedirect,
			@Value("${app.oauth2.failure-redirect:/login}") String failureRedirect) {
		return (request, response, authentication) -> {
			// OIDC 경로(scope에 openid 추가) 등으로 다른 principal이 오면 500 대신 실패 계약으로 보낸다.
			if (!(authentication.getPrincipal() instanceof SocialOAuth2User socialUser)) {
				response.sendRedirect(withSocialError(failureRedirect, "social_login_failed"));
				return;
			}
			if (socialUser.decision() instanceof PendingAccount pending) {
				request.getSession(true).setAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE,
						pending.registration());
				SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
				SecurityContextHolder.setContext(emptyContext);
				repository.saveContext(emptyContext, request, response);
				response.sendRedirect(signupRedirect);
				return;
			}

			ExistingAccount existing = (ExistingAccount) socialUser.decision();
			request.getSession(true).removeAttribute(PENDING_SOCIAL_REGISTRATION_SESSION_ATTRIBUTE);
			authenticatedSessionService.install(existing.authentication(), request, response);
			response.sendRedirect(successRedirect);
		};
	}

	@Bean
	AuthenticationFailureHandler socialLoginFailureHandler(
			@Value("${app.oauth2.failure-redirect:/login}") String failureRedirect) {
		return (request, response, exception) -> {
			String errorCode = exception instanceof OAuth2AuthenticationException oauth2Exception
					? oauth2Exception.getError().getErrorCode()
					: "social_login_failed";
			response.sendRedirect(withSocialError(failureRedirect, errorCode));
		};
	}

	private static String withSocialError(String redirect, String errorCode) {
		return redirect + (redirect.contains("?") ? "&" : "?")
				+ "socialError=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
	}

	private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
		private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
		private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response,
				Supplier<CsrfToken> csrfToken) {
			xor.handle(request, response, csrfToken);
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			String headerValue = request.getHeader(csrfToken.getHeaderName());
			return (StringUtils.hasText(headerValue) ? plain : xor)
					.resolveCsrfTokenValue(request, csrfToken);
		}
	}
}
