package com.ssafy.c206.backend.boss.image.domain;

public enum ImagePurpose {
	BOSS_PHASE("boss-phases"),
	BOOK_COVER("book-covers");

	private final String directory;

	ImagePurpose(String directory) {
		this.directory = directory;
	}

	public String directory() {
		return directory;
	}
}
