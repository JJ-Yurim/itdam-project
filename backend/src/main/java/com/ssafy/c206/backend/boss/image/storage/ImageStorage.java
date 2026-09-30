package com.ssafy.c206.backend.boss.image.storage;

import org.springframework.web.multipart.MultipartFile;

import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;

public interface ImageStorage {

	StoredImage store(MultipartFile file, ImagePurpose purpose);

	void delete(String storageKey);

	boolean exists(String storageKey);
}
