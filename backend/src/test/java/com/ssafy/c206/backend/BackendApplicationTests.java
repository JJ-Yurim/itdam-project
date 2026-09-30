package com.ssafy.c206.backend;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private Validator validator;


	@Test
	void contextLoadsWithJpaAndValidation() {
		assertThat(dataSource).isNotNull();
		assertThat(entityManager).isNotNull();
		assertThat(validator).isNotNull();
	}

}
