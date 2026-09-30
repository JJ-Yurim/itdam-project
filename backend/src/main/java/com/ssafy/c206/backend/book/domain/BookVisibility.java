package com.ssafy.c206.backend.book.domain;

/** 생성 상태와 분리된 서비스 공개 여부. HIDDEN 책은 모든 공개 표면에서 제외된다. */
public enum BookVisibility {
	PUBLIC,
	HIDDEN
}
