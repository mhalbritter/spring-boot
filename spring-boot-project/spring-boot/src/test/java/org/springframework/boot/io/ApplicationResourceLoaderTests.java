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

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApplicationResourceLoader}.
 *
 * @author Moritz Halbritter
 */
class ApplicationResourceLoaderTests {

	@Test
	void shouldLoadAbsolutePath() throws IOException {
		Resource resource = new ApplicationResourceLoader().getResource("/root/file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasParent("/root").hasName("file.txt");
	}

	@Test
	void shouldLoadAbsolutePathWithWorkingDirectory() throws IOException {
		Resource resource = new ApplicationResourceLoader(getClass().getClassLoader(), Path.of("/working-directory"))
			.getResource("/root/file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasParent("/root").hasName("file.txt");
	}

	@Test
	void shouldLoadRelativeFilename() throws IOException {
		Resource resource = new ApplicationResourceLoader().getResource("file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasNoParent().hasName("file.txt");
	}

	@Test
	void shouldLoadRelativeFilenameWithWorkingDirectory() throws IOException {
		Resource resource = new ApplicationResourceLoader(getClass().getClassLoader(), Path.of("/working-directory"))
			.getResource("file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasParent("/working-directory").hasName("file.txt");
	}

	@Test
	void shouldLoadRelativePathWithWorkingDirectory() throws IOException {
		Resource resource = new ApplicationResourceLoader(getClass().getClassLoader(), Path.of("/working-directory"))
			.getResource("a/file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasParent("/working-directory/a").hasName("file.txt");
	}

	@Test
	void shouldLoadClasspathLocations() {
		Resource resource = new ApplicationResourceLoader().getResource("classpath:a-file");
		assertThat(resource.exists()).isTrue();
	}

	@Test
	void shouldLoadNonExistentClasspathLocations() {
		Resource resource = new ApplicationResourceLoader().getResource("classpath:doesnt-exist");
		assertThat(resource.exists()).isFalse();
	}

	@Test
	void shouldLoadClasspathLocationsWithWorkingDirectory() {
		Resource resource = new ApplicationResourceLoader(getClass().getClassLoader(), Path.of("/working-directory"))
			.getResource("classpath:a-file");
		assertThat(resource.exists()).isTrue();
	}

	@Test
	void shouldLoadNonExistentClasspathLocationsWithWorkingDirectory() {
		Resource resource = new ApplicationResourceLoader(getClass().getClassLoader(), Path.of("/working-directory"))
			.getResource("classpath:doesnt-exist");
		assertThat(resource.exists()).isFalse();
	}

	@Test
	void shouldLoadRelativeFileUris() throws IOException {
		Resource resource = new ApplicationResourceLoader().getResource("file:file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasNoParent().hasName("file.txt");
	}

	@Test
	void shouldLoadAbsoluteFileUris() throws IOException {
		Resource resource = new ApplicationResourceLoader().getResource("file:/file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasParent("/").hasName("file.txt");
	}

	@Test
	void shouldLoadRelativeFileUrisWithWorkingDirectory() throws IOException {
		Resource resource = new ApplicationResourceLoader(getClass().getClassLoader(), Path.of("/working-directory"))
			.getResource("file:file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasParent("/working-directory").hasName("file.txt");
	}

	@Test
	void shouldLoadAbsoluteFileUrisWithWorkingDirectory() throws IOException {
		Resource resource = new ApplicationResourceLoader(getClass().getClassLoader(), Path.of("/working-directory"))
			.getResource("file:/file.txt");
		assertThat(resource.isFile()).isTrue();
		assertThat(resource.getFile()).hasParent("/").hasName("file.txt");
	}

}
