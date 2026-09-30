package com.ssafy.c206.backend.user.agreement.api;

import java.time.Instant;

import com.ssafy.c206.backend.user.agreement.domain.CopyrightAgreement;
import io.swagger.v3.oas.annotations.media.Schema;

public record CopyrightAgreementResponse(
		@Schema(description = "저작권 동의서 식별자입니다.", example = "1") Long id,
		@Schema(description = "동의서 버전입니다.", example = "1.0") String version,
		@Schema(description = "동의서 제목입니다.", example = "잇담 저작권 이용 동의서") String title,
		@Schema(description = "회원가입 화면에 표시할 동의서 본문입니다.") String content,
		@Schema(description = "동의서 효력 시작 시각입니다.", format = "date-time") Instant effectiveAt,
		@Schema(description = "동의 효력 기간(개월)입니다.", example = "12") int validMonths) {

	public static CopyrightAgreementResponse from(CopyrightAgreement agreement) {
		return new CopyrightAgreementResponse(agreement.getId(), agreement.getVersion(), agreement.getTitle(),
				agreement.getContent(), agreement.getEffectiveAt(), agreement.getValidMonths());
	}
}
