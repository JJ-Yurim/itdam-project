package com.ssafy.c206.backend.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossPhaseTemplate;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.image.service.ImageAssetCleanupService;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseTemplateRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.AdminAccountSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:bossrunadmin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossRunAdminApiTest {

	private static final String SUFFIX = String.valueOf(System.nanoTime());
	private static final String ADMIN_EMAIL = "run-admin-" + SUFFIX + "@api.test";
	private static final String ADMIN_PASSWORD = "admin-password-123";

	@Autowired private WebApplicationContext context;
	@Autowired private FilterChainProxy securityFilter;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private ImageAssetRepository imageRepository;
	@Autowired private BossPhaseTemplateRepository templateRepository;
	@Autowired private BossPhaseRepository phaseRepository;
	@Autowired private ImageAssetCleanupService cleanupService;
	@Autowired private ObjectMapper objectMapper;

	private MockMvc mockMvc;
	private MockHttpSession adminSession;
	private User admin;
	private Boss boss;
	private ImageAsset firstImage;
	private ImageAsset secondImage;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		new AdminAccountSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD, "run-admin-" + SUFFIX)
				.run(null);
		admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
		adminSession = login();
		boss = bossRepository.save(Boss.create("run-boss-" + System.nanoTime(), "boss", Instant.now()));
		firstImage = activeImage(admin, "first");
		secondImage = activeImage(admin, "second");
		templateRepository.save(BossPhaseTemplate.of(boss, 1, 1700, BigDecimal.ZERO, null,
				BigDecimal.ONE, firstImage));
		templateRepository.save(BossPhaseTemplate.of(boss, 2, 2000, BigDecimal.ZERO, null,
				BigDecimal.ONE, secondImage));
	}

	@Test
	void updatesScheduledSettingsAndOnlyEndsAtForActiveRun() throws Exception {
		MvcResult scheduled = createRunResult(false, firstImage.getId(), secondImage.getId());
		long runId = responseId(scheduled);
		Instant changedEnd = future().plusSeconds(600);
		MvcResult updated = mockMvc.perform(patch("/api/admin/boss-runs/{runId}", runId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("""
						{"bossId":%d,"endsAt":"%s","phases":[
						 {"maxHp":3100,"hardMinScore":15,"recommendedScore":35,"damageMultiplier":1.1,"imageAssetId":%d}],
						 "expectedUpdatedAt":"%s"}
						""".formatted(boss.getId(), changedEnd, firstImage.getId(), responseUpdatedAt(scheduled))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.endsAt").value(changedEnd.toString()))
			.andExpect(jsonPath("$.phases.length()").value(1))
			.andExpect(jsonPath("$.phases[0].maxHp").value(3100))
			.andReturn();

		MvcResult activated = mockMvc.perform(post("/api/admin/boss-runs/{runId}/activate", runId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"expectedUpdatedAt\":\"" + responseUpdatedAt(updated) + "\"}"))
			.andExpect(status().isOk()).andReturn();
		Instant activeEnd = changedEnd.plusSeconds(300);
		MvcResult activeUpdated = mockMvc.perform(patch("/api/admin/boss-runs/{runId}", runId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"endsAt\":\"" + activeEnd + "\",\"expectedUpdatedAt\":\""
						+ responseUpdatedAt(activated) + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.endsAt").value(activeEnd.toString()))
			.andReturn();

		mockMvc.perform(patch("/api/admin/boss-runs/{runId}", runId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"bossId\":" + boss.getId() + ",\"endsAt\":\"" + activeEnd.plusSeconds(60)
						+ "\",\"expectedUpdatedAt\":\"" + responseUpdatedAt(activeUpdated) + "\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void activatesCancelsAndEndsRunsWithUpdatedAtGuard() throws Exception {
		MvcResult scheduled = createRunResult(false, firstImage.getId(), secondImage.getId());
		long scheduledId = responseId(scheduled);
		String scheduledUpdatedAt = responseUpdatedAt(scheduled);

		MvcResult activated = mockMvc.perform(post("/api/admin/boss-runs/{runId}/activate", scheduledId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"expectedUpdatedAt\":\"" + scheduledUpdatedAt + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andReturn();

		MvcResult cancellable = createRunResult(false, firstImage.getId(), secondImage.getId());
		long cancellableId = responseId(cancellable);
		MvcResult cancelled = mockMvc.perform(post("/api/admin/boss-runs/{runId}/cancel", cancellableId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"expectedUpdatedAt\":\"" + responseUpdatedAt(cancellable) + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andReturn();

		mockMvc.perform(patch("/api/admin/boss-runs/{runId}", cancellableId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"bossId\":" + boss.getId() + ",\"endsAt\":\"" + future()
						+ "\",\"phases\":[],\"expectedUpdatedAt\":\"" + responseUpdatedAt(cancelled) + "\"}"))
			.andExpect(status().isConflict());

		Instant cancelledCreatedAt = responseCreatedAt(cancelled);
		mockMvc.perform(get("/api/admin/boss-runs").session(adminSession)
				.param("query", boss.getName())
				.param("status", "CANCELLED")
				.param("from", cancelledCreatedAt.minusSeconds(60).toString())
				.param("to", cancelledCreatedAt.plusSeconds(60).toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(cancellableId));

		mockMvc.perform(post("/api/admin/boss-runs/{runId}/end", scheduledId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"expectedUpdatedAt\":\"" + responseUpdatedAt(activated) + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED_UNCLEARED"));

		mockMvc.perform(post("/api/admin/boss-runs/{runId}/end", scheduledId)
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"expectedUpdatedAt\":\"" + responseUpdatedAt(activated) + "\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void filtersRunsByRaidScheduleInsteadOfRegistrationTime() throws Exception {
		Instant startsAt = Instant.now().plusSeconds(3_600);
		Instant endsAt = startsAt.plusSeconds(3_600);
		MvcResult scheduled = mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
				.contentType("application/json").content("""
						{"bossId":%d,"startsAt":"%s","endsAt":"%s","activate":false,"phases":[
						 {"maxHp":1800,"imageAssetId":%d},{"maxHp":2200,"imageAssetId":%d}]}
						""".formatted(boss.getId(), startsAt, endsAt, firstImage.getId(), secondImage.getId())))
			.andExpect(status().isCreated())
			.andReturn();

		mockMvc.perform(get("/api/admin/boss-runs").session(adminSession)
				.param("from", startsAt.minusSeconds(60).toString())
				.param("to", endsAt.plusSeconds(60).toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(responseId(scheduled))).exists());
	}

	@Test
	void rejectsExtendingAnAlreadyExpiredActiveRun() throws Exception {
		Instant createdAt = Instant.now().minusSeconds(7_200);
		BossRun expired = BossRun.schedule(boss, createdAt, createdAt.plusSeconds(3_600), createdAt);
		expired.addPhase(1_800, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		expired.addPhase(2_200, BigDecimal.ZERO, null, BigDecimal.ONE, secondImage);
		expired.activate(createdAt);
		expired = bossRunRepository.saveAndFlush(expired);

		mockMvc.perform(patch("/api/admin/boss-runs/{runId}", expired.getId())
				.with(csrf()).session(adminSession).contentType("application/json")
				.content("{\"endsAt\":\"" + future() + "\",\"expectedUpdatedAt\":\""
						+ expired.getUpdatedAt() + "\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void snapshotsImagesAndReplacesTemplatesWithoutChangingOlderRun() throws Exception {
		ImageAsset replacement = temporaryImage(admin, "replacement");
		long firstRunId = createRun(false, secondImage.getId(), replacement.getId());

		assertThat(phaseRepository.findByBossRunIdOrderByPhaseNoAsc(firstRunId))
				.extracting(phase -> phase.getImageAsset().getId())
				.containsExactly(secondImage.getId(), replacement.getId());
		assertThat(templateRepository.findByBossIdOrderByPhaseNo(boss.getId()))
				.extracting(template -> template.getImageAsset().getId())
				.containsExactly(secondImage.getId(), replacement.getId());
		assertThat(imageRepository.findById(replacement.getId()).orElseThrow().getStatus())
				.isEqualTo(ImageAssetStatus.ACTIVE);

		ImageAsset nextReplacement = temporaryImage(admin, "next");
		createRun(false, replacement.getId(), nextReplacement.getId());

		assertThat(phaseRepository.findByBossRunIdOrderByPhaseNoAsc(firstRunId))
				.extracting(phase -> phase.getImageAsset().getId())
				.containsExactly(secondImage.getId(), replacement.getId());
		assertThat(templateRepository.findByBossIdOrderByPhaseNo(boss.getId()))
				.extracting(template -> template.getImageAsset().getId())
				.containsExactly(replacement.getId(), nextReplacement.getId());
	}

	@Test
	void rejectsTemporaryImageOwnedByAnotherUser() throws Exception {
		User other = userRepository.save(User.createFixture("other-run-" + System.nanoTime() + "@test.dev", "hash",
				"other-run-" + System.nanoTime()));
		ImageAsset otherImage = temporaryImage(other, "other");
		assertRunValidation(firstImage.getId(), otherImage.getId());
	}

	@Test
	void rejectsActiveImageFromAnotherBoss() throws Exception {
		Boss otherBoss = bossRepository.save(Boss.create("other-boss-" + System.nanoTime(), "other", Instant.now()));
		ImageAsset otherImage = activeImage(admin, "other-active");
		templateRepository.save(BossPhaseTemplate.of(otherBoss, 1, 100, BigDecimal.ZERO, null,
				BigDecimal.ONE, otherImage));
		assertRunValidation(firstImage.getId(), otherImage.getId());
	}

	@Test
	void failedDuplicateActiveRunLeavesTemplatesUnchanged() throws Exception {
		createRun(true, firstImage.getId(), secondImage.getId());
		ImageAsset replacement = temporaryImage(admin, "conflict");

		mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
					.contentType("application/json").content(runBody(true, secondImage.getId(), replacement.getId())))
				.andExpect(status().isConflict());

		assertThat(templateRepository.findByBossIdOrderByPhaseNo(boss.getId()))
				.extracting(template -> template.getImageAsset().getId())
				.containsExactly(firstImage.getId(), secondImage.getId());
		assertThat(imageRepository.findById(replacement.getId()).orElseThrow().getStatus())
				.isEqualTo(ImageAssetStatus.TEMPORARY);
	}

	@Test
	void rejectsMissingImagesInvalidScoresAndUnknownBoss() throws Exception {
		mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"bossId\":" + boss.getId() + ",\"endsAt\":\"" + future()
							+ "\",\"phases\":[{\"maxHp\":100}]}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
					.contentType("application/json")
					.content("{\"bossId\":999999,\"endsAt\":\"" + future()
							+ "\",\"phases\":[{\"maxHp\":100,\"imageAssetId\":" + firstImage.getId() + "}]}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void deniesAnonymousAccess() throws Exception {
		mockMvc.perform(post("/api/admin/boss-runs").with(csrf())
					.contentType("application/json").content(runBody(false, firstImage.getId(), secondImage.getId())))
				.andExpect(status().isUnauthorized());

		MvcResult scheduled = createRunResult(false, firstImage.getId(), secondImage.getId());
		String actionBody = "{\"expectedUpdatedAt\":\"" + responseUpdatedAt(scheduled) + "\"}";
		mockMvc.perform(post("/api/admin/boss-runs/{runId}/activate", responseId(scheduled)).with(csrf())
					.contentType("application/json").content(actionBody))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/boss-runs/{runId}/activate", responseId(scheduled))
					.session(adminSession).contentType("application/json").content(actionBody))
				.andExpect(status().isForbidden());
	}

	@Test
	void changesPhaseImageForScheduledRun() throws Exception {
		MvcResult scheduled = createRunResult(false, firstImage.getId(), secondImage.getId());
		long runId = responseId(scheduled);
		ImageAsset replacement = temporaryImage(admin, "scheduled-replacement");

		MvcResult changed = mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, 2)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(imageChangeBody(replacement.getId(), responseUpdatedAt(scheduled))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phases[0].imageAssetId").value(firstImage.getId()))
				.andExpect(jsonPath("$.phases[1].imageAssetId").value(replacement.getId()))
				.andExpect(jsonPath("$.phases[1].imageUrl").value("/images/" + replacement.getStorageKey()))
				.andReturn();

		assertThat(responseUpdatedAt(changed)).isNotEqualTo(responseUpdatedAt(scheduled));
		assertThat(imageRepository.findById(replacement.getId()).orElseThrow().getStatus())
				.isEqualTo(ImageAssetStatus.ACTIVE);
	}

	@Test
	void changesClearedActiveAndWaitingPhaseImagesWithoutTouchingBattleProgress() throws Exception {
		Instant startedAt = Instant.now().minusSeconds(600);
		ImageAsset thirdImage = activeImage(admin, "third");
		BossRun run = BossRun.schedule(boss, startedAt, Instant.now().plusSeconds(3_600), startedAt);
		run.addPhase(1_000, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		run.addPhase(2_000, BigDecimal.ZERO, null, BigDecimal.ONE, secondImage);
		run.addPhase(3_000, BigDecimal.ZERO, null, BigDecimal.ONE, thirdImage);
		run.activate(startedAt);
		List<BossPhase> phases = run.getPhases();
		phases.get(0).applyDamage(1_000);
		run.advanceAfterCurrentPhaseClear(phases.get(0), startedAt.plusSeconds(60), phases.get(1));
		phases.get(1).applyDamage(500);
		run.addAccumulatedDamage(1_500, startedAt.plusSeconds(120));
		run = bossRunRepository.saveAndFlush(run);

		String updatedAt = run.getUpdatedAt().toString();
		ImageAsset clearedReplacement = temporaryImage(admin, "cleared-replacement");
		ImageAsset activeReplacement = temporaryImage(admin, "active-replacement");
		ImageAsset waitingReplacement = temporaryImage(admin, "waiting-replacement");
		updatedAt = changePhaseImage(run.getId(), 1, clearedReplacement.getId(), updatedAt);
		updatedAt = changePhaseImage(run.getId(), 2, activeReplacement.getId(), updatedAt);

		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", run.getId(), 3)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(imageChangeBody(waitingReplacement.getId(), updatedAt)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPhaseNo").value(2))
				.andExpect(jsonPath("$.phases[0].imageAssetId").value(clearedReplacement.getId()))
				.andExpect(jsonPath("$.phases[0].status").value("CLEARED"))
				.andExpect(jsonPath("$.phases[0].remainingHp").value(0))
				.andExpect(jsonPath("$.phases[1].imageAssetId").value(activeReplacement.getId()))
				.andExpect(jsonPath("$.phases[1].status").value("ACTIVE"))
				.andExpect(jsonPath("$.phases[1].remainingHp").value(1_500))
				.andExpect(jsonPath("$.phases[2].imageAssetId").value(waitingReplacement.getId()))
				.andExpect(jsonPath("$.phases[2].status").value("WAITING"))
				.andExpect(jsonPath("$.phases[2].remainingHp").value(3_000));

		assertThat(bossRunRepository.findById(run.getId()).orElseThrow().getAccumulatedDamage()).isEqualTo(1_500);

		// 변경 즉시 공개 조회에도 현재 페이즈의 새 이미지 URL이 반영되어야 한다.
		mockMvc.perform(get("/api/v1/boss-runs/{bossRunId}", run.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentPhase.imageUrl").value("/images/" + activeReplacement.getStorageKey()))
				.andExpect(jsonPath("$.accumulatedDamage").value(1_500));
	}

	@Test
	void rejectsPhaseImageChangeForEveryTerminalRun() throws Exception {
		Instant now = Instant.now();

		BossRun cancelled = BossRun.schedule(boss, now, now.plusSeconds(3_600), now);
		cancelled.addPhase(1_000, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		cancelled.cancel(now);

		BossRun ended = BossRun.schedule(boss, now, now.plusSeconds(3_600), now);
		ended.addPhase(1_000, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		ended.activate(now);
		ended.markEndedUncleared(now);

		BossRun cleared = BossRun.schedule(boss, now, now.plusSeconds(3_600), now);
		cleared.addPhase(1_000, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		cleared.activate(now);
		cleared.getPhases().get(0).applyDamage(1_000);
		cleared.advanceAfterCurrentPhaseClear(cleared.getPhases().get(0), now, null);

		BossRun closed = BossRun.schedule(boss, now, now.plusSeconds(3_600), now);
		closed.addPhase(1_000, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		closed.activate(now);
		closed.markEndedUncleared(now);
		closed.close(now);

		for (BossRun terminal : List.of(cancelled, ended, cleared, closed)) {
			BossRun saved = bossRunRepository.saveAndFlush(terminal);
			ImageAsset replacement = temporaryImage(admin, "terminal-replacement");
			mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", saved.getId(), 1)
						.with(csrf()).session(adminSession).contentType("application/json")
						.content(imageChangeBody(replacement.getId(), saved.getUpdatedAt().toString())))
					.andExpect(status().isConflict());
			assertThat(imageRepository.findById(replacement.getId()).orElseThrow().getStatus())
					.isEqualTo(ImageAssetStatus.TEMPORARY);
		}
	}

	@Test
	void appliesLazyExpiryAndRejectsPhaseImageChangeForExpiredActiveRun() throws Exception {
		Instant createdAt = Instant.now().minusSeconds(7_200);
		BossRun expired = BossRun.schedule(boss, createdAt, createdAt.plusSeconds(3_600), createdAt);
		expired.addPhase(1_800, BigDecimal.ZERO, null, BigDecimal.ONE, firstImage);
		expired.activate(createdAt);
		expired = bossRunRepository.saveAndFlush(expired);
		ImageAsset replacement = temporaryImage(admin, "expired-replacement");

		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", expired.getId(), 1)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(imageChangeBody(replacement.getId(), expired.getUpdatedAt().toString())))
				.andExpect(status().isConflict());

		assertThat(bossRunRepository.findById(expired.getId()).orElseThrow().getStatus())
				.isEqualTo(BossRunStatus.ENDED_UNCLEARED);
		assertThat(imageRepository.findById(replacement.getId()).orElseThrow().getStatus())
				.isEqualTo(ImageAssetStatus.TEMPORARY);
	}

	@Test
	void rejectsInvalidRunPhaseAndImageForPhaseImageChange() throws Exception {
		MvcResult scheduled = createRunResult(false, firstImage.getId(), secondImage.getId());
		long runId = responseId(scheduled);
		String updatedAt = responseUpdatedAt(scheduled);
		ImageAsset valid = temporaryImage(admin, "valid-replacement");

		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", 999_999L, 1)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(imageChangeBody(valid.getId(), updatedAt)))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, 99)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(imageChangeBody(valid.getId(), updatedAt)))
				.andExpect(status().isNotFound());
		assertPhaseImageValidation(runId, imageChangeBody(999_999L, updatedAt));

		User other = userRepository.save(User.createFixture("other-image-" + System.nanoTime() + "@test.dev",
				"hash", "other-image-" + System.nanoTime()));
		assertPhaseImageValidation(runId, imageChangeBody(temporaryImage(other, "other-owner").getId(), updatedAt));

		ImageAsset bookCover = imageRepository.save(ImageAsset.temporary(
				"book-covers/wrong-purpose-" + System.nanoTime() + ".png", "image/png", 10,
				ImagePurpose.BOOK_COVER, admin, Instant.now()));
		assertPhaseImageValidation(runId, imageChangeBody(bookCover.getId(), updatedAt));
		assertPhaseImageValidation(runId, imageChangeBody(activeImage(admin, "already-active").getId(), updatedAt));
		assertPhaseImageValidation(runId, "{\"imageAssetId\":" + valid.getId() + "}");

		String staleUpdatedAt = updatedAt;
		changePhaseImage(runId, 1, valid.getId(), updatedAt);
		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, 1)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(imageChangeBody(temporaryImage(admin, "stale").getId(), staleUpdatedAt)))
				.andExpect(status().isConflict());
	}

	@Test
	void deniesPhaseImageChangeWithoutAdminSessionOrCsrf() throws Exception {
		MvcResult scheduled = createRunResult(false, firstImage.getId(), secondImage.getId());
		long runId = responseId(scheduled);
		String body = imageChangeBody(temporaryImage(admin, "denied").getId(), responseUpdatedAt(scheduled));

		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, 1)
					.with(csrf()).contentType("application/json").content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, 1)
					.with(csrf()).with(user("normal-user").authorities(() -> "USER"))
					.contentType("application/json").content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, 1)
					.session(adminSession).contentType("application/json").content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void preservesReferencedOldImageAndExposesUnreferencedOldImageToCleanup() throws Exception {
		MvcResult scheduled = createRunResult(false, firstImage.getId(), secondImage.getId());
		long runId = responseId(scheduled);
		ImageAsset firstReplacement = temporaryImage(admin, "cleanup-one");
		String updatedAt = changePhaseImage(runId, 2, firstReplacement.getId(), responseUpdatedAt(scheduled));

		// 이전 이미지는 보스 템플릿이 아직 참조하므로 cleanup 이후에도 보존되어야 한다.
		cleanupService.cleanup();
		assertThat(templateRepository.existsByImageAssetId(secondImage.getId())).isTrue();
		assertThat(imageRepository.findById(secondImage.getId())).isPresent();

		ImageAsset secondReplacement = temporaryImage(admin, "cleanup-two");
		changePhaseImage(runId, 2, secondReplacement.getId(), updatedAt);

		// 어디서도 참조하지 않게 된 직전 이미지는 기존 cleanup 정책의 후보가 되어야 한다.
		assertThat(phaseRepository.existsByImageAssetId(firstReplacement.getId())).isFalse();
		assertThat(templateRepository.existsByImageAssetId(firstReplacement.getId())).isFalse();
		Instant futureCutoff = Instant.now().plusSeconds(3_600);
		assertThat(imageRepository.findCleanupCandidateIds(futureCutoff, futureCutoff))
				.contains(firstReplacement.getId());
		cleanupService.cleanup();
		assertThat(imageRepository.findById(firstReplacement.getId())).isPresent();
	}

	private String changePhaseImage(long runId, int phaseNo, long imageAssetId, String expectedUpdatedAt)
			throws Exception {
		MvcResult result = mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, phaseNo)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(imageChangeBody(imageAssetId, expectedUpdatedAt)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phases[%d].imageAssetId".formatted(phaseNo - 1)).value(imageAssetId))
				.andReturn();
		return responseUpdatedAt(result);
	}

	private void assertPhaseImageValidation(long runId, String body) throws Exception {
		mockMvc.perform(put("/api/admin/boss-runs/{runId}/phases/{phaseNo}/image", runId, 1)
					.with(csrf()).session(adminSession).contentType("application/json").content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private String imageChangeBody(long imageAssetId, String expectedUpdatedAt) {
		return "{\"imageAssetId\":" + imageAssetId + ",\"expectedUpdatedAt\":\"" + expectedUpdatedAt + "\"}";
	}

	private long createRun(boolean active, long image1, long image2) throws Exception {
		return responseId(createRunResult(active, image1, image2));
	}

	private MvcResult createRunResult(boolean active, long image1, long image2) throws Exception {
		return mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
					.contentType("application/json").content(runBody(active, image1, image2)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.phases[0].imageAssetId").value(image1))
				.andExpect(jsonPath("$.phases[1].imageAssetId").value(image2))
				.andReturn();
	}

	private long responseId(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
	}

	private String responseUpdatedAt(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("updatedAt").asText();
	}

	private Instant responseCreatedAt(MvcResult result) throws Exception {
		return Instant.parse(objectMapper.readTree(result.getResponse().getContentAsByteArray())
				.get("createdAt").asText());
	}

	private void assertRunValidation(long image1, long image2) throws Exception {
		mockMvc.perform(post("/api/admin/boss-runs").with(csrf()).session(adminSession)
					.contentType("application/json").content(runBody(false, image1, image2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private String runBody(boolean active, long image1, long image2) {
		return """
				{"bossId":%d,"endsAt":"%s","activate":%s,"phases":[
				 {"maxHp":1800,"hardMinScore":10.00,"recommendedScore":30.00,"damageMultiplier":1.0,"imageAssetId":%d},
				 {"maxHp":2200,"hardMinScore":20.00,"recommendedScore":40.00,"damageMultiplier":1.2,"imageAssetId":%d}]}
				""".formatted(boss.getId(), future(), active, image1, image2);
	}

	private Instant future() {
		return Instant.now().plusSeconds(3600);
	}

	private ImageAsset temporaryImage(User owner, String label) {
		return imageRepository.save(ImageAsset.temporary("boss-phases/" + label + "-" + System.nanoTime() + ".png",
				"image/png", 10, ImagePurpose.BOSS_PHASE, owner, Instant.now()));
	}

	private ImageAsset activeImage(User owner, String label) {
		ImageAsset image = temporaryImage(owner, label);
		image.activate();
		return imageRepository.save(image);
	}

	private MockHttpSession login() throws Exception {
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}
}
