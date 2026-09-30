package com.ssafy.c206.backend.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.BookStatus;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.LibraryQueryService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossPhaseRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.image.domain.ImageAsset;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.repository.ImageAssetRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.submission.domain.BossSubmission;
import com.ssafy.c206.backend.boss.submission.repository.BossSubmissionRepository;
import com.ssafy.c206.backend.boss.submission.service.DamageCalculation;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.sentence.domain.Sentence;
import com.ssafy.c206.backend.sentence.domain.SentenceVersion;
import com.ssafy.c206.backend.sentence.repository.SentenceRepository;
import com.ssafy.c206.backend.sentence.repository.SentenceVersionRepository;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LibraryQueryServiceTest {
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BossPhaseRepository bossPhaseRepository;
	@Autowired private BossSubmissionRepository submissionRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private LibraryQueryService libraryQueryService;
	@Autowired private UserRepository userRepository;
	@Autowired private EmailVerificationStore emailVerificationStore;
	@Autowired private SentenceRepository sentenceRepository;
	@Autowired private SentenceVersionRepository sentenceVersionRepository;
	@Autowired private ServiceClock serviceClock;
	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private BossRepository bossRepository;
	@Autowired private ImageAssetRepository imageAssetRepository;

	@Test
	@Transactional
	void onlyCompletedBooksAreListedAndContentOrderAndContributorAreReturned() {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		bossRunRepository.save(run);
		User coverOwner = userRepository.save(User.createFixture(
				"library-cover-" + System.nanoTime() + "@test.local", "hash", "cover-owner"));
		ImageAsset cover = ImageAsset.temporary("book-covers/library-cover.png", "image/png", 10,
				ImagePurpose.BOOK_COVER, coverOwner, now);
		cover.activate();
		imageAssetRepository.save(cover);
		run.getBoss().changeBookCover(cover, now);
		bossRepository.saveAndFlush(run.getBoss());
		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		User author = userRepository.save(User.createFixture("library-author-" + System.nanoTime() + "@test.local", "hash", "library-author"));
		contentRepository.save(BookContent.create(book, 2, ContentType.USER_ORIGINAL, "second", author, null, now));
		contentRepository.save(BookContent.create(book, 1, ContentType.AI_GENERATED, "first", null, null, now));

		AuthenticatedUser principal = new AuthenticatedUser(author.getId(), author.getEmail());
		// 다른 테스트가 커밋한 완성 책이 공유 H2에 남을 수 있으므로 이 책 기준으로만 단언한다.
		Long bookId = book.getId();
		assertThat(libraryQueryService.list(principal, 0, 20).content())
				.noneMatch(item -> item.bookId().equals(bookId));

		book.complete(now.plusSeconds(1), "본문 전문입니다.", "테스트용 요약입니다.");
		bookRepository.save(book);
		var page = libraryQueryService.list(principal, 0, 20);
		var summary = page.content().stream().filter(item -> item.bookId().equals(bookId)).findFirst().orElseThrow();
		assertThat(summary.contentCount()).isEqualTo(2);
		assertThat(summary.coverImageUrl()).isEqualTo("/images/book-covers/library-cover.png");
		assertThat(summary.summary()).isEqualTo("테스트용 요약입니다.");
		var detail = libraryQueryService.detail(principal, book.getId());
		assertThat(detail.coverImageUrl()).isEqualTo("/images/book-covers/library-cover.png");
		assertThat(detail.contents()).extracting(content -> content.sequenceNo()).containsExactly(1, 2);
		assertThat(detail.contents().get(0).contentType()).isEqualTo(ContentType.AI_GENERATED);
		assertThat(detail.contents().get(1).contributor().nickname()).isEqualTo("library-author");
		// 회원 열람에서는 기여자 userId가 그대로 노출된다.
		assertThat(detail.contents().get(1).contributor().userId()).isEqualTo(author.getId());
	}

	@Test
	@Transactional
	void anonymousDetailHidesContributorUserIdButKeepsNickname() {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		bossRunRepository.save(run);
		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		User author = userRepository.save(User.createFixture("library-anon-" + System.nanoTime() + "@test.local", "hash", "anon-author"));
		contentRepository.save(BookContent.create(book, 1, ContentType.USER_ORIGINAL, "original", author, null, now));
		book.complete(now.plusSeconds(1), "본문 전문입니다.", "테스트용 요약입니다.");
		bookRepository.save(book);

		Long bookId = book.getId();
		assertThat(libraryQueryService.list(null, 0, 20).content())
				.anyMatch(item -> item.bookId().equals(bookId));
		var detail = libraryQueryService.detail(null, book.getId());

		// 닉네임은 작성자 출처 표기(정책 §11.5)로 유지하되, 내부 식별자는 열거 노출을 막는다.
		assertThat(detail.contents().getFirst().contributor().nickname()).isEqualTo("anon-author");
		assertThat(detail.contents().getFirst().contributor().userId()).isNull();
	}

	@Test
	void libraryReadApiAllowsAnonymousAccess() throws Exception {
		var mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();

		// permitAll이 아니면 둘 다 401이다. 목록은 200 빈 페이지, 미존재 상세는 404가 나와야
		// 인증이 아니라 도메인 검증까지 도달했다는 뜻이다.
		mockMvc.perform(get("/api/v1/library/books"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
		mockMvc.perform(get("/api/v1/library/books/{bookId}", 999_999L))
				.andExpect(status().isNotFound());
	}

	@Test
	void anonymousHttpDetailHidesInternalIdsWhileMemberKeepsThem() throws Exception {
		var mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
		Instant now = serviceClock.now();
		// 완성된 책: AI 블록 1개 + 실제 제출에서 비롯된 사용자 편집본 블록 1개.
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		run = bossRunRepository.save(run);
		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		User author = userRepository.save(User.createFixture(
				"library-http-" + System.nanoTime() + "@test.local", "hash", "http-author"));
		BossSubmission submission = submissionFor(run, author, "도서관 계약 검증 문장.");
		contentRepository.save(BookContent.create(book, 1, ContentType.AI_GENERATED, "서막.", null, null, now));
		contentRepository.save(BookContent.create(book, 2, ContentType.USER_EDITED, "도서관 계약 검증 문장을 매끄럽게 다듬었다.",
				author, submission, now));
		book.complete(now.plusSeconds(1), "본문 전문입니다.", "테스트용 요약입니다.");
		bookRepository.save(book);

		// 비회원: 닉네임·출처 유형은 보이고 내부 식별자는 null이다.
		mockMvc.perform(get("/api/v1/library/books/{bookId}", book.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contents.length()").value(2))
				.andExpect(jsonPath("$.contents[1].contentType").value("USER_EDITED"))
				.andExpect(jsonPath("$.contents[1].contributor.nickname").value("http-author"))
				.andExpect(jsonPath("$.contents[1].contributor.userId").value(Matchers.nullValue()))
				.andExpect(jsonPath("$.contents[1].sourceSubmissionId").value(Matchers.nullValue()))
				.andExpect(jsonPath("$.contents[1].contribution.originalContent").value("도서관 계약 검증 문장."))
				.andExpect(jsonPath("$.contents[1].contribution.finalDamage").value(90))
				.andExpect(jsonPath("$.contents[1].contribution.moods").isArray())
				.andExpect(jsonPath("$.contents[1].contribution.usedDailyWords").isArray());

		// 로그인 회원: 내부 식별자가 기존 계약대로 유지된다.
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
				.content(EmailVerificationTestSupport.verifiedSignupBody(
						emailVerificationStore, "library-member@example.com", "password123", "library-member")))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"library-member@example.com\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		mockMvc.perform(get("/api/v1/library/books/{bookId}", book.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contents[1].contributor.userId").value(author.getId()))
				.andExpect(jsonPath("$.contents[1].sourceSubmissionId").value(submission.getId()));

		// COMPLETED가 아닌 책은 비회원에게도 404다.
		BossRun generating = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		generating.markCleared(now);
		generating = bossRunRepository.save(generating);
		preparationService.prepareAfterClear(generating.getId());
		var generatingBook = bookRepository.findByBossRunId(generating.getId()).orElseThrow();
		mockMvc.perform(get("/api/v1/library/books/{bookId}", generatingBook.getId()))
				.andExpect(status().isNotFound());
	}

	@Test
	void inactiveMemberIsStillForbidden() {
		// 비회원 허용이 비활성 계정까지 통과시키지 않는다(Jira AC). 익명 강등이 아니라 403이다.
		User inactive = userRepository.save(User.createFixture(
				"library-inactive-" + System.nanoTime() + "@test.local", "hash",
				"inactive-" + System.nanoTime(), UserStatus.INACTIVE));

		assertThatThrownBy(() -> libraryQueryService.list(new AuthenticatedUser(inactive.getId(), inactive.getEmail()), 0, 20))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
	}

	/** run에 주어진 원문으로 실제 제출을 만든다. 상세 응답의 sourceSubmissionId 계약 검증에 쓴다. */
	private BossSubmission submissionFor(BossRun run, User author, String sentenceText) {
		Instant now = serviceClock.now();
		var phase = bossPhaseRepository.findDetailsByBossRunIdIn(List.of(run.getId())).getFirst();
		Sentence sentence = sentenceRepository.save(Sentence.create(author, now));
		SentenceVersion version = sentenceVersionRepository.save(SentenceVersion.draft(sentence, 1, sentenceText, now));
		DamageCalculation damage = new DamageCalculation(new BigDecimal("90"), BigDecimal.ZERO,
				new BigDecimal("90"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 90L);
		return submissionRepository.save(
				BossSubmission.create(run, phase, author, version, serviceClock.today(), damage, now));
	}

	@Test
	@Transactional
	void incompleteBookDetailIsHidden() {
		Instant now = serviceClock.now();
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		bossRunRepository.save(run);
		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		AuthenticatedUser principal = new AuthenticatedUser(1L, "missing@test.local");
		assertThatThrownBy(() -> libraryQueryService.detail(principal, book.getId())).isInstanceOf(DomainException.class);
	}
}
