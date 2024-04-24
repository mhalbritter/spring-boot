/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.boot.buildpack.platform.docker;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import org.springframework.boot.buildpack.platform.docker.type.DistributionManifest;
import org.springframework.boot.buildpack.platform.docker.type.DistributionManifestList;
import org.springframework.boot.buildpack.platform.docker.type.ImageArchiveIndex;
import org.springframework.boot.buildpack.platform.docker.type.ImageArchiveManifest;
import org.springframework.boot.buildpack.platform.io.IOBiConsumer;
import org.springframework.boot.buildpack.platform.io.TarArchive;
import org.springframework.boot.buildpack.platform.io.TarArchive.Compression;
import org.springframework.util.function.ThrowingFunction;

/**
 * @author Phillip Webb
 */
class ExportableLayersTar implements Closeable {

	private final Path path;

	private final ImageArchiveManifest manifest;

	private final Map<String, String> layerDigestMediaTypes;

	ExportableLayersTar(InputStream inputStream) throws IOException {
		this.path = Files.createTempFile("docker-layers-", null);
		Files.copy(inputStream, this.path, StandardCopyOption.REPLACE_EXISTING);
		ImageArchiveManifest manifest = null;
		ImageArchiveIndex index = null;
		try (TarArchiveInputStream tar = openTar(this.path)) {
			TarArchiveEntry entry = tar.getNextTarEntry();
			while (entry != null) {
				if ("manifest.json".equals(entry.getName())) {
					manifest = readJson(tar, ImageArchiveManifest::of);
				}
				if ("index.json".equals(entry.getName())) {
					index = readJson(tar, ImageArchiveIndex::of);
				}
				entry = tar.getNextTarEntry();
			}
		}
		this.manifest = manifest;
		this.layerDigestMediaTypes = (index != null) ? getLayerDigestMediaTypes(index) : Collections.emptyMap();
	}

	private Map<String, String> getLayerDigestMediaTypes(ImageArchiveIndex index) throws IOException {
		Set<String> distributionManifestListDigests = getDistributionManifestListDigests(index);
		List<DistributionManifestList> distributionManifestLists = getDistributionManifestLists(this.path,
				distributionManifestListDigests);
		List<DistributionManifest> distributionManifests = getDistributionManifests(this.path,
				distributionManifestLists);
		Map<String, String> digestMediaTypes = new HashMap<>();
		for (DistributionManifest distributionManifest : distributionManifests) {
			for (DistributionManifest.Layer layer : distributionManifest.getLayers()) {
				digestMediaTypes.put(layer.getDigest(), layer.getMediaType());
			}
		}
		return Collections.unmodifiableMap(digestMediaTypes);
	}

	private static Set<String> getDistributionManifestListDigests(ImageArchiveIndex index) {
		Set<String> digests = new HashSet<>();
		for (ImageArchiveIndex.Manifest manifest : index.getManifests()) {
			if (manifest.getMediaType().startsWith("application/vnd.docker.distribution.manifest.list.v")
					&& manifest.getMediaType().endsWith("+json")) {
				digests.add(manifest.getDigest());
			}
		}
		return Collections.unmodifiableSet(digests);
	}

	private static List<DistributionManifestList> getDistributionManifestLists(Path path, Set<String> digests)
			throws IOException {
		if (digests.isEmpty()) {
			return Collections.emptyList();
		}
		List<DistributionManifestList> distributionManifestLists = new ArrayList<>();
		try (TarArchiveInputStream tar = openTar(path)) {
			TarArchiveEntry entry = tar.getNextTarEntry();
			while (entry != null) {
				if (isDigestMatch(entry, digests)) {
					distributionManifestLists.add(readJson(tar, DistributionManifestList::of));
				}
				entry = tar.getNextTarEntry();
			}
		}
		return Collections.unmodifiableList(distributionManifestLists);
	}

	private List<DistributionManifest> getDistributionManifests(Path path,
			List<DistributionManifestList> distributionManifestLists) throws IOException {
		if (distributionManifestLists.isEmpty()) {
			return Collections.emptyList();
		}
		Set<String> digests = new HashSet<>();
		for (DistributionManifestList manifestList : distributionManifestLists) {
			for (DistributionManifestList.Manifest manifest : manifestList.getManifests()) {
				if (manifest.getMediaType().startsWith("application/vnd.docker.distribution.manifest.v")
						&& manifest.getMediaType().endsWith("+json")) {
					digests.add(manifest.getDigest());
				}
			}
		}
		List<DistributionManifest> distributionManifests = new ArrayList<>();
		try (TarArchiveInputStream tar = openTar(path)) {
			TarArchiveEntry entry = tar.getNextTarEntry();
			while (entry != null) {
				if (isDigestMatch(entry, digests)) {
					distributionManifests.add(readJson(tar, DistributionManifest::of));
				}
				entry = tar.getNextTarEntry();
			}
		}
		return Collections.unmodifiableList(distributionManifests);
	}

	private static boolean isDigestMatch(TarArchiveEntry entry, Set<String> digests) {
		String hash = getEntryHash(entry);
		return digests.contains(hash);
	}

	private static String getEntryHash(TarArchiveEntry entry) {
		String entryName = entry.getName();
		if (entryName.startsWith("blobs/")) {
			String hash = entryName.substring("blobs/".length());
			hash = hash.replace('/', ':');
			return hash.contains(":") ? hash : null;
		}
		return null;
	}

	private static <T> T readJson(TarArchiveInputStream in, ThrowingFunction<InputStream, T> factory) {
		String content = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
			.collect(Collectors.joining());
		return factory.apply(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}

	void export(IOBiConsumer<String, TarArchive> exports) throws IOException {
		try (TarArchiveInputStream tar = openTar(this.path)) {
			TarArchiveEntry entry = tar.getNextTarEntry();
			while (entry != null) {
				if (isLayer(entry)) {
					Compression compression = getLayerCompression(entry);
					exports.accept(entry.getName(), TarArchive.fromInputStream(tar, compression));
				}
				entry = tar.getNextTarEntry();
			}
		}
	}

	private Compression getLayerCompression(TarArchiveEntry entry) {
		String hash = getEntryHash(entry);
		String mediaType = this.layerDigestMediaTypes.get(hash);
		if (mediaType == null) {
			return Compression.NONE;
		}
		return switch (mediaType) {
			case "application/vnd.docker.image.rootfs.diff.tar.gzip" -> Compression.GZIP;
			default -> Compression.NONE;
		};
	}

	private boolean isLayer(TarArchiveEntry entry) {
		return this.manifest.getEntries().stream().anyMatch((content) -> content.getLayers().contains(entry.getName()));
	}

	private static TarArchiveInputStream openTar(Path path) throws IOException {
		return new TarArchiveInputStream(Files.newInputStream(path));
	}

	@Override
	public void close() throws IOException {
		Files.delete(this.path);
	}

}
