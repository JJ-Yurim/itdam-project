package com.ssafy.c206.backend.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.c206.backend.book.domain.Book;
import com.ssafy.c206.backend.book.domain.BookContent;
import com.ssafy.c206.backend.book.domain.ContentType;
import com.ssafy.c206.backend.book.reaction.service.BookReactionService;
import com.ssafy.c206.backend.book.reaction.service.MyBookmarkQueryService;
import com.ssafy.c206.backend.book.repository.BookContentRepository;
import com.ssafy.c206.backend.book.repository.BookRepository;
import com.ssafy.c206.backend.book.service.BookGenerationPreparationService;
import com.ssafy.c206.backend.book.service.LibraryQueryService;
import com.ssafy.c206.backend.book.service.MyLibraryQueryService;
import com.ssafy.c206.backend.boss.domain.Boss;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossRepository;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.time.ServiceClock;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:bookadmin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookAdminApiTest {
	private static final String ADMIN_EMAIL = "book-admin-" + System.nanoTime() + "@api.test";
	private static final String ADMIN_PASSWORD = "admin-password-123";

	@Autowired private WebApplicationContext context;
	@Autowired private FilterChainProxy securityFilterChain;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private BossFixture bossFixture;
	@Autowired private BossRepository bossRepository;
	@Autowired private BossRunRepository bossRunRepository;
	@Autowired private BookGenerationPreparationService preparationService;
	@Autowired private BookRepository bookRepository;
	@Autowired private BookContentRepository contentRepository;
	@Autowired private LibraryQueryService libraryQueryService;
	@Autowired private MyLibraryQueryService myLibraryQueryService;
	@Autowired private BookReactionService reactionService;
	@Autowired private MyBookmarkQueryService myBookmarkQueryService;
	@Autowired private ServiceClock serviceClock;
	@Autowired private ObjectMapper objectMapper;

	private MockMvc mockMvc;
	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
		new AdminAccountSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD, "book-admin").run(null);
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();
		adminSession = (MockHttpSession) login.getRequest().getSession(false);
	}

	@Test
	void adminReplacesOnlyTheSelectedBooksCover() throws Exception {
		Instant now = serviceClock.now();
		var run = bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3600), now);
		run.markCleared(now);
		bossRunRepository.save(run);
		preparationService.prepareAfterClear(run.getId());
		var book = bookRepository.findByBossRunId(run.getId()).orElseThrow();

		MockMultipartFile image = new MockMultipartFile("image", "cover.png", "image/png",
				Base64.getDecoder().decode(
						"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
		MvcResult uploaded = mockMvc.perform(multipart("/api/admin/book-cover-images").file(image)
					.with(csrf()).session(adminSession)).andExpect(status().isCreated()).andReturn();
		long imageAssetId = objectMapper.readTree(uploaded.getResponse().getContentAsByteArray())
				.get("imageAssetId").asLong();

		mockMvc.perform(put("/api/admin/books/{bookId}/cover", book.getId()).with(csrf()).session(adminSession)
					.contentType("application/json").content("{\"imageAssetId\":" + imageAssetId + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookId").value(book.getId()))
				.andExpect(jsonPath("$.coverImageAssetId").value(imageAssetId))
				.andExpect(jsonPath("$.coverImageUrl").value(org.hamcrest.Matchers.startsWith("/images/book-covers/")))
				// 표지 교체도 updatedAt을 올린다. 이 값을 돌려주지 않으면 화면이 이어지는 공개 전환에
				// 옛 expectedUpdatedAt을 실어 보내 409를 맞는다.
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());

		Book replaced = bookRepository.findById(book.getId()).orElseThrow();
		assertThat(replaced.getCoverImageAsset().getId()).isEqualTo(imageAssetId);
		assertThat(replaced.getUpdatedAt()).isAfter(book.getCreatedAt());
	}

	@Test
	void bookCoverUploadRequiresAdminAndCsrf() throws Exception {
		MockMultipartFile image = new MockMultipartFile("image", "cover.png", "image/png", new byte[]{1});

		mockMvc.perform(multipart("/api/admin/book-cover-images").file(image).with(csrf()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(multipart("/api/admin/book-cover-images").file(image).with(user("member").roles("USER")).with(csrf()))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/admin/book-cover-images").file(image).session(adminSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void bookCoverChangeRequiresAdminAndCsrf() throws Exception {
		String body = "{\"imageAssetId\":1}";

		mockMvc.perform(put("/api/admin/books/1/cover").with(csrf()).contentType("application/json").content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/admin/books/1/cover").with(user("member").roles("USER")).with(csrf())
					.contentType("application/json").content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/admin/books/1/cover").session(adminSession)
					.contentType("application/json").content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void listsBooksWithBossNameStatusAndVisibilityFilters() throws Exception {
		Instant now = serviceClock.now();
		String bossName = "목록보스-" + System.nanoTime();
		Book completed = completedBookWithBoss(bossName, now);
		contentRepository.save(BookContent.create(completed, 1, ContentType.AI_GENERATED,
				"관리자 목록 검증용 본문 문장.", null, null, now));
		Book generating = generatingBook(now);

		mockMvc.perform(get("/api/admin/books").session(adminSession).param("query", bossName))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].bookId").value(completed.getId()))
				.andExpect(jsonPath("$.content[0].bossRunId").value(completed.getBossRun().getId()))
				.andExpect(jsonPath("$.content[0].bossName").value(bossName))
				.andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$.content[0].visibility").value("PUBLIC"))
				.andExpect(jsonPath("$.content[0].contentCount").value(1))
				.andExpect(jsonPath("$.content[0].completedAt").isNotEmpty())
				.andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty());

		mockMvc.perform(get("/api/admin/books").session(adminSession)
					.param("query", bossName).param("visibility", "HIDDEN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/admin/books").session(adminSession).param("status", "GENERATING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.bookId == %d)]".formatted(generating.getId())).exists())
				.andExpect(jsonPath("$.content[?(@.bookId == %d)]".formatted(completed.getId())).doesNotExist());

		mockMvc.perform(get("/api/admin/books").session(adminSession).param("size", "500"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void changesVisibilityWithCompletedOnlyPublishAndUpdatedAtGuard() throws Exception {
		Instant now = serviceClock.now();
		Book completed = completedBook(now);
		String initialUpdatedAt = completed.getUpdatedAt().toString();

		MvcResult hidden = mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", completed.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("HIDDEN", initialUpdatedAt)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookId").value(completed.getId()))
				.andExpect(jsonPath("$.visibility").value("HIDDEN"))
				.andReturn();
		String hiddenUpdatedAt = objectMapper.readTree(hidden.getResponse().getContentAsByteArray())
				.get("updatedAt").asText();

		// 직전 수정으로 stale이 된 expectedUpdatedAt은 409로 거절된다.
		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", completed.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("PUBLIC", initialUpdatedAt)))
				.andExpect(status().isConflict());

		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", completed.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("PUBLIC", hiddenUpdatedAt)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.visibility").value("PUBLIC"));

		// 미완성 책은 공개 상태를 바꿀 수 없다. 완료 시 complete()가 공개로 덮어쓰므로
		// "미리 숨겨두기"를 성공으로 응답하면 그 결정이 조용히 뒤집히기 때문이다.
		Book generating = generatingBook(now);
		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", generating.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("PUBLIC", generating.getUpdatedAt().toString())))
				.andExpect(status().isConflict());
		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", generating.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("HIDDEN", generating.getUpdatedAt().toString())))
				.andExpect(status().isConflict());

		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", completed.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content("{\"visibility\":\"HIDDEN\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", completed.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content("{\"expectedUpdatedAt\":\"" + hiddenUpdatedAt + "\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", Long.MAX_VALUE)
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("HIDDEN", hiddenUpdatedAt)))
				.andExpect(status().isNotFound());
	}

	@Test
	void hiddenBookDisappearsFromEveryPublicSurface() throws Exception {
		Instant now = serviceClock.now();
		Book book = completedBook(now);
		User member = saveUser("visibility-member");
		BookContent content = contentRepository.save(BookContent.create(book, 1, ContentType.USER_ORIGINAL,
				"기여 문장입니다.", member, null, now));
		AuthenticatedUser principal = new AuthenticatedUser(member.getId(), member.getEmail());
		reactionService.bookmark(principal, book.getId());

		assertThat(libraryQueryService.list(null, 0, 100).content())
				.anyMatch(item -> item.bookId().equals(book.getId()));
		assertThat(libraryQueryService.detail(null, book.getId()).bookId()).isEqualTo(book.getId());
		assertThat(myLibraryQueryService.getMyContributedBooks(principal, 0, 100).content())
				.anyMatch(item -> item.bookId().equals(book.getId()));
		assertThat(myBookmarkQueryService.getMyBookmarks(principal, 0, 100).content())
				.anyMatch(item -> item.bookId().equals(book.getId()));

		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", book.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("HIDDEN", book.getUpdatedAt().toString())))
				.andExpect(status().isOk());

		assertThat(libraryQueryService.list(null, 0, 100).content())
				.noneMatch(item -> item.bookId().equals(book.getId()));
		assertThatThrownBy(() -> libraryQueryService.detail(null, book.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		assertThat(myLibraryQueryService.getMyContributedBooks(principal, 0, 100).content())
				.noneMatch(item -> item.bookId().equals(book.getId()));
		assertThat(myBookmarkQueryService.getMyBookmarks(principal, 0, 100).content())
				.noneMatch(item -> item.bookId().equals(book.getId()));
		assertThatThrownBy(() -> reactionService.like(principal, book.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		User other = saveUser("visibility-other");
		assertThatThrownBy(() -> reactionService.bookmark(
				new AuthenticatedUser(other.getId(), other.getEmail()), book.getId()))
				.isInstanceOfSatisfying(DomainException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

		// 다시 공개하면 기존 반응 데이터(북마크)가 그대로 되살아난다.
		String hiddenUpdatedAt = bookRepository.findById(book.getId()).orElseThrow().getUpdatedAt().toString();
		mockMvc.perform(patch("/api/admin/books/{bookId}/visibility", book.getId())
					.with(csrf()).session(adminSession).contentType("application/json")
					.content(visibilityBody("PUBLIC", hiddenUpdatedAt)))
				.andExpect(status().isOk());
		assertThat(myBookmarkQueryService.getMyBookmarks(principal, 0, 100).content())
				.anyMatch(item -> item.bookId().equals(book.getId()));
		assertThat(contentRepository.findById(content.getId())).isPresent();
	}

	@Test
	void bookListAndVisibilityRequireAdminAndCsrf() throws Exception {
		mockMvc.perform(get("/api/admin/books")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/books").with(user("member").roles("USER")))
				.andExpect(status().isForbidden());

		String body = visibilityBody("HIDDEN", serviceClock.now().toString());
		mockMvc.perform(patch("/api/admin/books/1/visibility").with(csrf())
					.contentType("application/json").content(body))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/admin/books/1/visibility").with(user("member").roles("USER")).with(csrf())
					.contentType("application/json").content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/admin/books/1/visibility").session(adminSession)
					.contentType("application/json").content(body))
				.andExpect(status().isForbidden());
	}

	private String visibilityBody(String visibility, String expectedUpdatedAt) {
		return "{\"visibility\":\"" + visibility + "\",\"expectedUpdatedAt\":\"" + expectedUpdatedAt + "\"}";
	}

	private Book completedBook(Instant now) {
		Book book = generatingBook(now);
		book.complete(now, "책 공개 관리 테스트용 본문입니다.", "책 공개 관리 테스트용 요약입니다.");
		return bookRepository.save(book);
	}

	private Book completedBookWithBoss(String bossName, Instant now) {
		Boss boss = bossRepository.save(Boss.create(bossName, "관리자 목록 테스트용 보스입니다.", now));
		BossRun run = BossRun.schedule(boss, now.minusSeconds(60), now.plusSeconds(3_600), now);
		run.addPhase(500, BigDecimal.ZERO, null, BigDecimal.ONE);
		run.activate(now);
		run.markCleared(now);
		run = bossRunRepository.save(run);
		preparationService.prepareAfterClear(run.getId());
		Book book = bookRepository.findByBossRunId(run.getId()).orElseThrow();
		book.complete(now, "관리자 목록 테스트용 본문입니다.", "관리자 목록 테스트용 요약입니다.");
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

	@Test
	void rejectsMissingImageAssetIdBeforeLookingUpBook() throws Exception {
		mockMvc.perform(put("/api/admin/books/{bookId}/cover", Long.MAX_VALUE).with(csrf()).session(adminSession)
					.contentType("application/json").content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}
}
