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

package org.springframework.boot.io;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.springframework.core.io.ContextResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * A {@link DefaultResourceLoader} with any {@link ProtocolResolver ProtocolResolvers}
 * registered in a {@code spring.factories} file applied to it. Plain paths without a
 * qualifier will resolve to file system resources. This is different from
 * {@code DefaultResourceLoader}, which resolves unqualified paths to classpath resources.
 *
 * @author Scott Frederick
 * @author Moritz Halbritter
 * @since 3.3.0
 */
public class ApplicationResourceLoader extends DefaultResourceLoader {

	private final Path workingDirectory;

	/**
	 * Create a new {@code ApplicationResourceLoader}.
	 */
	public ApplicationResourceLoader() {
		this(null);
	}

	/**
	 * Create a new {@code ApplicationResourceLoader}.
	 * @param classLoader the {@link ClassLoader} to load class path resources with, or
	 * {@code null} for using the thread context class loader at the time of actual
	 * resource access
	 */
	public ApplicationResourceLoader(ClassLoader classLoader) {
		this(classLoader, null);
	}

	/**
	 * Create a new {@code ApplicationResourceLoader} with the given working directory.
	 * @param classLoader the {@link ClassLoader} to load class path resources with, or
	 * {@code null} for using the thread context class loader at the time of actual
	 * resource access
	 * @param workingDirectory the working directory to resolve relative filenames against
	 * @since 3.4.0
	 */
	public ApplicationResourceLoader(ClassLoader classLoader, Path workingDirectory) {
		super(classLoader);
		SpringFactoriesLoader loader = SpringFactoriesLoader.forDefaultResourceLocation(classLoader);
		getProtocolResolvers().addAll(loader.load(ProtocolResolver.class));
		this.workingDirectory = workingDirectory;
	}

	@Override
	public Resource getResource(String location) {
		Resource resource = super.getResource(location);
		if (this.workingDirectory == null) {
			return resource;
		}
		if (!resource.isFile()) {
			return resource;
		}
		return resolveFile(resource);
	}

	private Resource resolveFile(Resource resource) {
		try {
			File file = resource.getFile();
			if (file.isAbsolute()) {
				return resource;
			}
			return new FileSystemContextResource(new File(this.workingDirectory.toFile(), file.getPath()).getPath());
		}
		// TODO: Catch the FileNotFOundException from .getFile here
		// TODO: Create issue on framework for working directory on DefaultResourceLoader
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	protected Resource getResourceByPath(String path) {
		return new FileSystemContextResource(path);
	}

	private static class FileSystemContextResource extends FileSystemResource implements ContextResource {

		FileSystemContextResource(String path) {
			super(path);
		}

		@Override
		public String getPathWithinContext() {
			return getPath();
		}

	}

}
