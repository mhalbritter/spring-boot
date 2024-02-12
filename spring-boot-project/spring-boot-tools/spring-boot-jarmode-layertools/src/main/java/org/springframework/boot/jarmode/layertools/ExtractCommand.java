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
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.boot.jarmode.layertools.JarStructure.Entry;
import org.springframework.boot.jarmode.layertools.JarStructure.Entry.Type;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * The {@code 'extract'} tools command.
 *
 * @author Moritz Halbritter
 */
class ExtractCommand extends Command {

	/**
	 * Option to create a launcher.
	 */
	private static final Option LAUNCHER_OPTION = Option.of("launcher", null, "");

	/**
	 * Option to extract layers.
	 */
	static final Option LAYERS_OPTION = Option.of("layers", "string list", "Layers to extract", true);

	/**
	 * Option to specify the destination to write to.
	 */
	static final Option DESTINATION_OPTION = Option.of("destination", "string", "The destination to extract files to");

	private static final String LIBRARIES_DIRECTORY = "lib/";

	private static final String LAUNCHER_FILENAME = "launcher.jar";

	private final Context context;

	private final Layers layers;

	ExtractCommand(Context context) {
		this(context, null);
	}

	ExtractCommand(Context context, Layers layers) {
		super("extract", "Extract the contents from the jar",
				Options.of(LAUNCHER_OPTION, LAYERS_OPTION, DESTINATION_OPTION), Parameters.none());
		this.context = context;
		this.layers = layers;
	}

	@Override
	public void run(Map<Option, String> options, List<String> parameters) {
		try {
			File destination = options.containsKey(DESTINATION_OPTION) ? new File(options.get(DESTINATION_OPTION))
					: this.context.getWorkingDir();
			Layers layers = options.containsKey(LAYERS_OPTION) ? new LauncherAwareLayers(getLayersFromContext())
					: new NoLayers();
			Set<String> layersToExtract = StringUtils.commaDelimitedListToSet(options.get(LAYERS_OPTION));
			createLayersDirectories(destination, layersToExtract, layers);
			if (options.containsKey(LAUNCHER_OPTION)) {
				JarStructure jarStructure = getJarStructure();
				extract(destination, layers, layersToExtract, (zipEntry) -> {
					Entry entry = jarStructure.resolve(zipEntry);
					if (entry == null || entry.type() != Type.LIBRARY) {
						return null;
					}
					return LIBRARIES_DIRECTORY + entry.location();
				});
				createLauncher(destination, jarStructure, layers, layersToExtract);
			}
			else {
				extract(destination, layers, layersToExtract);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private JarStructure getJarStructure() {
		IndexedJarStructure jarStructure = IndexedJarStructure.get(this.context);
		Assert.state(jarStructure != null, "Couldn't read classpath index");
		return jarStructure;
	}

	private void createLayersDirectories(File directory, Set<String> layersToExtract, Layers layers)
			throws IOException {
		for (String layer : layers) {
			if (shouldExtractLayer(layersToExtract, layer)) {
				mkDirs(new File(directory, layer));
			}
		}
	}

	private void write(ZipInputStream zip, ZipEntry entry, String entryName, File directory) throws IOException {
		File file = new File(directory, entryName);
		assertFileIsContainedInDirectory(directory, file, entry);
		mkDirs(file.getParentFile());
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
	}

	private void assertFileIsContainedInDirectory(File directory, File file, ZipEntry entry) throws IOException {
		String canonicalOutputPath = directory.getCanonicalPath() + File.separator;
		String canonicalEntryPath = file.getCanonicalPath();
		Assert.state(canonicalEntryPath.startsWith(canonicalOutputPath),
				() -> "Entry '" + entry.getName() + "' would be written to '" + canonicalEntryPath
						+ "'. This is outside the output location of '" + canonicalOutputPath
						+ "'. Verify the contents of your archive.");
	}

	private void mkDirs(File file) throws IOException {
		if (!file.exists() && !file.mkdirs()) {
			throw new IOException("Unable to create directory " + file);
		}
	}

	private void extract(File directory, Layers layers, Set<String> layersToExtract) throws IOException {
		extract(directory, layers, layersToExtract, ZipEntry::getName);
	}

	private void extract(File directory, Layers layers, Set<String> layersToExtract,
			EntryNameTransformer entryNameTransformer) throws IOException {
		withZipEntries(this.context.getArchiveFile(), (stream, zipEntry) -> {
			String name = entryNameTransformer.getName(zipEntry);
			if (name == null) {
				return;
			}
			String layer = layers.getLayer(zipEntry);
			if (shouldExtractLayer(layersToExtract, layer)) {
				File targetDir = (layer != null) ? new File(directory, layer) : directory;
				write(stream, zipEntry, name, targetDir);
			}
		});
	}

	private Layers getLayersFromContext() {
		return (this.layers != null) ? this.layers : Layers.get(this.context);
	}

	private void createLauncher(File directory, JarStructure jarStructure, Layers layers, Set<String> layersToExtract)
			throws IOException {
		String layer = layers.getLayer(LAUNCHER_FILENAME);
		if (!shouldExtractLayer(layersToExtract, layer)) {
			return;
		}
		File targetDir = (layer != null) ? new File(directory, layer) : directory;
		File launcherJar = new File(targetDir, LAUNCHER_FILENAME);
		Manifest manifest = jarStructure.createLauncherManifest((library) -> LIBRARIES_DIRECTORY + library);
		mkDirs(launcherJar.getParentFile());
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(launcherJar.toPath()), manifest)) {
			withZipEntries(this.context.getArchiveFile(), ((stream, zipEntry) -> {
				Entry entry = jarStructure.resolve(zipEntry);
				if (entry == null || entry.type() != Type.APPLICATION_CLASS_OR_RESOURCE) {
					return;
				}
				JarEntry jarEntry = createJarEntry(entry.location(), zipEntry);
				output.putNextEntry(jarEntry);
				StreamUtils.copy(stream, output);
				output.closeEntry();
			}));
		}
	}

	private boolean shouldExtractLayer(Set<String> layersToExtract, String layer) {
		if (layer == null || layersToExtract.isEmpty()) {
			return true;
		}
		return layersToExtract.contains(layer);
	}

	private static JarEntry createJarEntry(String location, ZipEntry originalEntry) {
		JarEntry jarEntry = new JarEntry(location);
		FileTime lastModifiedTime = originalEntry.getLastModifiedTime();
		if (lastModifiedTime != null) {
			jarEntry.setLastModifiedTime(lastModifiedTime);
		}
		FileTime lastAccessTime = originalEntry.getLastAccessTime();
		if (lastAccessTime != null) {
			jarEntry.setLastAccessTime(lastAccessTime);
		}
		FileTime creationTime = originalEntry.getCreationTime();
		if (creationTime != null) {
			jarEntry.setCreationTime(creationTime);
		}
		return jarEntry;
	}

	private static void withZipEntries(File file, ThrowingConsumer callback) throws IOException {
		try (ZipInputStream stream = new ZipInputStream(new FileInputStream(file))) {
			ZipEntry entry = stream.getNextEntry();
			Assert.state(entry != null,
					"File '" + file + "' is not compatible; ensure jar file is valid and launch script is not enabled");
			while (entry != null) {
				if (StringUtils.hasLength(entry.getName()) && !entry.isDirectory()) {
					callback.accept(stream, entry);
				}
				entry = stream.getNextEntry();
			}
		}
	}

	@FunctionalInterface
	private interface EntryNameTransformer {

		String getName(ZipEntry entry);

	}

	@FunctionalInterface
	private interface ThrowingConsumer {

		void accept(ZipInputStream stream, ZipEntry entry) throws IOException;

	}

	private static final class LauncherAwareLayers implements Layers {

		private static final String APPLICATION_LAYER = "application";

		private final Layers layers;

		LauncherAwareLayers(Layers layers) {
			this.layers = layers;
		}

		@Override
		public Iterator<String> iterator() {
			return this.layers.iterator();
		}

		@Override
		public String getLayer(String entryName) {
			if (LAUNCHER_FILENAME.equals(entryName)) {
				return APPLICATION_LAYER;
			}
			return this.layers.getLayer(entryName);
		}

	}

}
