package com.ssafy.c206.backend.common.config;

import com.ssafy.c206.backend.common.api.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration
public class OpenApiConfiguration {

	private static final String API_ERROR_SCHEMA = "ApiError";

	@Bean
	OpenAPI openApi() {
		Components components = new Components()
				.addSecuritySchemes("sessionAuth", new SecurityScheme()
						.type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")
						.description("로그인 세션 쿠키입니다."))
				.addSecuritySchemes("csrfToken", new SecurityScheme()
						.type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN")
						.description("상태를 변경하는 요청에 필요한 CSRF 토큰입니다."))
				.addSchemas("FieldError", fieldErrorSchema())
				.addSchemas(API_ERROR_SCHEMA, apiErrorSchema());
		addErrorResponses(components);

		return new OpenAPI()
				.info(new Info().title("잇담 API").version("v1")
						.description("잇담 서비스의 REST API입니다. GET 읽기 요청에는 JSESSIONID 세션 쿠키만 전송하세요. 회원 가입과 로그인은 익명 요청이지만 POST 요청이므로 CSRF 토큰이 필요합니다. 인증된 상태 변경 요청에는 JSESSIONID와 X-XSRF-TOKEN을 함께 전송하세요. 브라우저에서는 먼저 GET /api/v1/csrf를 credentials/cookie를 유지한 채 호출하고, 받은 토큰을 POST·PATCH·DELETE 요청의 X-XSRF-TOKEN 헤더에만 넣으세요. 로그인 후에는 새 CSRF 토큰을 다시 발급받으세요."))
				.components(components);
	}

	@Bean
	OpenApiCustomizer apiPathsOnlyCustomizer() {
		// 공개 API(/api/v1)와 관리자 운영 API(/api/admin)만 문서에 노출한다.
		// Swagger UI 접근은 nginx Basic Auth로, 관리자 API 호출은 ADMIN 세션으로 보호된다.
		return openApi -> openApi.getPaths().keySet()
				.removeIf(path -> !path.equals("/api/v1") && !path.startsWith("/api/v1/")
						&& !path.equals("/api/admin") && !path.startsWith("/api/admin/"));
	}

	@Bean
	OpenApiCustomizer sessionAndCsrfSecurityCustomizer() {
		return openApi -> {
			requireSessionAndCsrf(openApi, "/api/v1/auth/sessions/current", PathItem.HttpMethod.POST);
			requireSessionAndCsrf(openApi, "/api/v1/sentences", PathItem.HttpMethod.POST);
			requireSessionAndCsrf(openApi, "/api/v1/sentences/{sentenceId}/versions", PathItem.HttpMethod.POST);
			requireSessionAndCsrf(openApi, "/api/v1/sentence-versions/{versionId}", PathItem.HttpMethod.PATCH);
			requireSessionAndCsrf(openApi, "/api/v1/sentences/{sentenceId}", PathItem.HttpMethod.DELETE);
			requireSessionAndCsrf(openApi, "/api/v1/sentence-versions/{versionId}/evaluations", PathItem.HttpMethod.POST);
			requireSessionAndCsrf(openApi, "/api/v1/boss-runs/{bossRunId}/submissions", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/bosses", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/boss-runs", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/boss-runs/{runId}", PathItem.HttpMethod.PATCH);
				requireSessionAndCsrf(openApi, "/api/admin/boss-runs/{runId}/activate", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/boss-runs/{runId}/cancel", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/boss-runs/{runId}/end", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", PathItem.HttpMethod.PUT);
				requireSessionAndCsrf(openApi, "/api/admin/boss-images", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/book-cover-images", PathItem.HttpMethod.POST);
				requireSessionAndCsrf(openApi, "/api/admin/bosses/{bossId}/book-cover", PathItem.HttpMethod.PUT);
				requireSessionAndCsrf(openApi, "/api/admin/bosses/{bossId}", PathItem.HttpMethod.PATCH);
				requireSessionAndCsrf(openApi, "/api/admin/bosses/{bossId}/phase-templates", PathItem.HttpMethod.PUT);
				requireSessionAndCsrf(openApi, "/api/admin/bosses/{bossId}/status", PathItem.HttpMethod.PATCH);
				requireSessionAndCsrf(openApi, "/api/admin/books/{bookId}/cover", PathItem.HttpMethod.PUT);
				requireSessionAndCsrf(openApi, "/api/admin/books/{bookId}/visibility", PathItem.HttpMethod.PATCH);
		};
	}

	private static void requireSessionAndCsrf(OpenAPI openApi, String path, PathItem.HttpMethod method) {
		PathItem pathItem = openApi.getPaths().get(path);
		Operation operation = pathItem == null ? null : pathItem.readOperationsMap().get(method);
		if (operation != null) {
			operation.setSecurity(new ArrayList<>(List.of(
					new SecurityRequirement().addList("sessionAuth").addList("csrfToken"))));
		}
	}

	private static Schema<?> apiErrorSchema() {
		return new ObjectSchema()
				.description("API 요청 처리 실패를 표현하는 공통 오류 응답입니다.")
				.required(List.of("occurredAt", "code", "message", "fieldErrors"))
				.addProperty("occurredAt", new DateTimeSchema().description("오류가 발생한 시각입니다. ISO-8601 UTC date-time 형식입니다.").example("2026-07-27T02:23:45Z"))
				.addProperty("code", new StringSchema().description("프로그램이 처리할 오류 코드입니다.").example("VALIDATION_ERROR"))
				.addProperty("message", new StringSchema().description("사용자에게 표시할 오류 메시지입니다.").example("요청 값을 확인한 뒤 다시 시도하세요."))
				.addProperty("fieldErrors", new ArraySchema().description("입력값 검증에 실패한 필드별 오류 목록입니다.")
						.items(new Schema<>().$ref("#/components/schemas/FieldError")));
	}

	private static Schema<?> fieldErrorSchema() {
		return new ObjectSchema()
				.description("입력값 검증에 실패한 필드와 사유입니다.")
				.required(List.of("field", "reason"))
				.addProperty("field", new StringSchema().description("검증에 실패한 요청 필드 이름입니다.").example("content"))
				.addProperty("reason", new StringSchema().description("해당 필드가 규칙을 만족하지 못한 이유입니다.").example("content must not be blank"));
	}

	private static void addErrorResponses(Components components) {
		addErrorResponse(components, "BadRequest", ErrorCode.VALIDATION_ERROR, "요청 값을 확인한 뒤 다시 시도하세요.");
		addErrorResponse(components, "Unauthorized", ErrorCode.UNAUTHORIZED, "로그인 후 다시 시도하세요.");
		addErrorResponse(components, "Forbidden", ErrorCode.FORBIDDEN, "CSRF 토큰이 없거나 잘못된 경우와 권한이 없는 경우에는 토큰과 권한을 확인하세요. 비활성 계정인 경우 계정을 활성화한 뒤 다시 시도하세요.");
		addErrorResponse(components, "NotFound", ErrorCode.NOT_FOUND, "요청한 리소스 식별자를 확인하세요.");
		addErrorResponse(components, "Conflict", ErrorCode.CONFLICT, "최신 상태를 확인한 뒤 다시 시도하세요.");
		addErrorResponse(components, "UnprocessableEntity", ErrorCode.POLICY_REJECTED, "안전 정책을 충족하도록 내용을 수정하세요.");
		addErrorResponse(components, "TooManyRequests", ErrorCode.RATE_LIMITED, "일일 사용량이 초기화된 뒤 다시 시도하세요.");
		addErrorResponse(components, "ServiceUnavailable", ErrorCode.COPYRIGHT_AGREEMENT_UNAVAILABLE,
				"현재 동의서를 조회할 수 있을 때 다시 시도하세요.");
		addErrorResponse(components, "InternalServerError", ErrorCode.INTERNAL_ERROR, "잠시 후 다시 시도하고 문제가 계속되면 문의하세요.");
	}

	private static void addErrorResponse(Components components, String name, ErrorCode errorCode,
			String recoveryInstruction) {
		components.addResponses(name, new ApiResponse()
				.description(errorCode.name() + ": " + recoveryInstruction)
				.content(new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE,
						new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>().$ref("#/components/schemas/" + API_ERROR_SCHEMA)))));
	}
}
