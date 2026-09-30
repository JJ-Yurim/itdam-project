package com.ssafy.c206.backend.book.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.feed.api.WeeklySentenceFeedItem;
import com.ssafy.c206.backend.book.feed.service.BookContentLikeService;
import com.ssafy.c206.backend.book.feed.service.WeeklySentenceFeedService;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
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

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeeklySentenceFeedTest {
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private WeeklySentenceFeedService feedService;
	@Autowired private BookContentLikeService likeService;
	@Autowired private ServiceClock serviceClock;
	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private EmailVerificationStore emailVerificationStore;

	@Test
	@Transactional
	void feedIsIdenticalWithinWeekAndOnlyLastWeekUserSentencesAreEligible() throws Exception {
		Instant now = serviceClock.now();
		LocalDate weekStart = weekStart();
		Instant lastWeek = lastWeekInstant(weekStart);
		User author = saveUser("feed-author");

		// 지난주 완성 책: 사용자 문장 2개 + AI 서술문 1개.
		Book eligibleBook = completedBook(now, lastWeek);
		BookContent original = contentRepository.save(BookContent.create(eligibleBook, 1,
				ContentType.USER_ORIGINAL, "지난주의 첫 문장.", author, null, now));
		BookContent edited = contentRepository.save(BookContent.create(eligibleBook, 2,
				ContentType.USER_EDITED, "지난주의 두 번째 문장.", author, null, now));
		contentRepository.save(BookContent.create(eligibleBook, 3, ContentType.AI_GENERATED, "AI 서술.", null, null, now));

		// 이번 주 완성 책과 미완성 책의 문장은 후보가 아니다.
		Book thisWeekBook = completedBook(now, now);
		contentRepository.save(BookContent.create(thisWeekBook, 1, ContentType.USER_ORIGINAL, "이번 주 문장.", author, null, now));
		Book generatingBook = generatingBook(now);
		contentRepository.save(BookContent.create(generatingBook, 1, ContentType.USER_ORIGINAL, "미완성 문장.", author, null, now));

		User viewer = saveUser("feed-viewer");
		AuthenticatedUser principal = new AuthenticatedUser(viewer.getId(), viewer.getEmail());

		var first = feedService.getFeed(principal);
		assertThat(first.weekStartDate()).isEqualTo(weekStart);
		assertThat(first.items()).extracting(WeeklySentenceFeedItem::bookContentId)
				.containsExactlyInAnyOrder(original.getId(), edited.getId());
		assertThat(first.items()).allSatisfy(item -> {
			assertThat(item.likeCount()).isZero();
			assertThat(item.liked()).isFalse();
			assertThat(item.authorNickname()).isEqualTo(author.getNickname());
			assertThat(item.bookId()).isEqualTo(eligibleBook.getId());
		});

		// 비회원도 같은 세트를 열람한다. 도서관 화면이 비회원에게 열려 있으므로 그 안의
		// 베스트 문장 탭만 막히면 안 된다. 개인화되는 값은 liked뿐이라 false로 내린다.
		var anonymous = feedService.getFeed(null);
		assertThat(anonymous.items()).extracting(WeeklySentenceFeedItem::bookContentId)
				.containsExactlyInAnyOrder(original.getId(), edited.getId());
		assertThat(anonymous.items()).allSatisfy(item -> assertThat(item.liked()).isFalse());

		// 서비스 직접 호출은 SecurityConfiguration의 permitAll 목록을 지나지 않는다.
		// 그 설정이 빠져도 위 단정은 통과하므로 HTTP 경계에서도 확인한다. 세트가 이미
		// 발급된 이 시점에서 호출해야 다른 테스트의 시드를 앞지르지 않는다.
		MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build()
				.perform(get("/api/v1/sentences/feed"))
				.andExpect(status().isOk());

		// 같은 주의 재조회는 발급된 세트를 그대로 반환한다(순서 포함).
		var second = feedService.getFeed(principal);
		assertThat(second.items()).extracting(WeeklySentenceFeedItem::bookContentId)
				.containsExactlyElementsOf(first.items().stream().map(WeeklySentenceFeedItem::bookContentId).toList());
	}

	@Test
	@Transactional
	void likeIsIdempotentAndReflectedInFeed() {
		Instant now = serviceClock.now();
		Instant lastWeek = lastWeekInstant(weekStart());
		User author = saveUser("like-author");
		Book book = completedBook(now, lastWeek);
		BookContent content = contentRepository.save(BookContent.create(book, 1,
				ContentType.USER_ORIGINAL, "좋아요 대상 문장.", author, null, now));

		User liker = saveUser("like-liker");
		AuthenticatedUser principal = new AuthenticatedUser(liker.getId(), liker.getEmail());

		likeService.like(principal, content.getId());
		likeService.like(principal, content.getId());
		var feed = feedService.getFeed(principal);
		var item = feed.items().stream().filter(it -> it.bookContentId().equals(content.getId())).findFirst().orElseThrow();
		assertThat(item.likeCount()).isEqualTo(1);
		assertThat(item.liked()).isTrue();

		// 다른 사용자에게는 liked가 개인화된다.
		User other = saveUser("like-other");
		var otherFeed = feedService.getFeed(new AuthenticatedUser(other.getId(), other.getEmail()));
		var otherItem = otherFeed.items().stream().filter(it -> it.bookContentId().equals(content.getId())).findFirst().orElseThrow();
		assertThat(otherItem.likeCount()).isEqualTo(1);
		assertThat(otherItem.liked()).isFalse();

		likeService.unlike(principal, content.getId());
		likeService.unlike(principal, content.getId());
		var afterUnlike = feedService.getFeed(principal);
		var unlikedItem = afterUnlike.items().stream().filter(it -> it.bookContentId().equals(content.getId())).findFirst().orElseThrow();
		assertThat(unlikedItem.likeCount()).isZero();
		assertThat(unlikedItem.liked()).isFalse();
	}

	@Test
	@Transactional
	void hiddenBookSentencesAreExcludedFromCandidatesAndExistingSets() {
		Instant now = serviceClock.now();
		Instant lastWeek = lastWeekInstant(weekStart());
		User author = saveUser("hidden-author");

		Book visibleBook = completedBook(now, lastWeek);
		BookContent visibleContent = contentRepository.save(BookContent.create(visibleBook, 1,
				ContentType.USER_ORIGINAL, "공개 책의 문장.", author, null, now));
		Book hiddenBook = completedBook(now, lastWeek);
		contentRepository.save(BookContent.create(hiddenBook, 1,
				ContentType.USER_ORIGINAL, "숨긴 책의 문장.", author, null, now));
		hiddenBook.hide(now);
		bookRepository.save(hiddenBook);

		User viewer = saveUser("hidden-viewer");
		AuthenticatedUser principal = new AuthenticatedUser(viewer.getId(), viewer.getEmail());

		// 발급 시점에 숨김 상태인 책의 문장은 후보에서 제외된다.
		var feed = feedService.getFeed(principal);
		assertThat(feed.items()).extracting(WeeklySentenceFeedItem::bookContentId)
				.containsExactly(visibleContent.getId());

		// 세트 발급 뒤 숨긴 책의 문장도 조회에서 사라진다.
		visibleBook.hide(now);
		bookRepository.save(visibleBook);
		assertThat(feedService.getFeed(principal).items()).isEmpty();
	}

	@Test
	@Transactional
	void likingContentOfHiddenBookIsRejected() {
		Instant now = serviceClock.now();
		User author = saveUser("hidden-like-author");
		Book hiddenBook = completedBook(now, now);
		BookContent content = contentRepository.save(BookContent.create(hiddenBook, 1,
				ContentType.USER_ORIGINAL, "숨긴 책의 문장.", author, null, now));
		hiddenBook.hide(now);
		bookRepository.save(hiddenBook);

		User liker = saveUser("hidden-like-liker");
		AuthenticatedUser principal = new AuthenticatedUser(liker.getId(), liker.getEmail());
		assertThatThrownBy(() -> likeService.like(principal, content.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	@Transactional
	void likingContentOfUnfinishedBookIsRejected() {
		Instant now = serviceClock.now();
		User author = saveUser("reject-author");
		Book generating = generatingBook(now);
		BookContent content = contentRepository.save(BookContent.create(generating, 1,
				ContentType.USER_ORIGINAL, "미완성 책의 문장.", author, null, now));

		User liker = saveUser("reject-liker");
		AuthenticatedUser principal = new AuthenticatedUser(liker.getId(), liker.getEmail());
		assertThatThrownBy(() -> likeService.like(principal, content.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void httpContractCoversAuthAndIdempotency() throws Exception {
		var mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();

		// 조회는 비회원에게 열려 있지만 좋아요는 여전히 회원만 할 수 있다.
		mockMvc.perform(put("/api/v1/book-contents/{id}/like", 1L).with(csrf()))
				.andExpect(status().isUnauthorized());

		// 좋아요 대상: 커밋되는 완성 책(이번 주 완성이라 주간 세트 후보는 아니다).
		Instant now = serviceClock.now();
		User author = saveUser("http-author");
		Book book = completedBook(now, now);
		BookContent content = contentRepository.save(BookContent.create(book, 1,
				ContentType.USER_ORIGINAL, "HTTP 계약 검증 문장.", author, null, now));

		String email = "feed-http-" + System.nanoTime() + "@example.com";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
				.content(EmailVerificationTestSupport.verifiedSignupBody(
						emailVerificationStore, email, "password123", "feed-http-" + System.nanoTime() % 100_000)))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

		mockMvc.perform(put("/api/v1/book-contents/{id}/like", content.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/book-contents/{id}/like", content.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/book-contents/{id}/like", content.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/book-contents/{id}/like", content.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/book-contents/{id}/like", 999_999_999L).with(csrf()).session(session))
				.andExpect(status().isNotFound());
	}

	private LocalDate weekStart() {
		return serviceClock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/** 주간 세트 후보 윈도우([주 시작 - 7일, 주 시작)) 안의 시각. */
	private Instant lastWeekInstant(LocalDate weekStart) {
		return weekStart.minusDays(3).atStartOfDay(serviceClock.zoneId()).toInstant();
	}

	private Book completedBook(Instant now, Instant completedAt) {
		Book book = generatingBook(now);
		book.complete(completedAt, "주간 문장 피드 테스트용 본문입니다.", "주간 문장 피드 테스트용 요약입니다.");
		return bookRepository.save(book);
	}

	private Book generatingBook(Instant now) {
		BossRun run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.markCleared(now);
		bossRunRepository.save(run);
		preparationService.prepareAfterClear(run.getId());
		return bookRepository.findByBossRunId(run.getId()).orElseThrow();
	}

	private User saveUser(String prefix) {
		String unique = prefix + "-" + System.nanoTime();
		return userRepository.save(User.createFixture(unique + "@test.local", "hash", unique));
	}
}
