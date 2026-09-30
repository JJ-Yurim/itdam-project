package com.ssafy.c206.backend.boss.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface BossRunRepository extends JpaRepository<BossRun, Long> {

	/**
	 * "지금 활성화할 수 있는 회차"의 정의. 가드·후보 조회·잠금 뒤 재확인이 모두 같은 조건을 봐야
	 * 하므로 한 곳에 둔다. 조건이 갈라지면 영원히 참인 가드나 헛도는 잠금이 생긴다.
	 *
	 * <p>종료 시각이 이미 지난 회차를 제외하는 이유는, 동기화가 만료를 먼저 돌리기 때문이다. 여기서
	 * 올려 봐야 같은 패스에서 정리되지 않아 끝난 레이드가 진행 중으로 응답에 실린다. 아무도 조회하지
	 * 않은 채 창이 닫힌 회차는 예정으로 남고, 관리자가 취소로 정리한다.
	 */
	String ACTIVATABLE_PREDICATE = """
			run.status = com.ssafy.c206.backend.boss.domain.BossRunStatus.SCHEDULED
			  and run.startedAt is not null and run.startedAt <= :now
			  and (run.endsAt is null or run.endsAt > :now)
			  and exists (select phase.id from BossPhase phase where phase.bossRun = run)
			  and not exists (select other.id from BossRun other where other.boss = run.boss
					and other.status = com.ssafy.c206.backend.boss.domain.BossRunStatus.ACTIVE)
			""";

	@EntityGraph(attributePaths = "boss")
	Page<BossRun> findAllByStatusIn(Collection<BossRunStatus> statuses, Pageable pageable);

	@EntityGraph(attributePaths = "boss")
	Page<BossRun> findAllBy(Pageable pageable);

	// :param is null 형태로 파라미터가 처음 등장하면 Hibernate가 타입을 추론하지 못해 PostgreSQL에서
	// bytea로 바인딩된다(lower(bytea) does not exist). 필터 사용 여부는 별도 boolean 파라미터로 전달한다.
	@EntityGraph(attributePaths = "boss")
	@Query("""
			select run from BossRun run join run.boss boss
			where (:filterQuery = false or lower(boss.name) like lower(concat('%', :query, '%')) escape '\\')
			  and (:filterStatuses = false or run.status in :statuses)
			  and (:filterFrom = false or run.endsAt >= :from)
			  and (:filterTo = false or run.startedAt is null or run.startedAt <= :to)
			""")
	Page<BossRun> searchAdmin(@Param("query") String query, @Param("filterQuery") boolean filterQuery,
			@Param("filterStatuses") boolean filterStatuses,
			@Param("statuses") Collection<BossRunStatus> statuses,
			@Param("from") Instant from, @Param("filterFrom") boolean filterFrom,
			@Param("to") Instant to, @Param("filterTo") boolean filterTo, Pageable pageable);

	@EntityGraph(attributePaths = "boss")
	Optional<BossRun> findWithBossById(Long id);

	@EntityGraph(attributePaths = "boss")
	List<BossRun> findWithBossByIdIn(Collection<Long> ids);

	boolean existsByBossIdAndStatus(Long bossId, BossRunStatus status);

	/**
	 * 지연 전이 가드. 만료(ACTIVE·endsAt 경과)와 활성화를 한 번에 확인해 전이 대상이 없는 대부분의
	 * 조회가 쓰기 트랜잭션을 열지 않도록 한다. 두 방향을 각각 exists로 확인하면 평상시 요청마다
	 * 쿼리가 두 번 나가므로 하나로 묶었다.
	 *
	 * <p>활성화 조건은 {@code ACTIVATABLE_PREDICATE}와 글자 그대로 같아야 한다. 가드가 더 느슨하면
	 * 실제로는 아무것도 전이되지 않는데 참을 돌려주는 회차가 생기고, 그 회차가 남아 있는 내내 모든
	 * 목록 요청이 쓰기 트랜잭션과 행 잠금을 헛되이 잡는다. 가드를 둔 이유가 사라진다.
	 *
	 * <p>활성화 분기는 "이미 진행 중인 회차가 없을 것"을 요구하지만, 그 진행 중 회차의 종료 시각이
	 * 지났다면 만료 분기가 참이 되어 동기화가 돌고 슬롯이 비므로 후속 활성화가 밀리지 않는다.
	 */
	@Query("""
			select count(run) > 0 from BossRun run
			where (run.status = com.ssafy.c206.backend.boss.domain.BossRunStatus.ACTIVE
					and run.endsAt is not null and run.endsAt <= :now)
			   or (""" + BossRunRepository.ACTIVATABLE_PREDICATE + ")")
	boolean existsPendingLifecycleTransition(@Param("now") Instant now);

	/** 단건 경로용 가드. 활성화는 보스 행 잠금을 잡으므로 밀린 전이가 있을 때만 그 경로로 들어간다. */
	@Query("select count(run) > 0 from BossRun run where run.id = :runId and ("
			+ BossRunRepository.ACTIVATABLE_PREDICATE + ")")
	boolean isActivationDue(@Param("runId") Long runId, @Param("now") Instant now);

	/**
	 * 지금 활성화할 수 있는 회차 id. 활성화는 만료와 달리 벌크 UPDATE로 끝낼 수 없어(1페이즈 상태도
	 * 함께 바꿔야 하고 보스당 ACTIVE 유일 제약이 걸린다) 호출부가 id로 하나씩 처리한다.
	 * 동시 요청이 같은 순서로 잠금을 잡도록 정렬을 고정해 데드락을 피한다.
	 *
	 * <p>한 보스에 활성화 대기 회차가 둘 이상이면 모두 후보로 나온다. 먼저 올라간 회차 때문에
	 * 나머지가 건너뛰어지는 판단은 호출부가 잠금을 잡은 뒤에 한다.
	 */
	@Query("select run.id from BossRun run where " + BossRunRepository.ACTIVATABLE_PREDICATE
			+ " order by run.startedAt asc, run.id asc")
	List<Long> findActivatableRunIds(@Param("now") Instant now, Pageable pageable);

	/** 회차 엔티티를 영속성 컨텍스트에 올리지 않고 보스 id만 얻는다. 잠금 순서를 지키기 위한 조회다. */
	@Query("select run.boss.id from BossRun run where run.id = :runId")
	Optional<Long> findBossIdByRunId(@Param("runId") Long runId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select run from BossRun run join fetch run.boss where run.id = :runId")
	Optional<BossRun> findWithBossByIdForUpdate(@Param("runId") Long runId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update BossRun run
			set run.status = com.ssafy.c206.backend.boss.domain.BossRunStatus.ENDED_UNCLEARED,
				run.updatedAt = :now
			where run.id = :runId
			  and run.status = com.ssafy.c206.backend.boss.domain.BossRunStatus.ACTIVE
			  and run.endsAt is not null
			  and run.endsAt <= :now
			""")
	int expireIfActiveAndEnded(@Param("runId") Long runId, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update BossRun run
			set run.status = com.ssafy.c206.backend.boss.domain.BossRunStatus.ENDED_UNCLEARED,
				run.updatedAt = :now
			where run.status = com.ssafy.c206.backend.boss.domain.BossRunStatus.ACTIVE
			  and run.endsAt is not null
			  and run.endsAt <= :now
			""")
	int expireAllActiveRunsEndedAt(@Param("now") Instant now);
}
