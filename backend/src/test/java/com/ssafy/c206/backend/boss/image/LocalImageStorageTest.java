package com.ssafy.c206.backend.boss.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import com.ssafy.c206.backend.boss.image.storage.LocalImageStorage;
import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.boss.image.storage.StoredImage;
import com.ssafy.c206.backend.common.api.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalImageStorageTest {

	private static final byte[] WEBP_1X1 = Base64.getDecoder().decode(
			"UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEALmk0mk0iIiIiIgBoSygABc6zbAAA");

	@TempDir Path tempDir;

	@Test
	void storesDecodedPngJpegAndWebpUnderGeneratedNames() throws Exception {
		LocalImageStorage storage = new LocalImageStorage(tempDir.toString(), 5 * 1024 * 1024, 16_777_216);

		StoredImage png = storage.store(file("client-name.png", "image/png", imageBytes("png", 1, 1)),
				ImagePurpose.BOSS_PHASE);
		StoredImage jpeg = storage.store(file("portrait.jpeg", "image/jpeg", imageBytes("jpeg", 1, 1)),
				ImagePurpose.BOSS_PHASE);
		StoredImage webp = storage.store(file("phase.webp", "image/webp", WEBP_1X1), ImagePurpose.BOSS_PHASE);

		assertThat(png.storageKey()).startsWith("boss-phases/").endsWith(".png").doesNotContain("client-name");
		assertThat(jpeg.storageKey()).startsWith("boss-phases/").endsWith(".jpg").doesNotContain("portrait");
		assertThat(webp.storageKey()).startsWith("boss-phases/").endsWith(".webp").doesNotContain("phase.webp");
		assertThat(Files.exists(tempDir.resolve(png.storageKey()))).isTrue();
		assertThat(Files.exists(tempDir.resolve(jpeg.storageKey()))).isTrue();
		assertThat(Files.exists(tempDir.resolve(webp.storageKey()))).isTrue();
		assertThat(Files.walk(tempDir).noneMatch(path -> path.getFileName().toString().endsWith(".tmp"))).isTrue();
	}

	@Test
	void storesBookCoversInTheirOwnPublicDirectory() throws Exception {
		LocalImageStorage storage = new LocalImageStorage(tempDir.toString(), 5 * 1024 * 1024, 16_777_216);

		StoredImage cover = storage.store(file("cover.png", "image/png", imageBytes("png", 1, 1)),
				ImagePurpose.BOOK_COVER);

		assertThat(cover.storageKey()).startsWith("book-covers/").endsWith(".png");
		assertThat(Files.exists(tempDir.resolve(cover.storageKey()))).isTrue();
	}

	@Test
	void rejectsSpoofedMimeSvgOversizeAndExcessivePixels() throws Exception {
		LocalImageStorage storage = new LocalImageStorage(tempDir.toString(), 100, 4);

		assertThatThrownBy(() -> storage.store(file("fake.png", "image/png", "not-an-image".getBytes()),
				ImagePurpose.BOSS_PHASE))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> storage.store(file("attack.svg", "image/svg+xml",
				"<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes()), ImagePurpose.BOSS_PHASE))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> storage.store(file("large.png", "image/png", new byte[101]),
				ImagePurpose.BOSS_PHASE))
				.isInstanceOf(DomainException.class);
		assertThatThrownBy(() -> storage.store(file("pixels.png", "image/png", imageBytes("png", 3, 2)),
				ImagePurpose.BOSS_PHASE))
				.isInstanceOf(DomainException.class);
	}

	private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
		return new MockMultipartFile("image", name, contentType, bytes);
	}

	static byte[] imageBytes(String format, int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, format, output)) {
			throw new IllegalStateException("No ImageIO writer for " + format);
		}
		return output.toByteArray();
	}
}
