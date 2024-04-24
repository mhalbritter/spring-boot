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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
		Files.copy(inputStream, this.path);
		ImageArchiveManifest manifest = null;
		ImageArchiveIndex index = null;
		try (TarArchiveInputStream tar = openTar()) {
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
		Map<String, String> digestMediaTypes = new HashMap<>();
		List<String> distributionManifestListDigests = getDistributionManifestListDigests(index);
		List<DistributionManifestList> distributionManifestLists = getDistributionManifestLists(this.path,
				distributionManifestListDigests);
		List<DistributionManifest> distributionManifests = getDistributionManifests(this.path,
				distributionManifestLists);
		for (DistributionManifest distributionManifest : distributionManifests) {
			for (DistributionManifest.Layer layer : distributionManifest.getLayers()) {
				digestMediaTypes.put(layer.getDigest(), layer.getMediaType());
			}
		}
		return digestMediaTypes;
	}

	private static List<String> getDistributionManifestListDigests(ImageArchiveIndex index) {
		List<String> digests = new ArrayList<>();
		for (ImageArchiveIndex.Manifest manifest : index.getManifests()) {
			if (manifest.getMediaType().startsWith("application/vnd.docker.distribution.manifest.list.v")
					&& manifest.getMediaType().endsWith("+json")) {
				digests.add(manifest.getDigest());
			}
		}
		return digests;
	}

	private static List<DistributionManifestList> getDistributionManifestLists(Path path, List<String> digests)
			throws IOException {
		if (digests.isEmpty()) {
			return Collections.emptyList();
		}
		List<DistributionManifestList> distributionManifestLists = new ArrayList<>();
		try (TarArchiveInputStream tar = new TarArchiveInputStream(new FileInputStream(path.toFile()))) {
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
		List<String> digests = new ArrayList<>();
		for (DistributionManifestList manifestList : distributionManifestLists) {
			for (DistributionManifestList.Manifest manifest : manifestList.getManifests()) {
				if (manifest.getMediaType().startsWith("application/vnd.docker.distribution.manifest.v")
						&& manifest.getMediaType().endsWith("+json")) {
					digests.add(manifest.getDigest());
				}
			}
		}
		List<DistributionManifest> distributionManifests = new ArrayList<>();
		try (TarArchiveInputStream tar = new TarArchiveInputStream(new FileInputStream(path.toFile()))) {
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

	private static boolean isDigestMatch(TarArchiveEntry entry, List<String> digests) {
		throw new UnsupportedOperationException("Auto-generated method stub");
	}

	private static <T> T readJson(TarArchiveInputStream in, ThrowingFunction<InputStream, T> factory) {
		String content = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
			.collect(Collectors.joining());
		return factory.apply(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
	}

	void export(IOBiConsumer<String, TarArchive> exports) throws IOException {
		try (TarArchiveInputStream tar = openTar()) {
			TarArchiveEntry entry = tar.getNextTarEntry();
			while (entry != null) {
				if (isLayer(entry.getName())) {
					// FIXME if we're a regular layer
					// FIXME lookup from layerDigestMediaTypes
					Compression compression = Compression.NONE;
					exports.accept(entry.getName(), TarArchive.fromInputStream(tar, compression));
				}
				entry = tar.getNextTarEntry();
			}
		}
	}

	private boolean isLayer(String name) {
		// FIXME also check layerDigestMediaTypes?
		return this.manifest.getEntries().stream().anyMatch((content) -> content.getLayers().contains(name));
	}

	private TarArchiveInputStream openTar() throws IOException {
		return new TarArchiveInputStream(new FileInputStream(this.path.toFile()));
	}

	@Override
	public void close() throws IOException {
		Files.delete(this.path);
	}

}
