package com.ssafy.c206.backend.boss;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossPhase;
import com.ssafy.c206.backend.boss.domain.BossPhaseTemplate;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.domain.ImageAssetStatus;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.repository.BossPhaseTemplateRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossPhaseImagePersistenceTest {

	private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

	@Autowired private UserRepository userRepository;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private ImageAssetRepository imageAssetRepository;
	@Autowired private BossPhaseTemplateRepository templateRepository;

	@Test
	void persistsReusableTemplateAndIndependentRunPhaseSnapshot() {
		User admin = userRepository.save(User.registerAdmin(
				"phase-image-admin@example.com", "encoded-password", "phase-image-admin"));
		Boss boss = bossRepository.save(Boss.create("Image Boss", "phase image persistence", NOW));
		ImageAsset image = imageAssetRepository.save(ImageAsset.temporary(
				"boss-phases/test.png", "image/png", 68L, ImagePurpose.BOSS_PHASE, admin, NOW));
		BossPhaseTemplate template = templateRepository.save(BossPhaseTemplate.of(
				boss, 1, 1700, new BigDecimal("40.00"), new BigDecimal("60.00"), BigDecimal.ONE, image));

		BossRun run = BossRun.schedule(boss, null, NOW.plusSeconds(3600), NOW);
		BossPhase phase = run.addPhase(1700, new BigDecimal("40.00"), new BigDecimal("60.00"),
				BigDecimal.ONE, image);
		bossRunRepository.saveAndFlush(run);

		assertThat(template.getBoss().getId()).isEqualTo(boss.getId());
		assertThat(template.getImageAsset().getId()).isEqualTo(image.getId());
		assertThat(phase.getBossRun()).isSameAs(run);
		assertThat(phase.getImageAsset().getId()).isEqualTo(image.getId());
		assertThat(image.getStatus()).isEqualTo(ImageAssetStatus.TEMPORARY);
		assertThat(templateRepository.findByBossIdOrderByPhaseNo(boss.getId())).containsExactly(template);
	}
}
