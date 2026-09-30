package com.ssafy.c206.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserActivityQueryApiTest {

	private static final String PASSWORD = "password123";

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ServiceClock serviceClock;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
	}

	@Test
	@Transactional
	void myBossRunsAreDistinctFilteredOrderedAndPrivate() throws Exception {
		UserSession member = userSession("boss-history-member");
		UserSession outsider = userSession("boss-history-outsider");
		Instant now = serviceClock.now();

		BossRun olderRun = bossFixture.createThreePhaseRun(now.minusSeconds(600), now.plusSeconds(3_600), now.minusSeconds(600));
		submissionFor(olderRun, member.user(), "older one", now.minusSeconds(300));
		submissionFor(olderRun, member.user(), "older two", now.minusSeconds(200));

		BossRun clearedRun = bossFixture.createThreePhaseRun(now.minusSeconds(600), now.plusSeconds(3_600), now.minusSeconds(600));
		submissionFor(clearedRun, member.user(), "latest", now.minusSeconds(100));
		clearedRun.markCleared(now.minusSeconds(50));
		bossRunRepository.saveAndFlush(clearedRun);
		Book completedBook = prepareBook(clearedRun);
		completedBook.complete(now.minusSeconds(40), "본문 전문입니다.", "테스트용 요약입니다.");
		bookRepository.saveAndFlush(completedBook);

		BossRun outsiderRun = bossFixture.createThreePhaseRun(now.minusSeconds(600), now.plusSeconds(3_600), now.minusSeconds(600));
		submissionFor(outsiderRun, outsider.user(), "outsider only", now.minusSeconds(10));

		mockMvc.perform(get("/api/v1/me/boss-runs").session(member.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].bossRunId").value(clearedRun.getId()))
				.andExpect(jsonPath("$.content[0].lastSubmittedAt").isString())
				.andExpect(jsonPath("$.content[0].bookId").value(completedBook.getId()))
				.andExpect(jsonPath("$.content[0].bookStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.content[1].bossRunId").value(olderRun.getId()));

		mockMvc.perform(get("/api/v1/me/boss-runs").param("status", "CLEARED").session(member.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].bossRunId").value(clearedRun.getId()));

		mockMvc.perform(get("/api/v1/me/boss-runs")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me/boss-runs").param("size", "101").session(member.session()))
				.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void myLibraryContainsOnlyCompletedBooksWithMyActuallyIncludedContents() throws Exception {
		UserSession member = userSession("book-history-member");
		UserSession outsider = userSession("book-history-outsider");
		Instant now = serviceClock.now();

		Book completed = newBook(now, 0);
		contentRepository.save(BookContent.create(completed, 1, ContentType.AI_GENERATED, "opening", null, null, now));
		contentRepository.save(BookContent.create(completed, 2, ContentType.USER_ORIGINAL, "mine one",
				member.user(), null, now));
		contentRepository.save(BookContent.create(completed, 3, ContentType.USER_EDITED, "mine two",
				member.user(), null, now));
		completed.complete(now.plusSeconds(1), "본문 전문입니다.", "테스트용 요약입니다.");
		bookRepository.saveAndFlush(completed);

		Book generating = newBook(now, 10);
		contentRepository.save(BookContent.create(generating, 1, ContentType.USER_ORIGINAL, "not completed",
				member.user(), null, now));

		Book failed = newBook(now, 20);
		contentRepository.save(BookContent.create(failed, 1, ContentType.USER_ORIGINAL, "failed",
				member.user(), null, now));
		failed.fail(now.plusSeconds(1));
		bookRepository.saveAndFlush(failed);

		Book someoneElses = newBook(now, 30);
		contentRepository.save(BookContent.create(someoneElses, 1, ContentType.USER_ORIGINAL, "not mine",
				outsider.user(), null, now));
		someoneElses.complete(now.plusSeconds(1), "본문 전문입니다.", "테스트용 요약입니다.");
		bookRepository.saveAndFlush(someoneElses);

		mockMvc.perform(get("/api/v1/me/library/books").session(member.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].bookId").value(completed.getId()))
				.andExpect(jsonPath("$.content[0].contentCount").value(3))
				.andExpect(jsonPath("$.content[0].myIncludedContentCount").value(2));

		mockMvc.perform(get("/api/v1/me/library/books")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me/library/books").param("page", "-1").session(member.session()))
				.andExpect(status().isBadRequest());
	}

	private Book newBook(Instant now, long offsetSeconds) {
		Instant createdAt = now.minusSeconds(600 + offsetSeconds);
		BossRun run = bossFixture.createThreePhaseRun(createdAt, now.plusSeconds(3_600), createdAt);
		run.markCleared(now.minusSeconds(100 + offsetSeconds));
		bossRunRepository.saveAndFlush(run);
		return prepareBook(run);
	}

	private Book prepareBook(BossRun run) {
		preparationService.prepareAfterClear(run.getId());
		return bookRepository.findByBossRunId(run.getId()).orElseThrow();
	}

	private BossSubmission submissionFor(BossRun run, User author, String text, Instant submittedAt) {
		var phase = bossPhaseRepository.findDetailsByBossRunIdIn(List.of(run.getId())).getFirst();
		Sentence sentence = sentenceRepository.save(Sentence.create(author, submittedAt));
		SentenceVersion version = sentenceVersionRepository.save(SentenceVersion.draft(sentence, 1, text, submittedAt));
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		return submissionRepository.saveAndFlush(
				BossSubmission.create(run, phase, author, version, serviceClock.today(), damage, submittedAt));
	}

	private UserSession userSession(String prefix) throws Exception {
		String suffix = String.valueOf(System.nanoTime());
		String email = prefix + '-' + suffix + "@activity.test";
		User user = userRepository.save(User.createFixture(email, passwordEncoder.encode(PASSWORD), prefix + '-' + suffix));
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return new UserSession(user, (MockHttpSession) login.getRequest().getSession(false));
	}

	private record UserSession(User user, MockHttpSession session) { }
}
