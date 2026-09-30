package com.ssafy.c206.backend.boss.image.storage;

public record StoredImage(String storageKey, String contentType, long byteSize) {
}
