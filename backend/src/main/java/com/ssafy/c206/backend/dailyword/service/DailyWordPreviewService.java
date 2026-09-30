package com.ssafy.c206.backend.dailyword.service;

import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import com.ssafy.c206.backend.dailyword.api.DailyWordPreviewResponse;
import com.ssafy.c206.backend.word.service.ActiveWordSelector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 비회원용 오늘의 단어 미리보기(ISSUE-115).
 *
 * <p>서비스 날짜를 시드로 쓰는 결정적 선택이라 같은 날짜에는 항상 같은 5개가 계산된다.
 * 그래서 캐시나 게스트 식별자 없이도 하루 동안 일관된 결과를 보장하고,
 * {@code daily_word_sets}에 지급 이력을 만들지 않아 Lazy Issuance 원칙(정책 §6.2)과 충돌하지 않는다.
 *
 * <p>결정성의 전제는 활성 단어 집합이 하루 안에 바뀌지 않는다는 것이다. 관리자가 낮에
 * ETL로 단어를 추가·비활성화하면 그 시점부터 미리보기가 달라질 수 있다. 미리보기는 열람
 * 전용이고 어떤 기록과도 연결되지 않으므로 이 정도 흔들림은 허용한다.
 */
@Service
@RequiredArgsConstructor
public class DailyWordPreviewService {

	/**
	 * epochDay는 매일 1씩 커지는 작은 값이라 그대로 시드로 쓰면 java.util.Random(LCG)의
	 * 첫 추출값이 날짜 간에 상관된다(실측: 단어 20개 기준 첫 오프셋이 4일 주기로 반복).
	 * 황금비 상수를 곱해 시드 비트를 섞으면 결정성은 유지하면서 날짜 간 상관이 사라진다.
	 */
	private static final long SEED_MIX = 0x9E3779B97F4A7C15L;

	private final ActiveWordSelector activeWordSelector;

	/**
	 * 서비스 날짜 단위 단일 슬롯 캐시. 결과가 날짜당 결정적이라 경합으로 두 번 계산돼도
	 * 같은 값이 들어가므로 락 없이 AtomicReference로 충분하고, 날짜가 바뀌면 키 불일치로
	 * 자동 무효화된다. 캐시 적중 시 DB에 전혀 가지 않도록 트랜잭션도 열지 않는다.
	 * 단일 인스턴스 배포 전제이며, 캐시 동안 활성 단어 변경이 반영되지 않는 것은
	 * 클래스 Javadoc에서 허용한 흔들림 범위다.
	 */
	private final AtomicReference<DailyWordPreviewResponse> cached = new AtomicReference<>();

	public DailyWordPreviewResponse getPreview(LocalDate serviceDate) {
		DailyWordPreviewResponse hit = cached.get();
		if (hit != null && hit.serviceDate().equals(serviceDate)) {
			return hit;
		}
		DailyWordPreviewResponse fresh = DailyWordPreviewResponse.of(serviceDate,
				activeWordSelector.selectFive(new Random(serviceDate.toEpochDay() * SEED_MIX)));
		cached.set(fresh);
		return fresh;
	}
}
