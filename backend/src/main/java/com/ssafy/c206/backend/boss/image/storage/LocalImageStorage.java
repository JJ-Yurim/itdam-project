package com.ssafy.c206.backend.boss.image.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.ssafy.c206.backend.boss.image.domain.ImagePurpose;
import com.ssafy.c206.backend.common.api.DomainException;
import com.ssafy.c206.backend.common.api.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalImageStorage implements ImageStorage {

	private final Path storageRoot;
	private final long maxBytes;
	private final long maxPixels;

	public LocalImageStorage(
			@Value("${app.boss-images.storage-root}") String storageRoot,
			@Value("${app.boss-images.max-bytes}") long maxBytes,
			@Value("${app.boss-images.max-pixels}") long maxPixels) {
		this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
		this.maxBytes = maxBytes;
		this.maxPixels = maxPixels;
	}

	@Override
	public StoredImage store(MultipartFile file, ImagePurpose purpose) {
		if (purpose == null) {
			throw invalid("Image purpose is required.");
		}
		byte[] bytes = readBounded(file);
		ImageFormat format = detectFormat(bytes);
		validateDeclaredType(file.getContentType(), format);
		validateDecodedImage(bytes, format);

		String storageKey = purpose.directory() + "/" + UUID.randomUUID() + "." + format.extension;
		Path target = resolve(storageKey);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		try {
			Files.createDirectories(target.getParent());
			Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			moveAtomically(temporary, target);
			return new StoredImage(storageKey, format.contentType, bytes.length);
		} catch (IOException exception) {
			deleteQuietly(temporary);
			throw new DomainException(ErrorCode.INTERNAL_ERROR, "Image file could not be stored.", exception);
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolve(storageKey));
		} catch (IOException exception) {
			throw new DomainException(ErrorCode.INTERNAL_ERROR, "Image file could not be deleted.", exception);
		}
	}

	@Override
	public boolean exists(String storageKey) {
		return Files.isRegularFile(resolve(storageKey));
	}

	private byte[] readBounded(MultipartFile file) {
		if (file == null || file.isEmpty() || maxBytes <= 0 || file.getSize() > maxBytes) {
			throw invalid("Image is required and must not exceed " + maxBytes + " bytes.");
		}
		try (var input = file.getInputStream()) {
			int limit = Math.toIntExact(Math.min(maxBytes + 1, Integer.MAX_VALUE));
			byte[] bytes = input.readNBytes(limit);
			if (bytes.length == 0 || bytes.length > maxBytes) {
				throw invalid("Image is empty or too large.");
			}
			return bytes;
		} catch (IOException exception) {
			throw invalid("Image could not be read.");
		}
	}

	private ImageFormat detectFormat(byte[] bytes) {
		if (startsWith(bytes, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
			return ImageFormat.PNG;
		}
		if (startsWith(bytes, new int[] {0xFF, 0xD8, 0xFF})) {
			return ImageFormat.JPEG;
		}
		if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
			return ImageFormat.WEBP;
		}
		throw invalid("Only PNG, JPEG and WebP images are allowed.");
	}

	private void validateDeclaredType(String declaredType, ImageFormat format) {
		if (declaredType == null) {
			return;
		}
		String normalized = declaredType.toLowerCase(Locale.ROOT);
		boolean matches = normalized.equals(format.contentType)
				|| (format == ImageFormat.JPEG && normalized.equals("image/jpg"));
		if (!matches) {
			throw invalid("Declared image type does not match file contents.");
		}
	}

	private void validateDecodedImage(byte[] bytes, ImageFormat expectedFormat) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (input == null) {
				throw invalid("Image contents are invalid.");
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw invalid("Image format is not decodable.");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
					throw invalid("Image dimensions are invalid or too large.");
				}
				BufferedImage decoded = reader.read(0);
				if (decoded == null) {
					throw invalid("Image contents are invalid.");
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof DomainException domainException) {
				throw domainException;
			}
			throw invalid(expectedFormat.contentType + " image could not be decoded.");
		}
	}

	private Path resolve(String storageKey) {
		if (storageKey == null || storageKey.isBlank()) {
			throw invalid("storageKey is required.");
		}
		Path resolved = storageRoot.resolve(storageKey).normalize();
		if (!resolved.startsWith(storageRoot)) {
			throw invalid("storageKey is outside the image root.");
		}
		return resolved;
	}

	private static void moveAtomically(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			throw new IOException("Atomic image move is not supported.", exception);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// A later operational orphan scan can remove an exceptional temporary file.
		}
	}

	private static boolean startsWith(byte[] bytes, int[] prefix) {
		if (bytes.length < prefix.length) return false;
		for (int index = 0; index < prefix.length; index++) {
			if ((bytes[index] & 0xFF) != prefix[index]) return false;
		}
		return true;
	}

	private static boolean ascii(byte[] bytes, int offset, String value) {
		for (int index = 0; index < value.length(); index++) {
			if (bytes[offset + index] != (byte) value.charAt(index)) return false;
		}
		return true;
	}

	private static DomainException invalid(String message) {
		return new DomainException(ErrorCode.VALIDATION_ERROR, message);
	}

	private enum ImageFormat {
		PNG("png", "image/png"),
		JPEG("jpg", "image/jpeg"),
		WEBP("webp", "image/webp");

		private final String extension;
		private final String contentType;

		ImageFormat(String extension, String contentType) {
			this.extension = extension;
			this.contentType = contentType;
		}
	}
}
