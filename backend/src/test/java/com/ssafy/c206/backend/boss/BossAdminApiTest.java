package com.ssafy.c206.backend.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseTemplateRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.AdminAccountSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:bossadmin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossAdminApiTest {

	private static final String SUFFIX = String.valueOf(System.nanoTime());
	private static final String ADMIN_EMAIL = "boss-admin-" + SUFFIX + "@api.test";
	private static final String ADMIN_PASSWORD = "admin-password-123";

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private UserRepository userRepository;
	@Autowired private ImageAssetRepository imageAssetRepository;
	@Autowired private BossPhaseTemplateRepository templateRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ObjectMapper objectMapper;

	private MockMvc mockMvc;
	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
		new AdminAccountSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD, "boss-admin-" + SUFFIX)
				.run(null);
		adminSession = loginAsAdmin();
	}

	@Test
	void updatesBossMetadataMoodsTemplatesAndStatusWithOptimisticGuard() throws Exception {
		long coverId = uploadBookCover();
		long initialPhaseId = uploadImage();
		MvcResult created = mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
				.contentType("application/json")
				.content(validBossJson("editable-boss-" + System.nanoTime(), coverId, initialPhaseId)))
			.andExpect(status().isCreated()).andReturn();
		JsonNode boss = objectMapper.readTree(created.getResponse().getContentAsByteArray());
		long bossId = boss.get("id").asLong();
		String updatedAt = boss.get("updatedAt").asText();

		String updatedName = "수정된-보스-" + System.nanoTime();
		MvcResult updated = mockMvc.perform(patch("/api/admin/bosses/{bossId}", bossId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("""
						{"name":"%s","description":"수정된 설명",
						 "moods":[{"code":"MYSTERY","role":"THEME","weight":1.5}],
						 "expectedUpdatedAt":"%s"}
						""".formatted(updatedName, updatedAt)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(updatedName))
			.andExpect(jsonPath("$.moods[0].code").value("MYSTERY"))
			.andReturn();
		String afterMetadata = objectMapper.readTree(updated.getResponse().getContentAsByteArray())
				.get("updatedAt").asText();

		long replacementPhaseId = uploadImage();
		MvcResult templatesUpdated = mockMvc.perform(put("/api/admin/bosses/{bossId}/phase-templates", bossId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("""
						{"phaseTemplates":[{"maxHp":2300,"hardMinScore":30,"recommendedScore":50,
						 "damageMultiplier":1.2,"imageAssetId":%d}],"expectedUpdatedAt":"%s"}
						""".formatted(replacementPhaseId,
								Instant.parse(afterMetadata).plusNanos(999))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phaseTemplates[0].maxHp").value(2300))
			.andReturn();
		String afterTemplates = objectMapper.readTree(templatesUpdated.getResponse().getContentAsByteArray())
				.get("updatedAt").asText();

		mockMvc.perform(patch("/api/admin/bosses/{bossId}/status", bossId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"status\":\"INACTIVE\",\"expectedUpdatedAt\":\"" + afterTemplates + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INACTIVE"));

		mockMvc.perform(patch("/api/admin/bosses/{bossId}", bossId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("""
						{"name":"stale","description":"stale",
						 "moods":[{"code":"JOY","role":"THEME","weight":1}],
						 "expectedUpdatedAt":"%s"}
						""".formatted(updatedAt)))
			.andExpect(status().isConflict());

		mockMvc.perform(get("/api/admin/bosses").session(adminSession)
				.param("query", updatedName).param("status", "INACTIVE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(bossId));
	}

	@Test
	void rejectsStructurallyInvalidBossUpdatesWithFieldErrors() throws Exception {
		mockMvc.perform(patch("/api/admin/bosses/{bossId}", 1).with(csrf()).session(adminSession)
				.contentType("application/json")
				.content("{\"name\":\" \",\"description\":\" \",\"moods\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("moods")))
			.andExpect(jsonPath("$.fieldErrors[*].field", hasItem("expectedUpdatedAt")));
	}

	@Test
	void treatsBossSearchWildcardsAsLiteralCharacters() throws Exception {
		long marker = System.nanoTime();
		long coverId = uploadBookCover();
		long phaseId = uploadImage();
		mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
				.contentType("application/json")
				.content(validBossJson("wildcard-%-" + marker, coverId, phaseId)))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
				.contentType("application/json")
				.content(validBossJson("wildcard-plain-" + marker, uploadBookCover(), uploadImage())))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/admin/bosses").session(adminSession).param("query", "%"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].name").value("wildcard-%-" + marker));
	}

	@Test
	void createsBossWithMoodsAndPhaseTemplatesAndListsIt() throws Exception {
		String bossName = "admin-boss-" + System.nanoTime();
		long coverImageId = uploadBookCover();
		long firstImageId = uploadImage();
		long secondImageId = uploadImage();

		MvcResult created = mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("""
							{"name":"%s","description":"created by admin API","bookCoverImageAssetId":%d,
							 "moods":[{"code":"JOY","role":"THEME","weight":1.0},
							          {"code":"MYSTERY","role":"WEAKNESS","weight":1.25}],
							 "phaseTemplates":[
							   {"maxHp":1700,"hardMinScore":20.00,"recommendedScore":40.00,"damageMultiplier":1.0,"imageAssetId":%d},
							   {"maxHp":2500,"hardMinScore":40.00,"recommendedScore":60.00,"damageMultiplier":1.25,"imageAssetId":%d}]}
							""".formatted(bossName, coverImageId, firstImageId, secondImageId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value(bossName))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.bookCoverImageAssetId").value(coverImageId))
				.andExpect(jsonPath("$.bookCoverImageUrl").value(startsWith("/images/book-covers/")))
				.andExpect(jsonPath("$.moods.length()").value(2))
				.andExpect(jsonPath("$.moods[*].code", hasItem("JOY")))
				.andExpect(jsonPath("$.phaseTemplates[0].phaseNo").value(1))
				.andExpect(jsonPath("$.phaseTemplates[0].imageAssetId").value(firstImageId))
				.andExpect(jsonPath("$.phaseTemplates[0].imageUrl").value(startsWith("/images/boss-phases/")))
				.andExpect(jsonPath("$.phaseTemplates[1].phaseNo").value(2))
				.andExpect(jsonPath("$.createdAt").exists())
				.andReturn();

		long bossId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();
		assertThat(imageAssetRepository.findById(firstImageId).orElseThrow().getStatus())
				.isEqualTo(ImageAssetStatus.ACTIVE);
		assertThat(imageAssetRepository.findById(secondImageId).orElseThrow().getStatus())
				.isEqualTo(ImageAssetStatus.ACTIVE);
		assertThat(imageAssetRepository.findById(coverImageId).orElseThrow().getStatus())
				.isEqualTo(ImageAssetStatus.ACTIVE);
		assertThat(templateRepository.findByBossIdOrderByPhaseNo(bossId))
				.extracting(template -> template.getPhaseNo()).containsExactly(1, 2);

		mockMvc.perform(get("/api/admin/bosses/{bossId}/phase-templates", bossId).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bossId").value(bossId))
				.andExpect(jsonPath("$.phases[0].phaseNo").value(1))
				.andExpect(jsonPath("$.phases[0].imageUrl").exists());

		mockMvc.perform(get("/api/admin/bosses").session(adminSession).param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name", hasItem(bossName)));
	}

	@Test
	void requiresBookCoverAndCanReplaceBossDefaultWithoutOverwritingAsset() throws Exception {
		long phaseImageId = uploadImage();
		mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content(validBossJson("missing-cover-" + System.nanoTime(), null, phaseImageId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		long originalCoverId = uploadBookCover();
		MvcResult created = mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content(validBossJson("replace-cover-" + System.nanoTime(), originalCoverId, phaseImageId)))
				.andExpect(status().isCreated()).andReturn();
		long bossId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();
		long replacementCoverId = uploadBookCover();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
					"/api/admin/bosses/{bossId}/book-cover", bossId).with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"imageAssetId\":" + replacementCoverId + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookCoverImageAssetId").value(replacementCoverId));
		assertThat(imageAssetRepository.findById(originalCoverId)).isPresent();
	}

	@Test
	void rejectsInvalidInitialPhaseTemplates() throws Exception {
		long imageId = uploadImage();
		assertPhaseValidation("[]");
		assertPhaseValidation("[{\"maxHp\":100,\"imageAssetId\":null}]");
		assertPhaseValidation("[{\"maxHp\":0,\"imageAssetId\":" + imageId + "}]");
		assertPhaseValidation("[{\"maxHp\":100,\"hardMinScore\":20,\"recommendedScore\":10,\"imageAssetId\":" + imageId + "}]");
		assertPhaseValidation("[{\"maxHp\":100,\"imageAssetId\":" + imageId + "},{\"maxHp\":100,\"imageAssetId\":" + imageId + "}]");
		assertPhaseValidation("[{\"maxHp\":100,\"imageAssetId\":999999999}]");
	}

	@Test
	void rejectsTemporaryImageOwnedByAnotherUser() throws Exception {
		User other = userRepository.save(User.createFixture("other-" + System.nanoTime() + "@api.test", "hash",
				"other-" + System.nanoTime()));
		ImageAsset otherAsset = imageAssetRepository.save(ImageAsset.temporary(
				"boss-phases/other-" + System.nanoTime() + ".png", "image/png", 10,
				ImagePurpose.BOSS_PHASE, other, Instant.now()));
		assertPhaseValidation("[{\"maxHp\":100,\"imageAssetId\":" + otherAsset.getId() + "}]");
	}

	@Test
	void rejectsUnknownMoodCode() throws Exception {
		assertCreateValidation("[{\"code\":\"NOT_A_MOOD\",\"role\":\"THEME\",\"weight\":1.0}]");
	}

	@Test
	void rejectsDuplicateMoodEntry() throws Exception {
		assertCreateValidation("[{\"code\":\"JOY\",\"role\":\"THEME\",\"weight\":1.0},{\"code\":\"JOY\",\"role\":\"THEME\",\"weight\":2.0}]");
	}

	@Test
	void rejectsMissingMoodsAndNonPositiveWeight() throws Exception {
		assertCreateValidation("[]");
		assertCreateValidation("[{\"code\":\"JOY\",\"role\":\"THEME\",\"weight\":0}]");
	}

	@Test
	void rejectsOverlongBossName() throws Exception {
		mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"name\":\"" + "가".repeat(151)
							+ "\",\"description\":\"too long\",\"moods\":[{\"code\":\"JOY\",\"role\":\"THEME\",\"weight\":1.0}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsInvalidPagingParameters() throws Exception {
		mockMvc.perform(get("/api/admin/bosses").session(adminSession).param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private MockHttpSession loginAsAdmin() throws Exception {
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).as("admin login must create a session").isNotNull();
		return session;
	}

	private long uploadImage() throws Exception {
		return uploadImage("/api/admin/boss-images", "phase.png");
	}

	@Test
	void rejectsMissingBookCoverImageAssetIdBeforeLookingUpBoss() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
					"/api/admin/bosses/{bossId}/book-cover", Long.MAX_VALUE).with(csrf()).session(adminSession)
					.contentType("application/json").content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void bossBookCoverChangeRequiresAdminAndCsrf() throws Exception {
		String body = "{\"imageAssetId\":1}";

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
					"/api/admin/bosses/1/book-cover").with(csrf())
					.contentType("application/json").content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
					"/api/admin/bosses/1/book-cover").with(user("member").roles("USER")).with(csrf())
					.contentType("application/json").content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
					"/api/admin/bosses/1/book-cover").session(adminSession)
					.contentType("application/json").content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsBookCoverAssetUsedAsPhaseImage() throws Exception {
		long bookCoverId = uploadBookCover();
		assertPhaseValidation("[{\"maxHp\":100,\"imageAssetId\":" + bookCoverId + "}]");
	}

	private long uploadBookCover() throws Exception {
		return uploadImage("/api/admin/book-cover-images", "cover.png");
	}

	private long uploadImage(String endpoint, String filename) throws Exception {
		byte[] png = Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
		MockMultipartFile image = new MockMultipartFile("image", filename, "image/png", png);
		MvcResult result = mockMvc.perform(multipart(endpoint).file(image).with(csrf()).session(adminSession))
				.andExpect(status().isCreated()).andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
		return response.get("imageAssetId").asLong();
	}

	private String validBossJson(String name, Long coverImageId, long phaseImageId) {
		String cover = coverImageId == null ? "null" : coverImageId.toString();
		return "{\"name\":\"" + name + "\",\"description\":\"validation\","
				+ "\"bookCoverImageAssetId\":" + cover + ","
				+ "\"moods\":[{\"code\":\"JOY\",\"role\":\"THEME\",\"weight\":1.0}],"
				+ "\"phaseTemplates\":[{\"maxHp\":100,\"imageAssetId\":" + phaseImageId + "}]}";
	}

	private void assertPhaseValidation(String phaseTemplatesJson) throws Exception {
		long coverImageId = uploadBookCover();
		mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("""
							{"name":"phase-validation-%s","description":"validation","bookCoverImageAssetId":%d,
							 "moods":[{"code":"JOY","role":"THEME","weight":1.0}],
							 "phaseTemplates":%s}
							""".formatted(System.nanoTime(), coverImageId, phaseTemplatesJson)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private void assertCreateValidation(String moodsJson) throws Exception {
		long coverImageId = uploadBookCover();
		long phaseImageId = uploadImage();
		mockMvc.perform(post("/api/admin/bosses").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"name\":\"invalid-boss\",\"description\":\"validation\","
							+ "\"bookCoverImageAssetId\":" + coverImageId + ",\"moods\":" + moodsJson + ","
							+ "\"phaseTemplates\":[{\"maxHp\":100,\"imageAssetId\":" + phaseImageId + "}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}
}
