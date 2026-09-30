package com.ssafy.c206.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;

import com.ssafy.c206.backend.book.service.BookAdminService;
import com.ssafy.c206.backend.boss.service.BossAdminService;
import com.ssafy.c206.backend.boss.service.BossFixture;
import com.ssafy.c206.backend.boss.service.BossRunAdminService;
import com.ssafy.c206.backend.common.time.ServiceClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 관리자 검색 쿼리의 :query 파라미터는 H2에서는 타입 추론 없이도 통과하지만 PostgreSQL에서는
 * bytea로 바인딩되어 lower(bytea) 오류를 낸다. 실제 PostgreSQL에서만 재현되는 회귀라 여기서 검증한다.
 */
class AdminSearchPostgresIntegrationTest extends PostgresIntegrationTestSupport {

	@Autowired private BossFixture bossFixture;
	@Autowired private BossRunAdminService bossRunAdminService;
	@Autowired private BossAdminService bossAdminService;
	@Autowired private BookAdminService bookAdminService;
	@Autowired private ServiceClock serviceClock;

	@Test
	void searchesAdminListsWithAndWithoutKeyword() {
		Instant now = serviceClock.now();
		bossFixture.createThreePhaseRun(now.minusSeconds(60), now.plusSeconds(3_600), now);

		assertThat(bossRunAdminService.getRuns(null, null, null, null, 0, 20).content()).isNotEmpty();
		assertThat(bossRunAdminService.getRuns("Fixture", null, null, null, 0, 20).content()).isNotEmpty();
		assertThat(bossRunAdminService.getRuns("no-such-boss", null, null, null, 0, 20).content()).isEmpty();

		assertThat(bossAdminService.getBosses(null, null, 0, 20).content()).isNotEmpty();
		assertThat(bossAdminService.getBosses("Fixture", null, 0, 20).content()).isNotEmpty();
		assertThat(bossAdminService.getBosses("no-such-boss", null, 0, 20).content()).isEmpty();

		// 책은 픽스처로 만들지 않는다. 컨테이너를 공유하므로 다른 테스트가 남긴 책의 유무를 단정할 수 없어
		// 검색어 없는 경로는 실행 여부만, 검색어 경로는 걸러진다는 사실만 고정한다.
		assertThatCode(() -> bookAdminService.getBooks(null, null, null, 0, 20)).doesNotThrowAnyException();
		assertThat(bookAdminService.getBooks("no-such-boss", null, null, 0, 20).content()).isEmpty();
	}
}
