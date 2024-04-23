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

import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import org.springframework.boot.buildpack.platform.docker.type.ImageReference;

/**
 * @author Phillip Webb
 */
class Gh40100 {

	@Test
	void testName() throws IOException {
		DockerApi api = new DockerApi();
		ImageReference reference = ImageReference.of("gcr.io/paketo-buildpacks/adoptium:latest");
		api.image().pull(reference, UpdateListener.none());
		api.image().exportLayerFiles(reference, (name, path) -> {
			System.out.println(name);
			System.out.println(path);
			TarArchiveInputStream tarIn = new TarArchiveInputStream(Files.newInputStream(path));
			tarIn.getNextEntry();
		});
	}

}
