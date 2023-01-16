/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.build.bom.bomr.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP cache which caches on the filesystem.
 *
 * @author Moritz Halbritter
 */
public class FileBasedHttpCache implements HttpCache {

	private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedHttpCache.class);

	private final Path directory;

	public FileBasedHttpCache(Path directory) {
		this.directory = directory;
	}

	@Override
	public void store(String uri, String etag, String content) {
		try {
			Files.createDirectories(getCacheDirectory(uri));
			Files.write(getUriFile(uri), uri.getBytes(StandardCharsets.UTF_8));
			Files.write(getEtagFile(uri), etag.getBytes(StandardCharsets.UTF_8));
			Files.write(getContentFile(uri), content.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		LOGGER.debug("Stored etag '{}' for uri '{}', {} bytes of content", etag, uri, content.length());
	}

	@Override
	public Cached load(String uri) {
		Path etagFile = getEtagFile(uri);
		Path contentFile = getContentFile(uri);
		if (!Files.exists(etagFile) || !Files.exists(contentFile)) {
			return null;
		}
		try {
			String etag = new String(Files.readAllBytes(etagFile), StandardCharsets.UTF_8);
			String content = new String(Files.readAllBytes(contentFile), StandardCharsets.UTF_8);
			LOGGER.debug("Found cache entry for '{}'", uri);
			return new Cached(etag, content);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

	}

	private Path getCacheDirectory(String uri) {
		return this.directory.resolve(sha256(uri));
	}

	private Path getEtagFile(String uri) {
		return getCacheDirectory(uri).resolve("etag");
	}

	private Path getContentFile(String uri) {
		return getCacheDirectory(uri).resolve("content");
	}

	private Path getUriFile(String uri) {
		return getCacheDirectory(uri).resolve("uri");
	}

	private String sha256(String input) {
		MessageDigest sha256;
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new AssertionError("Failed to get SHA-256", ex);
		}
		byte[] bytes = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Creates a http cache in the tmp directory.
	 * @return the created http cache
	 */
	public static FileBasedHttpCache createDefault() {
		Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
		Path cacheDir = tempDir.resolve("spring-boot-bomr");
		LOGGER.debug("Using {} as cache directory", cacheDir);
		return new FileBasedHttpCache(cacheDir);
	}

}
