package com.ssafy.c206.backend.user.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import com.ssafy.c206.backend.user.domain.User;
import com.ssafy.c206.backend.user.api.UserResponse;
import com.ssafy.c206.backend.user.domain.UserStatus;
import com.ssafy.c206.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public User getActiveUser(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new DomainException(ErrorCode.FORBIDDEN, "The account is inactive.");
		}
		return user;
	}

	/**
	 * FK 연관만 필요한 호출자를 위한 지연 참조. SELECT를 발생시키지 않으므로 짧게 유지해야 하는
	 * 쓰기 트랜잭션 안에서 쓴다. 상태 검증이 필요하면 {@link #getActiveUser(Long)}를 쓴다.
	 *
	 * <p>호출자의 트랜잭션 안에서만 유효하다.
	 */
	public User getReference(Long userId) {
		return userRepository.getReferenceById(userId);
	}

	/** 컨트롤러용 응답. 엔티티는 경계 서비스 소비자(다른 도메인 서비스)에게만 반환한다. */
	@Transactional(readOnly = true)
	public UserResponse getMe(Long userId) {
		return UserResponse.from(getActiveUser(userId));
	}

	/** 표시용 닉네임 일괄 조회. 존재하지 않는 id는 결과에서 빠진다. */
	@Transactional(readOnly = true)
	public Map<Long, String> getNicknames(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userRepository.findNicknamesByIdIn(userIds).stream()
				.collect(Collectors.toMap(UserRepository.UserNicknameRow::getId,
						UserRepository.UserNicknameRow::getNickname));
	}
}
