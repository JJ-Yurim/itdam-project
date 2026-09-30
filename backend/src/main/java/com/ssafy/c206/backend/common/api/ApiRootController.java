package com.ssafy.c206.backend.common.api;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated API-root discovery response; never exposes deployment metadata. */
@RestController
@RequestMapping("/api/v1")
public class ApiRootController implements ApiRootApi {

	@GetMapping
	public ApiRootResponse root() {
		return new ApiRootResponse("mvp-backend", "v1");
	}
}
