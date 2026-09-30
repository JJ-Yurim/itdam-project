package com.ssafy.c206.backend.book.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.reaction.service.BookReactionService;
import com.ssafy.c206.backend.book.reaction.service.MyBookmarkQueryService;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.LibraryQueryService;
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
class BookReactionTest {
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookRepository bookRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private BookReactionService reactionService;
	@Autowired private MyBookmarkQueryService myBookmarkQueryService;
	@Autowired private LibraryQueryService libraryQueryService;
	@Autowired private ServiceClock serviceClock;
	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private EmailVerificationStore emailVerificationStore;

	@Test
	@Transactional
	void likeAndBookmarkAreIdempotentAndReflectedInLibraryResponses() {
		Instant now = serviceClock.now();
		Book book = completedBook(now);
		User member = saveUser("reaction-member");
		AuthenticatedUser principal = new AuthenticatedUser(member.getId(), member.getEmail());

		reactionService.like(principal, book.getId());
		reactionService.like(principal, book.getId());
		reactionService.bookmark(principal, book.getId());
		reactionService.bookmark(principal, book.getId());

		var detail = libraryQueryService.detail(principal, book.getId());
		assertThat(detail.likeCount()).isEqualTo(1);
		assertThat(detail.liked()).isTrue();
		assertThat(detail.bookmarked()).isTrue();

		// 공유 H2에 다른 테스트의 완성 책이 남아 있을 수 있어 첫 페이지를 넉넉히 잡고 이 책만 찾는다.
		var summary = libraryQueryService.list(principal, 0, 100).content().stream()
				.filter(item -> item.bookId().equals(book.getId())).findFirst().orElseThrow();
		assertThat(summary.likeCount()).isEqualTo(1);
		assertThat(summary.liked()).isTrue();
		assertThat(summary.bookmarked()).isTrue();

		// 다른 회원과 비회원에게는 개인화 플래그가 꺼진 채 집계만 보인다.
		User other = saveUser("reaction-other");
		var otherDetail = libraryQueryService.detail(new AuthenticatedUser(other.getId(), other.getEmail()), book.getId());
		assertThat(otherDetail.likeCount()).isEqualTo(1);
		assertThat(otherDetail.liked()).isFalse();
		assertThat(otherDetail.bookmarked()).isFalse();
		var anonymousDetail = libraryQueryService.detail(null, book.getId());
		assertThat(anonymousDetail.likeCount()).isEqualTo(1);
		assertThat(anonymousDetail.liked()).isFalse();
		assertThat(anonymousDetail.bookmarked()).isFalse();

		reactionService.unlike(principal, book.getId());
		reactionService.unlike(principal, book.getId());
		reactionService.unbookmark(principal, book.getId());
		reactionService.unbookmark(principal, book.getId());
		var afterRemoval = libraryQueryService.detail(principal, book.getId());
		assertThat(afterRemoval.likeCount()).isZero();
		assertThat(afterRemoval.liked()).isFalse();
		assertThat(afterRemoval.bookmarked()).isFalse();
	}

	@Test
	@Transactional
	void myBookmarksAreOrderedByBookmarkedAtDesc() {
		Instant now = serviceClock.now();
		Book first = completedBook(now);
		Book second = completedBook(now);
		User member = saveUser("bookmark-member");
		AuthenticatedUser principal = new AuthenticatedUser(member.getId(), member.getEmail());

		reactionService.bookmark(principal, first.getId());
		reactionService.bookmark(principal, second.getId());

		var page = myBookmarkQueryService.getMyBookmarks(principal, 0, 20);
		assertThat(page.content()).extracting(item -> item.bookId())
				.containsExactly(second.getId(), first.getId());
		assertThat(page.content()).allSatisfy(item -> assertThat(item.bookmarked()).isTrue());

		reactionService.like(principal, first.getId());
		var refreshed = myBookmarkQueryService.getMyBookmarks(principal, 0, 20);
		var firstItem = refreshed.content().stream().filter(item -> item.bookId().equals(first.getId()))
				.findFirst().orElseThrow();
		assertThat(firstItem.likeCount()).isEqualTo(1);
		assertThat(firstItem.liked()).isTrue();
	}

	@Test
	@Transactional
	void reactingToUnfinishedBookIsRejected() {
		Instant now = serviceClock.now();
		Book generating = generatingBook(now);
		User member = saveUser("reject-member");
		AuthenticatedUser principal = new AuthenticatedUser(member.getId(), member.getEmail());

		assertThatThrownBy(() -> reactionService.like(principal, generating.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> reactionService.bookmark(principal, generating.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void httpContractCoversAuthAndIdempotency() throws Exception {
		var mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();

		Instant now = serviceClock.now();
		Book book = completedBook(now);

		// 등록·해제와 북마크 목록은 회원 전용이다.
		mockMvc.perform(put("/api/v1/books/{id}/like", book.getId()).with(csrf()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me/bookmarks")).andExpect(status().isUnauthorized());

		String email = "reaction-http-" + System.nanoTime() + "@example.com";
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
				.content(EmailVerificationTestSupport.verifiedSignupBody(
						emailVerificationStore, email, "password123", "reaction-" + System.nanoTime() % 100_000)))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

		mockMvc.perform(put("/api/v1/books/{id}/like", book.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/books/{id}/like", book.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/books/{id}/bookmark", book.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/me/bookmarks").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].bookId").value(book.getId()))
				.andExpect(jsonPath("$.content[0].liked").value(true))
				.andExpect(jsonPath("$.content[0].bookmarked").value(true));

		mockMvc.perform(delete("/api/v1/books/{id}/like", book.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/books/{id}/like", book.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/books/{id}/bookmark", book.getId()).with(csrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/books/{id}/like", 999_999_999L).with(csrf()).session(session))
				.andExpect(status().isNotFound());
	}

	private Book completedBook(Instant now) {
		Book book = generatingBook(now);
		book.complete(now, "책 반응 테스트용 본문입니다.", "책 반응 테스트용 요약입니다.");
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
