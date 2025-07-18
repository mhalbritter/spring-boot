/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Moritz Halbritter
 */
// TODO MH: Remove this when done with JSpecify
public class JSpecifyPackageFinder {

	private static final Pattern PACKAGE_NAME = Pattern.compile("package ([\\w.]+);");

	private final Path root;

	public JSpecifyPackageFinder(Path root) {
		this.root = root;
	}

	private void run() throws IOException {
		System.out.printf("= Nullness report for `%s`%n", this.root);
		List<Path> packageInfoFiles = findPackageInfoFiles();
		List<PackageInfo> packageInfos = readPackageInfos(packageInfoFiles);
		printMarkedPackages(packageInfos);
		printUnmarkedPackages(packageInfos);
	}

	public static void main(String[] args) throws IOException {
		Path root = findRoot();
		if (args.length > 0) {
			root = selectModule(root, args[0]);
		}
		new JSpecifyPackageFinder(root).run();
	}

	private static Path selectModule(Path root, String module) {
		return root.resolve(module);
	}

	private void printMarkedPackages(List<PackageInfo> packageInfos) {
		System.out.println("== Marked packages");
		for (PackageInfo packageInfo : packageInfos) {
			if (!packageInfo.nullMarked()) {
				continue;
			}
			System.out.println("* `" + packageInfo.name() + "`");
		}
	}

	private void printUnmarkedPackages(List<PackageInfo> packageInfos) {
		System.out.println("== Unmarked packages");
		for (PackageInfo packageInfo : packageInfos) {
			if (packageInfo.nullMarked()) {
				continue;
			}
			System.out.println("* `" + packageInfo.name() + "`");
		}
	}

	private List<PackageInfo> readPackageInfos(List<Path> packageInfoFiles) throws IOException {
		List<PackageInfo> result = new ArrayList<>(packageInfoFiles.size());
		for (Path packageInfoFile : packageInfoFiles) {
			String content = Files.readString(packageInfoFile);
			boolean nullMarked = content.contains("import org.jspecify.annotations.NullMarked;");
			Matcher matcher = PACKAGE_NAME.matcher(content);
			if (!matcher.find()) {
				throw new IllegalStateException("Unable to find package for file '%s'".formatted(packageInfoFile));
			}
			String name = matcher.group(1);
			result.add(new PackageInfo(name, nullMarked));
		}
		return result;
	}

	private List<Path> findPackageInfoFiles() throws IOException {
		try (Stream<Path> files = Files.find(this.root, Integer.MAX_VALUE,
				(file, attributes) -> isPackageInfoFile(file, attributes) && isMainSourceFile(file, attributes))) {
			return files.toList();
		}
	}

	private boolean isPackageInfoFile(Path path, BasicFileAttributes attributes) {
		return attributes.isRegularFile() && path.getFileName().toString().equals("package-info.java");
	}

	private boolean isMainSourceFile(Path path, BasicFileAttributes attributes) {
		return attributes.isRegularFile() && path.toString().contains("src/main/java/");
	}

	private static Path findRoot() {
		Path current = Path.of(".").toAbsolutePath();
		while (true) {
			Path gitDir = current.resolve(".git");
			if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
				return current.toAbsolutePath();
			}
			current = current.getParent();
		}
	}

	private record PackageInfo(String name, boolean nullMarked) {
	}

}
