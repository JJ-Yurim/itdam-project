package com.ssafy.c206.backend.boss.service;

import com.ssafy.c206.backend.boss.domain.BossRun;
import com.ssafy.c206.backend.boss.repository.BossRunRepository;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 다른 도메인(book 등)이 보스 런 엔티티를 조회할 때 사용하는 경계 서비스. */
@Service
@RequiredArgsConstructor
public class BossRunReader {

	private final BossRunRepository bossRunRepository;

	/** 쓰기 흐름용 잠금 조회. 호출자는 트랜잭션 안이어야 한다. */
	public BossRun getRunWithBossForUpdate(Long bossRunId) {
		return bossRunRepository.findWithBossByIdForUpdate(bossRunId)
				.orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
	}
}
