package com.ssafy.c206.backend.boss.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:bossimageadmin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"app.boss-images.storage-root=build/test-images/boss-image-admin"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BossImageAdminApiTest {

	private static final String SUFFIX = String.valueOf(System.nanoTime());
	private static final String ADMIN_EMAIL = "image-admin-" + SUFFIX + "@api.test";
	private static final String ADMIN_PASSWORD = "admin-password-123";

	@Autowired private WebApplicationContext webApplicationContext;
	@Autowired private FilterChainProxy springSecurityFilterChain;
	@Autowired private UserRepository userRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;
	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain).build();
		new AdminAccountSeeder(userRepository, passwordEncoder, ADMIN_EMAIL, ADMIN_PASSWORD,
				"image-admin-" + SUFFIX).run(null);
		adminSession = loginAsAdmin();
	}

	@Test
	void adminUploadsImageAndReceivesPublicUrl() throws Exception {
		MockMultipartFile image = new MockMultipartFile("image", "boss.png", "image/png",
				LocalImageStorageTest.imageBytes("png", 1, 1));

		mockMvc.perform(multipart("/api/admin/boss-images").file(image).with(csrf()).session(adminSession))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.imageAssetId").isNumber())
				.andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith(
						"/images/boss-phases/")));
	}

	@Test
	void adminUploadsBookCoverIntoDedicatedDirectory() throws Exception {
		MockMultipartFile image = new MockMultipartFile("image", "cover.png", "image/png",
				LocalImageStorageTest.imageBytes("png", 1, 1));

		mockMvc.perform(multipart("/api/admin/book-cover-images").file(image).with(csrf()).session(adminSession))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.imageAssetId").isNumber())
				.andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith(
						"/images/book-covers/")));
	}

	@Test
	void rejectsAnonymousMissingCsrfAndInvalidFiles() throws Exception {
		MockMultipartFile valid = new MockMultipartFile("image", "boss.png", "image/png",
				LocalImageStorageTest.imageBytes("png", 1, 1));
		mockMvc.perform(multipart("/api/admin/boss-images").file(valid).with(csrf()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(multipart("/api/admin/boss-images").file(valid).with(csrf())
					.with(user("normal-user").authorities(() -> "USER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/admin/boss-images").file(valid).session(adminSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/admin/boss-images")
					.file(new MockMultipartFile("image", "fake.png", "image/png", "fake".getBytes()))
					.with(csrf()).session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(multipart("/api/admin/boss-images").with(csrf()).session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private MockHttpSession loginAsAdmin() throws Exception {
		MvcResult login = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sessions").with(csrf())
					.contentType("application/json")
					.content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return session;
	}
}
