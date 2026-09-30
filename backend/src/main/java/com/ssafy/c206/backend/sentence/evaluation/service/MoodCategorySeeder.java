package com.ssafy.c206.backend.sentence.evaluation.service;


import com.ssafy.c206.backend.sentence.evaluation.domain.MoodCategory;
import com.ssafy.c206.backend.sentence.evaluation.domain.MoodTaxonomy;
import com.ssafy.c206.backend.sentence.evaluation.repository.MoodCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MoodCategorySeeder implements ApplicationRunner {

	private final MoodCategoryRepository moodCategoryRepository;

	// 프록시를 거치도록 진입점에 직접 @Transactional을 둬 시딩 전체가 한 트랜잭션으로 묶인다.
	// (기존에는 @Bean 람다의 자기호출로 트랜잭션이 적용되지 않아 건별 자동커밋이었다.)
	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		MoodTaxonomy.CODES_TO_KOREAN_NAMES.forEach((code, nameKo) -> moodCategoryRepository.findByCode(code)
				.orElseGet(() -> moodCategoryRepository.save(MoodCategory.of(code, nameKo))));
	}
}
