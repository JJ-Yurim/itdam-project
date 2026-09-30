package com.ssafy.c206.backend.dailyword.service;

import java.time.LocalDate;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.common.time.ServiceClock;
import com.ssafy.c206.backend.dailyword.api.DailyWordResponse;
import com.ssafy.c206.backend.dailyword.domain.DailyWordSet;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetItemRepository;
import com.ssafy.c206.backend.dailyword.repository.DailyWordSetRepository;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import com.ssafy.c206.backend.common.security.AuthenticatedUsers;
import com.ssafy.c206.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyWordService {

	private final UserQueryService userQueryService;
	private final DailyWordSetRepository dailyWordSetRepository;
	private final DailyWordSetItemRepository dailyWordSetItemRepository;
	private final DailyWordIssuanceTransaction issuanceTransaction;
	private final ServiceClock serviceClock;

	public DailyWordResponse getToday(AuthenticatedUser principal) {
		AuthenticatedUsers.requireUserId(principal);
		var user = userQueryService.getActiveUser(principal.userId());
		LocalDate serviceDate = serviceClock.today();
		DailyWordSet existing = dailyWordSetRepository.findByUserIdAndServiceDate(user.getId(), serviceDate).orElse(null);
		if (existing != null) {
			return response(existing);
		}
		try {
			return issuanceTransaction.issue(user, serviceDate);
		} catch (DataIntegrityViolationException exception) {
			return findAfterRace(user.getId(), serviceDate);
		}
	}

	// 자기호출이라 @Transactional이 적용되지 않던 것을 제거했다.
	// 두 조회는 각자 리포지토리 트랜잭션으로 충분하며, 공유 트랜잭션이 필요하면 프록시를 거치는 구조로 바꿔야 한다.
	DailyWordResponse findAfterRace(Long userId, LocalDate serviceDate) {
		DailyWordSet set = dailyWordSetRepository.findByUserIdAndServiceDate(userId, serviceDate)
				.orElseThrow(() -> new DomainException(ErrorCode.CONFLICT, "Failed to issue today's daily words."));
		return response(set);
	}

	private DailyWordResponse response(DailyWordSet set) {
		return DailyWordResponse.from(set,
				dailyWordSetItemRepository.findByDailyWordSetIdOrderByPositionAsc(set.getId()));
	}
}
