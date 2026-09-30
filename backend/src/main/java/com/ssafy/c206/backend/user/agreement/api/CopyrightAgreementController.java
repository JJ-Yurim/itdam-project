package com.ssafy.c206.backend.user.agreement.api;

import com.ssafy.c206.backend.user.agreement.service.CopyrightAgreementQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/copyright-agreements")
@RequiredArgsConstructor
public class CopyrightAgreementController implements CopyrightAgreementApi {

	private final CopyrightAgreementQueryService copyrightAgreementQueryService;

	@GetMapping("/current")
	public CopyrightAgreementResponse current() {
		return copyrightAgreementQueryService.getCurrent();
	}
}
