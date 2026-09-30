package com.ssafy.c206.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class OpenApiDocumentationTest {

	@Autowired private WebApplicationContext context;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private OpenApiCustomizer apiPathsOnlyCustomizer;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
	}

	@Test
	void anonymousUsersCanReadDocumentationAndTheContractExcludesNonApis() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/users']").exists())
				.andExpect(jsonPath("$.components.schemas.ApiError").exists())
				.andExpect(jsonPath("$.paths['/test']").doesNotExist())
				.andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
	}

	@Test
	void apiPathFilterExcludesPathsThatOnlyShareTheApiV1Prefix() {
		OpenAPI openApi = new OpenAPI().paths(new Paths()
				.addPathItem("/api/v1/users", new PathItem())
				.addPathItem("/api/v10/users", new PathItem())
				.addPathItem("/api/admin/bosses", new PathItem())
				.addPathItem("/api/administrator/tools", new PathItem()));

		apiPathsOnlyCustomizer.customise(openApi);

		assertThat(openApi.getPaths())
				.containsKeys("/api/v1/users", "/api/admin/bosses")
				.doesNotContainKeys("/api/v10/users", "/api/administrator/tools");
	}

	@Test
	void contractDescribesCsrfProtectedSentenceAndEvaluationCommands() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/csrf'].get.tags[0]").value("인증 및 사용자"))
				.andExpect(jsonPath("$.paths['/api/v1/sentences'].post.requestBody").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences'].post.responses['400']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/current'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/current'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/current'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/sentences'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}/versions'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}/versions'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}/versions'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentence-versions/{versionId}'].patch.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/sentence-versions/{versionId}'].patch.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentence-versions/{versionId}'].patch.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}'].delete.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}'].delete.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}'].delete.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentence-versions/{versionId}/evaluations'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/sentence-versions/{versionId}/evaluations'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentence-versions/{versionId}/evaluations'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentence-versions/{versionId}/evaluations'].post.responses['429']").exists())
				.andExpect(jsonPath("$.components.schemas.SentenceRequest.properties.content.example").exists())
				.andExpect(jsonPath("$.components.schemas.Scores.properties.completeness.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.completeness.maximum").value(100))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.originality.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.originality.maximum").value(100))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.expression.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.expression.maximum").value(100))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.imagery.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.imagery.maximum").value(100))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.emotionalImpact.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.Scores.properties.emotionalImpact.maximum").value(100))
				.andExpect(jsonPath("$.components.schemas.Mood.properties.code.enum").value(Matchers.containsInAnyOrder(
						"JOY", "HOPE", "LOVE", "CALM", "NOSTALGIA", "SADNESS", "LONELINESS", "ANGER", "TENSION", "MYSTERY")))
				.andExpect(jsonPath("$.components.schemas.Mood.properties.strength.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.Mood.properties.strength.maximum").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences/{sentenceVersionId}'].get.responses['200'].content['*/*'].schema.$ref")
						.value("#/components/schemas/ArchiveSentenceResponse"))
				.andExpect(jsonPath("$.components.schemas.ArchiveSentenceResponse.properties.moods.items.$ref")
						.value("#/components/schemas/Mood"))
				.andExpect(jsonPath("$.components.schemas.ArchiveSentenceResponse.properties.evaluationStatus.$ref")
						.value("#/components/schemas/EvaluationStatus"))
				.andExpect(jsonPath("$.components.schemas.ArchiveSentenceResponse.properties.createdAt.format")
						.value("date-time"))
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences/unevaluated'].get.security[0].sessionAuth")
						.exists())
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences/unevaluated'].get.parameters[0].schema.default")
						.value(0))
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences/unevaluated'].get.parameters[1].schema.default")
						.value(20))
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences/unevaluated'].get.parameters[1].schema.maximum")
						.value(100))
				.andExpect(jsonPath("$.components.schemas.UnevaluatedArchiveSentenceResponse.properties.evaluationStatus.$ref")
						.value("#/components/schemas/EvaluationStatus"))
				.andExpect(jsonPath("$.components.schemas.EvaluationStatus.enum")
						.value(Matchers.containsInAnyOrder(
								"DRAFT", "EVALUATING", "EVALUATED", "REJECTED", "EVALUATION_FAILED")))
				.andExpect(jsonPath("$.components.schemas.UnevaluatedArchiveSentenceResponse.properties.createdAt.format")
						.value("date-time"))
				.andExpect(jsonPath("$.components.schemas.Mood.properties.code.enum").value(Matchers.containsInAnyOrder(
						"JOY", "HOPE", "LOVE", "CALM", "NOSTALGIA", "SADNESS", "LONELINESS", "ANGER", "TENSION", "MYSTERY")))
				.andExpect(jsonPath("$.components.schemas.Mood.properties.strength.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.Mood.properties.strength.maximum").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/sentences/{sentenceId}'].get.security[0].csrfToken").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/sentences'].post.parameters").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/users'].post.security[0].sessionAuth").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].post.security[0].sessionAuth").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/csrf'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences'].get.parameters[0].schema.default").value(0))
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences'].get.parameters[1].schema.default").value(20))
				.andExpect(jsonPath("$.paths['/api/v1/me/sentences'].get.parameters[1].schema.maximum").value(100));
	}

	@Test
	void sentenceControllerUsesDedicatedOpenApiContractInterface() throws Exception {
		Class<?> contract = Class.forName("com.ssafy.c206.backend.sentence.api.SentenceApi");
		Class<?> controller = Class.forName("com.ssafy.c206.backend.sentence.api.SentenceController");

		assertThat(contract.isAssignableFrom(controller)).isTrue();
	}

	@Test
	void apiControllerImplementationsDoNotRedeclareBeanValidationParameterConstraints() throws Exception {
		List<String> controllerClassNames = List.of(
				"com.ssafy.c206.backend.book.api.AiJobController",
				"com.ssafy.c206.backend.book.api.BookGenerationController",
				"com.ssafy.c206.backend.book.api.LibraryController",
				"com.ssafy.c206.backend.book.api.MyLibraryController",
				"com.ssafy.c206.backend.boss.api.BossAdminController",
				"com.ssafy.c206.backend.boss.api.BossController",
				"com.ssafy.c206.backend.boss.api.BossRunAdminController",
				"com.ssafy.c206.backend.boss.api.MyBossRunController",
				"com.ssafy.c206.backend.common.api.ApiRootController",
				"com.ssafy.c206.backend.dailyword.api.DailyWordController",
				"com.ssafy.c206.backend.sentence.api.SentenceController",
				"com.ssafy.c206.backend.sentence.archive.api.ArchiveController",
				"com.ssafy.c206.backend.sentence.evaluation.api.SentenceEvaluationController",
				"com.ssafy.c206.backend.user.api.AuthController",
				"com.ssafy.c206.backend.user.agreement.api.CopyrightAgreementController",
				"com.ssafy.c206.backend.user.api.CsrfController");

		for (String controllerClassName : controllerClassNames) {
			Class<?> controllerType = Class.forName(controllerClassName);
			for (var method : controllerType.getDeclaredMethods()) {
				var beanValidationAnnotations = Arrays.stream(method.getParameterAnnotations())
						.flatMap(Arrays::stream)
						.filter(annotation -> annotation.annotationType().getPackageName().startsWith("jakarta.validation"));

				assertThat(beanValidationAnnotations)
						.as("%s#%s", controllerType.getSimpleName(), method.getName())
						.isEmpty();
			}
		}
	}

	@Test
	void contractDescribesBossSubmissionAndLibraryResponses() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/daily-words/today'].get.tags[0]").value("오늘의 단어"))
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/submissions'].post.responses['422']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/submissions'].post.responses['429']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/library/books'].get.parameters[0].schema.minimum").value(0))
				.andExpect(jsonPath("$.components.schemas.BossSubmissionRequest.properties.submissionConsentAccepted.example").value(true));
	}

	@Test
	void contractDescribesAuthenticatedUserActivityHistory() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/me/boss-runs'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/me/library/books'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.components.schemas.MyBossRunResponse.properties.lastSubmittedAt.format")
						.value("date-time"))
				.andExpect(jsonPath("$.components.schemas.MyContributedBookResponse.properties.myIncludedContentCount.minimum")
						.value(1))
				.andExpect(jsonPath("$.components.schemas.BossRunResponse.properties.mySubmissionCount.description")
						.value(Matchers.containsString("오늘")));
	}

	@Test
	void contractMarksAnonymousReadEndpointsAsPublic() throws Exception {
		// 비회원 열람 공개(ISSUE-115): 공개 GET에는 security 요구가 없어야 하고,
		// 개인 데이터 하위 경로는 계속 세션 인증을 문서화해야 한다.
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/copyright-agreements/current'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/daily-words/preview'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/library/books'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/library/books/{bookId}'].get.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/daily-words/preview'].get.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/daily-words/today'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/eligible-sentences'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/submissions/me'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.components.schemas.DailyWordPreviewResponse.properties.serviceDate.format").value("date"))
				.andExpect(jsonPath("$.components.schemas.DailyWordPreviewResponse.properties.dailyWordSetId").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.CopyrightAgreementResponse.properties.effectiveAt.format")
						.value("date-time"))
				.andExpect(jsonPath("$.components.schemas.SignupRequest.required")
						.value(Matchers.hasItems("copyrightAgreementId", "copyrightAgreed")))
				.andExpect(jsonPath("$.paths['/api/v1/users'].post.description")
						.value(Matchers.containsString("COPYRIGHT_AGREEMENT_MISMATCH")))
				.andExpect(jsonPath("$.paths['/api/v1/users'].post.responses['503'].$ref")
						.value("#/components/responses/ServiceUnavailable"))
				.andExpect(jsonPath("$.components.responses.ServiceUnavailable.description")
						.value(Matchers.containsString("COPYRIGHT_AGREEMENT_UNAVAILABLE")));
	}

	@Test
	void contractMakesBossSubmissionSecurityAndRecoveryExplicit() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/submissions'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/submissions'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/submissions'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/eligible-sentences'].get.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/boss-runs/{bossRunId}/submissions/me'].get.responses['404']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/daily-words/today'].get.description").value(Matchers.containsString("JSESSIONID")))
				.andExpect(jsonPath("$.components.schemas.BossSubmissionSummary.properties.submittedAt.format").value("date-time"))
				// 본문이 목록에 함께 담겨야 화면이 항목마다 상세를 다시 부르지 않는다.
				.andExpect(jsonPath("$.components.schemas.BossSubmissionSummary.properties.content.example").exists())
				.andExpect(jsonPath("$.components.schemas.BossSubmissionSummary.properties.finalDamage.example").value(57))
				.andExpect(jsonPath("$.components.schemas.BookGenerationStatusResponse.properties.jobStatus.description")
						.value(Matchers.containsString("PENDING")));
	}

	@Test
	void contractExposesAdminOperationsWithSessionAndCsrfSecurity() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/admin/boss-images'].post.requestBody.content['multipart/form-data']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-images'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-images'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/admin/bosses'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses'].get.security[0].csrfToken").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs'].post.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs'].post.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses'].get.parameters[?(@.name == 'size')].schema.maximum").value(100))
				.andExpect(jsonPath("$.paths['/api/admin/bosses/{bossId}'].patch.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses/{bossId}'].patch.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses/{bossId}'].patch.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses/{bossId}/phase-templates'].put.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses/{bossId}/phase-templates'].put.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses/{bossId}/status'].patch.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/bosses/{bossId}/status'].patch.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs'].get.parameters[?(@.name == 'size')].schema.maximum").value(100))
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}'].patch.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}'].patch.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/activate'].post.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/cancel'].post.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/end'].post.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/phases/{phaseNo}/image'].put.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/phases/{phaseNo}/image'].put.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/phases/{phaseNo}/image'].put.responses['400']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/phases/{phaseNo}/image'].put.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/phases/{phaseNo}/image'].put.responses['403']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/phases/{phaseNo}/image'].put.responses['404']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs/{runId}/phases/{phaseNo}/image'].put.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books'].get.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books'].get.security[0].csrfToken").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/admin/books'].get.parameters[?(@.name == 'size')].schema.maximum").value(100))
				.andExpect(jsonPath("$.paths['/api/admin/books/{bookId}/visibility'].patch.security[0].sessionAuth").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books/{bookId}/visibility'].patch.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books/{bookId}/visibility'].patch.responses['400']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books/{bookId}/visibility'].patch.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books/{bookId}/visibility'].patch.responses['403']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books/{bookId}/visibility'].patch.responses['404']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/books/{bookId}/visibility'].patch.responses['409']").exists())
				.andExpect(jsonPath("$.components.schemas.AdminBossRunCreateRequest.properties.phases.minItems").value(1))
				.andExpect(jsonPath("$.components.schemas.AdminBossRunUpdateRequest.properties.phases.minItems").value(1))
				.andExpect(jsonPath("$.components.schemas.AdminBossCreateRequest.properties.name.maxLength").value(150))
				.andExpect(jsonPath("$.paths['/api/admin/bosses'].post.tags[0]").value("관리자 - 보스 원형"))
				.andExpect(jsonPath("$.paths['/api/admin/boss-runs'].post.tags[0]").value("관리자 - 보스런 회차"));
	}

	@Test
	void contractDescribesReusableErrorsAndInactiveAccountRecovery() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.components.schemas.ApiError.required").value(Matchers.containsInAnyOrder(
						"occurredAt", "code", "message", "fieldErrors")))
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.occurredAt.format").value("date-time"))
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.occurredAt.description")
						.value(Matchers.containsString("ISO-8601")))
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.code.example").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.fieldErrors.items.$ref")
						.value("#/components/schemas/FieldError"))
				.andExpect(jsonPath("$.components.schemas.FieldError.required").value(Matchers.containsInAnyOrder("field", "reason")))
				.andExpect(jsonPath("$.components.schemas.FieldError.properties.field.description")
						.value(Matchers.containsString("필드")))
				.andExpect(jsonPath("$.components.responses.Forbidden.content.application/json.schema.$ref")
						.value("#/components/schemas/ApiError"))
				.andExpect(jsonPath("$.components.responses.Forbidden.description")
						.value(Matchers.containsString("비활성")))
				.andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses['403'].$ref")
						.value("#/components/responses/Forbidden"))
				.andExpect(jsonPath("$.paths['/api/v1/users'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].post.security[0].csrfToken").exists())
				.andExpect(jsonPath("$.info.description").value(Matchers.allOf(
						Matchers.containsString("GET"),
						Matchers.containsString("/api/v1/csrf"),
						Matchers.containsString("X-XSRF-TOKEN"),
						Matchers.containsString("로그인 후"))));
	}
}
