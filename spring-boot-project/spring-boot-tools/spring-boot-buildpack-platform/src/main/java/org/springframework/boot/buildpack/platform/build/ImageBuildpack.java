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

package org.springframework.boot.buildpack.platform.build;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import org.springframework.boot.buildpack.platform.build.BuildpackLayersMetadata.BuildpackLayerDetails;
import org.springframework.boot.buildpack.platform.docker.transport.DockerEngineException;
import org.springframework.boot.buildpack.platform.docker.type.Image;
import org.springframework.boot.buildpack.platform.docker.type.ImageReference;
import org.springframework.boot.buildpack.platform.docker.type.Layer;
import org.springframework.boot.buildpack.platform.docker.type.LayerId;
import org.springframework.boot.buildpack.platform.io.IOConsumer;
import org.springframework.util.StreamUtils;

/**
 * A {@link Buildpack} that references a buildpack contained in an OCI image.
 *
 * The reference must be an OCI image reference. The reference can optionally contain a
 * prefix {@code docker://} to unambiguously identify it as an image buildpack reference.
 *
 * @author Scott Frederick
 * @author Phillip Webb
 */
final class ImageBuildpack implements Buildpack {

	private static final String PREFIX = "docker://";

	private final BuildpackCoordinates coordinates;

	private final ExportedLayers exportedLayers;

	private ImageBuildpack(BuildpackResolverContext context, ImageReference imageReference) {
		ImageReference reference = imageReference.inTaggedOrDigestForm();
		try {
			Image image = context.fetchImage(reference, ImageType.BUILDPACK);
			BuildpackMetadata buildpackMetadata = BuildpackMetadata.fromImage(image);
			this.coordinates = BuildpackCoordinates.fromBuildpackMetadata(buildpackMetadata);
			this.exportedLayers = (!buildpackExistsInBuilder(context, image.getLayers()))
					? new ExportedLayers(context, reference) : null;
		}
		catch (IOException | DockerEngineException ex) {
			throw new IllegalArgumentException("Error pulling buildpack image '" + reference + "'", ex);
		}
	}

	private boolean buildpackExistsInBuilder(BuildpackResolverContext context, List<LayerId> imageLayers) {
		BuildpackLayerDetails buildpackLayerDetails = context.getBuildpackLayersMetadata()
			.getBuildpack(this.coordinates.getId(), this.coordinates.getVersion());
		String layerDiffId = (buildpackLayerDetails != null) ? buildpackLayerDetails.getLayerDiffId() : null;
		return (layerDiffId != null) && imageLayers.stream().map(LayerId::toString).anyMatch(layerDiffId::equals);
	}

	@Override
	public BuildpackCoordinates getCoordinates() {
		return this.coordinates;
	}

	@Override
	public void apply(IOConsumer<Layer> layers) throws IOException {
		if (this.exportedLayers != null) {
			this.exportedLayers.apply(layers);
		}
	}

	/**
	 * A {@link BuildpackResolver} compatible method to resolve image buildpacks.
	 * @param context the resolver context
	 * @param reference the buildpack reference
	 * @return the resolved {@link Buildpack} or {@code null}
	 */
	static Buildpack resolve(BuildpackResolverContext context, BuildpackReference reference) {
		boolean unambiguous = reference.hasPrefix(PREFIX);
		try {
			ImageReference imageReference = ImageReference
				.of((unambiguous) ? reference.getSubReference(PREFIX) : reference.toString());
			return new ImageBuildpack(context, imageReference);
		}
		catch (IllegalArgumentException ex) {
			if (unambiguous) {
				throw ex;
			}
			return null;
		}
	}

	enum FileType {

		TAR(new byte[] { 0x75, 0x73, 0x74, 0x61, 0x72 }, 257) {
			@Override
			InputStream wrap(InputStream stream) {
				return stream;
			}
		},
		TAR_GZIP(new byte[] { 0x1f, (byte) 0x8b }, 0) {
			@Override
			InputStream wrap(InputStream stream) throws IOException {
				return new GZIPInputStream(stream);
			}
		},
		TAR_ZSTD(new byte[] { 0x28, (byte) 0xb5, 0x2f, (byte) 0xfd }, 0) {
			@Override
			InputStream wrap(InputStream stream) {
				throw new IllegalStateException("zstd compression is not supported");
			}
		};

		private final int offset;

		private final byte[] magic;

		FileType(byte[] magic, int offset) {
			this.offset = offset;
			this.magic = magic;
		}

		abstract InputStream wrap(InputStream stream) throws IOException;

		static FileType detect(Path path) throws IOException {
			try (FileInputStream stream = new FileInputStream(path.toFile())) {
				for (FileType value : FileType.values()) {
					byte[] magic = new byte[value.magic.length];
					stream.getChannel().position(value.offset);
					if (stream.read(magic) == -1) {
						continue;
					}
					if (Arrays.equals(magic, value.magic)) {
						return value;
					}
				}
				return FileType.TAR;
			}
		}

	}

	private static class ExportedLayers {

		private final List<Path> layerFiles;

		ExportedLayers(BuildpackResolverContext context, ImageReference imageReference) throws IOException {
			List<Path> layerFiles = new ArrayList<>();
			context.exportImageLayers(imageReference, (name, path) -> layerFiles.add(copyToTemp(path)));
			this.layerFiles = Collections.unmodifiableList(layerFiles);
		}

		private Path copyToTemp(Path path) throws IOException {
			Path outputPath = Files.createTempFile("create-builder-scratch-", null);
			try (OutputStream out = Files.newOutputStream(outputPath)) {
				copyLayerTar(path, out);
			}
			return outputPath;
		}

		private void copyLayerTar(Path path, OutputStream out) throws IOException {
			FileType fileType = FileType.detect(path);
			try (TarArchiveInputStream tarIn = new TarArchiveInputStream(fileType.wrap(Files.newInputStream(path)));
					TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out)) {
				tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
				TarArchiveEntry entry = tarIn.getNextEntry();
				while (entry != null) {
					tarOut.putArchiveEntry(entry);
					StreamUtils.copy(tarIn, tarOut);
					tarOut.closeArchiveEntry();
					entry = tarIn.getNextEntry();
				}
				tarOut.finish();
			}
		}

		void apply(IOConsumer<Layer> layers) throws IOException {
			for (Path path : this.layerFiles) {
				layers.accept(Layer.fromTarArchive((out) -> {
					InputStream in = Files.newInputStream(path);
					StreamUtils.copy(in, out);
				}));
				Files.delete(path);
			}
		}

	}

}
