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

package org.springframework.boot.buildpack.platform.docker.type;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.springframework.boot.buildpack.platform.json.AbstractJsonTests;

/**
 * Tests for {@link DistributionManifestList}.
 *
 * @author Phillip Webb
 */
class DistributionManifestListTests extends AbstractJsonTests {

	@Test
	void loadJsonWhenHasManifests() throws IOException {
		String content = getContentAsString("distribution-manifest-list-with-manifests.json");
		DistributionManifestList manifestList = getManifestList(content);
	}

	@Test
	void loadJsonWhenHasLayers() throws IOException {
		String content = getContentAsString("distribution-manifest-list-with-layers.json");
		DistributionManifestList manifestList = getManifestList(content);
	}

	private DistributionManifestList getManifestList(String content) throws IOException {
		return new DistributionManifestList(getObjectMapper().readTree(content));
	}

}
