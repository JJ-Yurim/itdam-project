package com.ssafy.c206.backend.dailyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ssafy.c206.backend.dailyword.api.DailyWordResponse;
import com.ssafy.c206.backend.dailyword.domain.DailyWordSet;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetItemRepository;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetRepository;
import com.ssafy.c206.backend.dailyword.service.DailyWordPreviewService;
import com.ssafy.c206.backend.dailyword.service.DailyWordService;
import com.ssafy.c206.backend.user.repository.UserRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.user.service.UserFixture;
import com.ssafy.c206.backend.word.domain.Word;
import com.ssafy.c206.backend.word.etl.DictionarySense;
import com.ssafy.c206.backend.word.repository.WordRepository;
import com.ssafy.c206.backend.user.EmailVerificationTestSupport;
import com.ssafy.c206.backend.user.service.EmailVerificationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DailyWordPersistenceTest {

	@Autowired DailyWordService dailyWordService;
	@Autowired DailyWordPreviewService dailyWordPreviewService;
	@Autowired DailyWordSetRepository dailyWordSetRepository;
	@Autowired DailyWordSetItemRepository dailyWordSetItemRepository;
	@Autowired UserFixture userFixture;
	@Autowired UserRepository userRepository;
	@Autowired EmailVerificationStore emailVerificationStore;
	@Autowired WordRepository wordRepository;
	@Autowired WebApplicationContext webApplicationContext;
	@Autowired FilterChainProxy springSecurityFilterChain;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
		dailyWordSetItemRepository.deleteAll();
		dailyWordSetRepository.deleteAll();
		wordRepository.deleteAll();
		userRepository.deleteAll();
		// preview는 날짜 단위 인메모리 캐시를 갖는다. 테스트마다 단어 풀을 갈아끼우므로
		// 캐시를 비우지 않으면 앞 테스트의 결과가 뒤 테스트에 새어 들어간다.
		org.springframework.test.util.ReflectionTestUtils.setField(dailyWordPreviewService, "cached",
				new java.util.concurrent.atomic.AtomicReference<>());
	}

	@AfterEach
	void shutDown() {
		dailyWordSetItemRepository.deleteAll();
		dailyWordSetRepository.deleteAll();
		wordRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void issuesFiveActiveWordsOnceAndKeepsOrderOnRequery() {
		var user = userFixture.createFixture("daily@example.com", "hash", "daily");
		seedWords(6);

		DailyWordResponse first = dailyWordService.getToday(new AuthenticatedUser(user.getId(), user.getEmail()));
		DailyWordResponse second = dailyWordService.getToday(new AuthenticatedUser(user.getId(), user.getEmail()));

		assertThat(first.items()).hasSize(5);
		assertThat(second).isEqualTo(first);
		assertThat(dailyWordSetRepository.count()).isEqualTo(1);
		assertThat(dailyWordSetItemRepository.count()).isEqualTo(5);
		assertThat(first.items()).extracting(DailyWordResponse.Item::position)
				.containsExactly(1, 2, 3, 4, 5);
		assertThat(first.items()).extracting(DailyWordResponse.Item::wordId).doesNotHaveDuplicates();
	}

	@Test
	void rejectsIssuanceWhenActivePoolHasFewerThanFiveWords() {
		var user = userFixture.createFixture("small-pool@example.com", "hash", "small-pool");
		seedWords(4);

		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
			dailyWordService.getToday(new AuthenticatedUser(user.getId(), user.getEmail())))
				.isInstanceOf(com.ssafy.c206.backend.common.api.DomainException.class);
		assertThat(dailyWordSetRepository.count()).isZero();
	}

	@Test
	void concurrentFirstRequestsCreateOneSetAndFiveItems() throws Exception {
		var user = userFixture.createFixture("race@example.com", "hash", "race");
		seedWords(8);
		int callers = 8;
		ExecutorService executor = Executors.newFixedThreadPool(callers);
		CountDownLatch ready = new CountDownLatch(callers);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<DailyWordResponse>> futures = java.util.stream.IntStream.range(0, callers)
					.mapToObj(index -> executor.submit(() -> {
						ready.countDown();
						start.await();
						return dailyWordService.getToday(new AuthenticatedUser(user.getId(), user.getEmail()));
					})).toList();
			ready.await();
			start.countDown();
			List<DailyWordResponse> responses = futures.stream().map(future -> {
				try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
			}).toList();
			assertThat(responses).allSatisfy(response -> assertThat(response).isEqualTo(responses.get(0)));
			assertThat(dailyWordSetRepository.count()).isEqualTo(1);
			assertThat(dailyWordSetItemRepository.count()).isEqualTo(5);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void authenticatedEndpointUsesSessionPrincipal() throws Exception {
		seedWords(5);
		mockMvc.perform(post("/api/v1/users").with(csrf()).contentType("application/json")
				.content(EmailVerificationTestSupport.verifiedSignupBody(
						emailVerificationStore, "api-daily@example.com", "password123", "api-daily")))
			.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/sessions").with(csrf()).contentType("application/json")
				.content("{\"email\":\"api-daily@example.com\",\"password\":\"password123\"}"))
			.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

		mockMvc.perform(get("/api/v1/daily-words/today").session(session))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(containsString("public"))))
				.andExpect(jsonPath("$.items.length()").value(5))
				.andExpect(jsonPath("$.items[0].position").value(1));
	}

	@Test
	void previewAllowsAnonymousAndIsDeterministicWithoutIssuingSets() throws Exception {
		seedWords(8);

		// 개인 세트(/today)는 여전히 인증을 요구한다. 미리보기 공개가 지급 경로를 열지 않는다.
		mockMvc.perform(get("/api/v1/daily-words/today")).andExpect(status().isUnauthorized());

		String first = mockMvc.perform(get("/api/v1/daily-words/preview"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL,
						allOf(containsString("max-age="), containsString("public"))))
				.andExpect(jsonPath("$.dailyWordSetId").doesNotExist())
				.andExpect(jsonPath("$.serviceDate").exists())
				.andExpect(jsonPath("$.items.length()").value(5))
				.andExpect(jsonPath("$.items[0].position").value(1))
				.andReturn().getResponse().getContentAsString();
		// 캐시를 비워 두 번째 호출이 실제 재계산을 타게 한다. 캐시 적중 비교로는
		// 시드 결정성이 검증되지 않는다.
		org.springframework.test.util.ReflectionTestUtils.setField(dailyWordPreviewService, "cached",
				new java.util.concurrent.atomic.AtomicReference<>());
		String second = mockMvc.perform(get("/api/v1/daily-words/preview"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// 같은 서비스 날짜에는 항상 같은 결과가 계산된다(날짜 시드 결정성).
		assertThat(second).isEqualTo(first);
		// 미리보기는 지급 이력을 만들지 않는다.
		assertThat(dailyWordSetRepository.count()).isZero();
		assertThat(dailyWordSetItemRepository.count()).isZero();
	}

	@Test
	void previewRejectsWhenActivePoolHasFewerThanFiveWords() throws Exception {
		seedWords(4);

		mockMvc.perform(get("/api/v1/daily-words/preview")).andExpect(status().isConflict());
	}

	private void seedWords(int count) {
		List<Word> words = java.util.stream.IntStream.range(0, count)
				.mapToObj(index -> Word.from(DictionarySense.builder().word("word-" + index)
						.partOfSpeech("noun").definition("definition-" + index).build()))
				.toList();
		wordRepository.saveAllAndFlush(words);
	}
}
