package com.ssafy.c206.backend.book.service;

import com.ssafy.c206.backend.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCoverReferenceQueryService {
	private final BookRepository bookRepository;

	@Transactional(readOnly = true)
	public boolean isCoverImageAssetReferenced(Long imageAssetId) {
		return bookRepository.existsByCoverImageAssetId(imageAssetId);
	}
}
