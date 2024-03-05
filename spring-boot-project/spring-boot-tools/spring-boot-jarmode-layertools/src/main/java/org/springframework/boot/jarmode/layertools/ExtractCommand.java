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
import java.io.PrintStream;
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
import org.springframework.boot.jarmode.layertools.Layers.LayersNotEnabledException;
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
	static final Option LAUNCHER_OPTION = Option.of("launcher", null, "Whether to extract the Spring Boot launcher");

	/**
	 * Option to extract layers.
	 */
	static final Option LAYERS_OPTION = Option.of("layers", "string list", "Layers to extract", true);

	/**
	 * Option to specify the destination to write to.
	 */
	static final Option DESTINATION_OPTION = Option.of("destination", "string",
			"Directory to extract files to. Defaults to the current working directory");

	private static final Option LIBRARIES_DIRECTORY_OPTION = Option.of("libraries", "string",
			"Name of the libraries directory. Only applicable when not using --launcher. Defaults to lib/");

	private static final Option RUNNER_FILENAME_OPTION = Option.of("runner-filename", "string",
			"Name of the runner JAR file. Only applicable when not using --launcher. Defaults to runner.jar");

	private final Context context;

	private final Layers layers;

	ExtractCommand(Context context) {
		this(context, null);
	}

	ExtractCommand(Context context, Layers layers) {
		super("extract", "Extract the contents from the jar", Options.of(LAUNCHER_OPTION, LAYERS_OPTION,
				DESTINATION_OPTION, LIBRARIES_DIRECTORY_OPTION, RUNNER_FILENAME_OPTION), Parameters.none());
		this.context = context;
		this.layers = layers;
	}

	@Override
	void run(PrintStream out, Map<Option, String> options, List<String> parameters) {
		try {
			File destination = getWorkingDirectory(options);
			Layers layers = getLayers(options);
			Set<String> layersToExtract = getLayersToExtract(options);
			createLayersDirectories(destination, layersToExtract, layers);
			if (options.containsKey(LAUNCHER_OPTION)) {
				extract(destination, layers, layersToExtract);
			}
			else {
				JarStructure jarStructure = getJarStructure();
				extractLibraries(destination, layers, layersToExtract, jarStructure, options);
				createRunner(destination, jarStructure, layers, layersToExtract, options);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (LayersNotEnabledException ex) {
			printError(out, "Layers are not enabled");
		}
	}

	private void printError(PrintStream out, String message) {
		out.println("Error: " + message);
		out.println();
	}

	private void extractLibraries(File destination, Layers layers, Set<String> layersToExtract,
			JarStructure jarStructure, Map<Option, String> options) throws IOException {
		extract(destination, layers, layersToExtract, (zipEntry) -> {
			Entry entry = jarStructure.resolve(zipEntry);
			if (isType(entry, Type.LIBRARY)) {
				return getLibrariesDirectory(options) + entry.location();
			}
			return null;
		});
	}

	private static Object getLibrariesDirectory(Map<Option, String> options) {
		if (options.containsKey(LIBRARIES_DIRECTORY_OPTION)) {
			String value = options.get(LIBRARIES_DIRECTORY_OPTION);
			if (value.endsWith("/")) {
				return value;
			}
			return value + "/";
		}
		return "lib/";
	}

	private static Set<String> getLayersToExtract(Map<Option, String> options) {
		return StringUtils.commaDelimitedListToSet(options.get(LAYERS_OPTION));
	}

	private Layers getLayers(Map<Option, String> options) {
		if (options.containsKey(LAYERS_OPTION)) {
			return new RunnerAwareLayers(getLayersFromContext(), getRunnerFilename(options));
		}
		return Layers.none();
	}

	private File getWorkingDirectory(Map<Option, String> options) {
		if (options.containsKey(DESTINATION_OPTION)) {
			return new File(options.get(DESTINATION_OPTION));
		}
		return this.context.getWorkingDir();
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
				File targetDir = getLayerDirectory(directory, layer);
				write(stream, zipEntry, name, targetDir);
			}
		});
	}

	private static File getLayerDirectory(File directory, String layer) {
		if (layer == null) {
			return directory;
		}
		return new File(directory, layer);
	}

	private Layers getLayersFromContext() {
		if (this.layers != null) {
			return this.layers;
		}
		return Layers.get(this.context);
	}

	private void createRunner(File directory, JarStructure jarStructure, Layers layers, Set<String> layersToExtract,
			Map<Option, String> options) throws IOException {
		String runnerFileName = getRunnerFilename(options);
		String layer = layers.getLayer(runnerFileName);
		if (!shouldExtractLayer(layersToExtract, layer)) {
			return;
		}
		File targetDir = getLayerDirectory(directory, layer);
		File launcherJar = new File(targetDir, runnerFileName);
		Manifest manifest = jarStructure.createLauncherManifest((library) -> getLibrariesDirectory(options) + library);
		mkDirs(launcherJar.getParentFile());
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(launcherJar.toPath()), manifest)) {
			withZipEntries(this.context.getArchiveFile(), ((stream, zipEntry) -> {
				Entry entry = jarStructure.resolve(zipEntry);
				if (isType(entry, Type.APPLICATION_CLASS_OR_RESOURCE) && StringUtils.hasLength(entry.location())) {
					JarEntry jarEntry = createJarEntry(entry.location(), zipEntry);
					output.putNextEntry(jarEntry);
					StreamUtils.copy(stream, output);
					output.closeEntry();
				}
			}));
		}
	}

	private String getRunnerFilename(Map<Option, String> options) {
		if (options.containsKey(RUNNER_FILENAME_OPTION)) {
			return options.get(RUNNER_FILENAME_OPTION);
		}
		return "runner.jar";
	}

	private static boolean isType(Entry entry, Type type) {
		if (entry == null) {
			return false;
		}
		return entry.type() == type;
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
					() -> "File '%s' is not compatible; ensure jar file is valid and launch script is not enabled"
						.formatted(file));
			while (entry != null) {
				if (StringUtils.hasLength(entry.getName())) {
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

	private static final class RunnerAwareLayers implements Layers {

		private final Layers layers;

		private final String runnerFilename;

		RunnerAwareLayers(Layers layers, String runnerFilename) {
			this.layers = layers;
			this.runnerFilename = runnerFilename;
		}

		@Override
		public Iterator<String> iterator() {
			return this.layers.iterator();
		}

		@Override
		public String getLayer(String entryName) {
			if (this.runnerFilename.equals(entryName)) {
				return this.layers.getApplicationLayerName();
			}
			return this.layers.getLayer(entryName);
		}

	}

}
