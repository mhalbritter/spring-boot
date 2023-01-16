/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.boot.build.bom.bomr;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.springframework.boot.build.bom.bomr.version.DependencyVersion;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * Uses the Maven Central search to look for versions.
 *
 * @author Moritz Halbritter
 */
class MavenSearchVersionResolver implements VersionResolver {

	private final RestTemplate restTemplate;

	private final ObjectMapper objectMapper;

	MavenSearchVersionResolver() {
		this.restTemplate = new RestTemplate(Collections.singletonList(new StringHttpMessageConverter()));
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public SortedSet<DependencyVersion> resolveVersions(String groupId, String artifactId) {
		Set<String> versions = new HashSet<>();

		String url = String.format(
				"https://search.maven.org/solrsearch/select?q=g:%s+AND+a:%s&core=gav&rows=100&wt=json", groupId,
				artifactId);
		String response = this.restTemplate.getForObject(url, String.class);
		JsonNode jsonNode;
		try {
			jsonNode = this.objectMapper.readTree(response);
		}
		catch (JsonProcessingException ex) {
			throw new RuntimeException(ex);
		}

		int versionsFound = jsonNode.get("response").get("numFound").intValue();
		if (versionsFound == 100) {
			// TODO Check that versionsFound is < 100, otherwise we need to paginate the
			// requests
			System.out.printf("WARNING: Found 100 results for %s:%s, there may be more!%n", artifactId, groupId);
		}

		ArrayNode docs = (ArrayNode) jsonNode.get("response").get("docs");
		for (JsonNode doc : docs) {
			String candidateGroupId = doc.get("g").textValue();
			String candidateArtifactId = doc.get("a").textValue();
			if (!candidateGroupId.equals(groupId) || !candidateArtifactId.equals(artifactId)) {
				throw new IllegalStateException("Search API returned wrong candidate: " + doc);
			}
			String version = doc.get("v").textValue();
			versions.add(version);
		}
		TreeSet<DependencyVersion> result = versions.stream().map(DependencyVersion::parse)
				.collect(Collectors.toCollection(TreeSet::new));
		System.out.printf("Versions for %s:%s: %s%n", artifactId, groupId, result);
		return result;
	}

}
