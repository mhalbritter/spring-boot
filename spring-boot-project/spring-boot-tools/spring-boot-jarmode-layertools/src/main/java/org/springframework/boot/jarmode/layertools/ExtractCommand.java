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

package org.springframework.boot.jarmode.layertools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * The {@code 'extract'} tools command.
 *
 * @author Moritz Halbritter
 */
class ExtractCommand extends Command {

	private static final Option LAUNCHER_OPTION = Option.of("launcher", null, "");

	/**
	 * Option to extract layers.
	 */
	static final Option LAYERS_OPTION = Option.of("layers", "string list", "Layers to extract");

	/**
	 * Option to specify the destination to write to.
	 */
	static final Option DESTINATION_OPTION = Option.of("destination", "string", "The destination to extract files to");

	private final Context context;

	private final Layers layers;

	ExtractCommand(Context context) {
		this(context, null);
	}

	ExtractCommand(Context context, Layers layers) {
		super("extract", "TODO", Options.of(LAUNCHER_OPTION, LAYERS_OPTION, DESTINATION_OPTION), Parameters.none());
		this.context = context;
		this.layers = layers;
	}

	@Override
	public void run(Map<Option, String> options, List<String> parameters) {
		try {
			File destination = options.containsKey(DESTINATION_OPTION) ? new File(options.get(DESTINATION_OPTION))
					: this.context.getWorkingDir();
			List<ExtractedFile> extractedFiles;
			if (options.containsKey(LAYERS_OPTION)) {
				extractedFiles = extractWithLayers(destination, options.get(LAYERS_OPTION));
			}
			else {
				extractedFiles = extractWithoutLayers(destination);
			}
			if (options.containsKey(LAUNCHER_OPTION)) {
				extractLauncher(extractedFiles);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private List<ExtractedFile> extractWithLayers(File directory, String layersToExtract) throws IOException {
		return extractWithLayers(directory, StringUtils.commaDelimitedListToSet(layersToExtract));
	}

	private List<ExtractedFile> extractWithLayers(File directory, Set<String> layersToExtract) throws IOException {
		List<ExtractedFile> extractedFiles = new ArrayList<>();
		Layers layers = (this.layers != null) ? this.layers : Layers.get(this.context);
		createLayersDirectories(directory, layersToExtract, layers);
		try (ZipInputStream zip = new ZipInputStream(new FileInputStream(this.context.getArchiveFile()))) {
			ZipEntry entry = zip.getNextEntry();
			Assert.state(entry != null, "File '" + this.context.getArchiveFile().toString()
					+ "' is not compatible with layertools; ensure jar file is valid and launch script is not enabled");
			while (entry != null) {
				if (!entry.isDirectory()) {
					String layer = layers.getLayer(entry);
					if (layersToExtract.isEmpty() || layersToExtract.contains(layer)) {
						extractedFiles.add(write(zip, entry, layer, new File(directory, layer)));
					}
				}
				entry = zip.getNextEntry();
			}
		}
		return extractedFiles;
	}

	private void createLayersDirectories(File directory, Set<String> layersToExtract, Layers layers)
			throws IOException {
		for (String layer : layers) {
			if (layersToExtract.isEmpty() || layersToExtract.contains(layer)) {
				mkDirs(new File(directory, layer));
			}
		}
	}

	private ExtractedFile write(ZipInputStream zip, ZipEntry entry, String layer, File directory) throws IOException {
		String canonicalOutputPath = directory.getCanonicalPath() + File.separator;
		File file = new File(directory, entry.getName());
		String canonicalEntryPath = file.getCanonicalPath();
		Assert.state(canonicalEntryPath.startsWith(canonicalOutputPath),
				() -> "Entry '" + entry.getName() + "' would be written to '" + canonicalEntryPath
						+ "'. This is outside the output location of '" + canonicalOutputPath
						+ "'. Verify the contents of your archive.");
		mkParentDirs(file);
		try (OutputStream out = new FileOutputStream(file)) {
			StreamUtils.copy(zip, out);
		}
		try {
			Files.getFileAttributeView(file.toPath(), BasicFileAttributeView.class)
				.setTimes(entry.getLastModifiedTime(), entry.getLastAccessTime(), entry.getCreationTime());
		}
		catch (IOException ex) {
			// File system does not support setting time attributes. Continue.
		}
		return new ExtractedFile(layer, entry.getName(), file);
	}

	private void mkParentDirs(File file) throws IOException {
		mkDirs(file.getParentFile());
	}

	private void mkDirs(File file) throws IOException {
		if (!file.exists() && !file.mkdirs()) {
			throw new IOException("Unable to create directory " + file);
		}
	}

	private List<ExtractedFile> extractWithoutLayers(File directory) {
		// TODO
		return Collections.emptyList();
	}

	private void extractLauncher(List<ExtractedFile> extractedFiles) {
		// TODO
	}

	private record ExtractedFile(String layer, String zipEntryName, File destination) {
	}

}
