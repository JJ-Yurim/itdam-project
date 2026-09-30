package com.ssafy.c206.backend.boss.image.api;

import com.ssafy.c206.backend.boss.image.service.BossImageService;
import com.ssafy.c206.backend.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/book-cover-images")
@RequiredArgsConstructor
public class BookCoverImageAdminController implements BookCoverImageAdminApi {
	private final BossImageService bossImageService;

	@Override
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public BossImageUploadResponse upload(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestPart("image") MultipartFile image) {
		return bossImageService.uploadBookCover(principal, image);
	}
}
