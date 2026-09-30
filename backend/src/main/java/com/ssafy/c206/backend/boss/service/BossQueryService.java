package com.ssafy.c206.backend.boss.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.ssafy.c206.backend.boss.api.BossRunResponse;
import com.ssafy.c206.backend.boss.api.BossRunSummaryResponse;
import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.domain.BossRunStatus;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.api.PageResponse;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BossQueryService {

	private final BossRunLifecycleService lifecycleService;
	private final BossRunRepository bossRunRepository;
	private final UserQueryService userQueryService;
	private final BossRunResponseAssembler responseAssembler;
	private final ServiceClock serviceClock;

	/**
	 * 목록 조회. statuses가 비어 있으면 전체를 조회한다.
	 *
	 * <p>여러 상태를 한 번에 받는 이유는 화면의 "종료" 탭 하나가 CLEARED·ENDED_UNCLEARED·CLOSED
	 * 세 상태에 걸쳐 있기 때문이다. 단일 상태만 받으면 그 탭은 요청을 세 번 보내고 페이지네이션을
	 * 클라이언트에서 다시 맞춰야 한다. 어떤 상태를 묶을지는 화면이 정하고 서버는 도메인 상태
	 * 그대로 받는다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<BossRunSummaryResponse> getRuns(AuthenticatedUser principal, List<BossRunStatus> statuses,
			int page, int size) {
		Long userId = resolveActiveUserId(principal);
		// 지연 전이는 공개 경로에서도 유지한다. 익명에게만 건너뛰면 종료 시각이 지난 보스가
		// ACTIVE로, 시작 시각이 지난 보스가 SCHEDULED로 보여 회원과 응답이 어긋나고 status
		// 필터도 틀어진다. 다만 전이 대상이 없는 대부분의 요청까지 REQUIRES_NEW 쓰기
		// 트랜잭션(두 번째 커넥션)을 열 이유는 없으므로, 이 readOnly 트랜잭션 안의 exists
		// 조회로 가드한다. 가드는 호출부에만 둘 수 있다. lifecycle 서비스 내부에 두면 이미
		// REQUIRES_NEW에 진입한 뒤거나 자기호출로 프록시를 우회한다.
		Instant requestTime = serviceClock.now();
		if (bossRunRepository.existsPendingLifecycleTransition(requestTime)) {
			lifecycleService.syncAllRunStates(requestTime);
		}
		PageRequest pageRequest = PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		// null 원소를 먼저 걷어낸다. Spring은 빈 문자열을 enum null로 변환하므로
		// ?status=&status=ACTIVE 같은 요청이 [null, ACTIVE]로 들어오고, 그대로 EnumSet에
		// 넣으면 NPE가 나서 400이 아니라 500으로 응답한다.
		List<BossRunStatus> requested = statuses == null ? List.of()
				: statuses.stream().filter(Objects::nonNull)
						.filter(status -> status != BossRunStatus.CANCELLED).toList();
		boolean explicitlyFiltered = statuses != null && statuses.stream().anyMatch(Objects::nonNull);
		Page<BossRun> runs = explicitlyFiltered && requested.isEmpty()
				? Page.empty(pageRequest)
				: bossRunRepository.findAllByStatusIn(requested.isEmpty()
						? EnumSet.complementOf(EnumSet.of(BossRunStatus.CANCELLED)) : EnumSet.copyOf(requested), pageRequest);
		List<BossRunSummaryResponse> responses = responseAssembler.assembleSummaries(runs.getContent(), userId);
		return new PageResponse<>(responses, runs.getNumber(), runs.getSize(), runs.getTotalElements(),
				runs.getTotalPages(), runs.hasNext());
	}

	@Transactional(readOnly = true)
	public BossRunResponse getRun(AuthenticatedUser principal, Long bossRunId) {
		Long userId = resolveActiveUserId(principal);
		lifecycleService.syncStateIfNeeded(bossRunId, serviceClock.now());
		BossRun run = bossRunRepository.findWithBossById(bossRunId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		return responseAssembler.assemble(List.of(run), userId).getFirst();
	}

	/**
	 * 보스 조회는 비회원 열람을 허용한다(ISSUE-115). principal이 없으면 null을 돌려주고,
	 * assembler가 사용자별 필드(mySubmissionCount 등)를 0과 빈 목록으로 채운다.
	 * 세션이 있는 경우에는 기존과 동일하게 활성 계정만 통과시킨다(비활성 계정은 403).
	 * 인증 객체는 있는데 userId가 비어 있는 상태는 익명이 아니라 인증 설정 결함이므로
	 * 조용히 흡수하지 않고 requireUserId로 401을 드러낸다.
	 */
	private Long resolveActiveUserId(AuthenticatedUser principal) {
		if (principal == null) {
			return null;
		}
		long userId = AuthenticatedUsers.requireUserId(principal);
		userQueryService.getActiveUser(userId);
		return userId;
	}
}
