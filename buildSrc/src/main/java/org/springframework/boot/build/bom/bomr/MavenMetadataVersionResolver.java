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

import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.springframework.boot.build.bom.bomr.cache.HttpCache;
import org.springframework.boot.build.bom.bomr.cache.HttpCache.Cached;
import org.springframework.boot.build.bom.bomr.version.DependencyVersion;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * A {@link VersionResolver} that examines {@code maven-metadata.xml} to determine the
 * available versions.
 *
 * @author Andy Wilkinson
 */
final class MavenMetadataVersionResolver implements VersionResolver {

	private static final Logger LOGGER = LoggerFactory.getLogger(MavenMetadataVersionResolver.class);

	private final RestTemplate rest;

	private final Collection<String> repositoryUrls;

	private final HttpCache httpCache;

	MavenMetadataVersionResolver(Collection<String> repositoryUrls, HttpCache httpCache) {
		this(new RestTemplate(Collections.singletonList(new StringHttpMessageConverter())), repositoryUrls, httpCache);
	}

	MavenMetadataVersionResolver(RestTemplate restTemplate, Collection<String> repositoryUrls, HttpCache httpCache) {
		this.rest = restTemplate;
		this.repositoryUrls = repositoryUrls;
		this.httpCache = httpCache;
	}

	@Override
	public SortedSet<DependencyVersion> resolveVersions(String groupId, String artifactId) {
		Set<String> versions = new HashSet<>();
		for (String repositoryUrl : this.repositoryUrls) {
			versions.addAll(resolveVersions(groupId, artifactId, repositoryUrl));
		}
		return versions.stream().map(DependencyVersion::parse).collect(Collectors.toCollection(TreeSet::new));
	}

	private Set<String> resolveVersions(String groupId, String artifactId, String repositoryUrl) {
		Set<String> versions = new HashSet<>();
		String url = repositoryUrl + "/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
		try {
			String metadata = request(groupId, artifactId, repositoryUrl, url);
			Document metadataDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new InputSource(new StringReader(metadata)));
			NodeList versionNodes = (NodeList) XPathFactory.newInstance().newXPath()
					.evaluate("/metadata/versioning/versions/version", metadataDocument, XPathConstants.NODESET);
			for (int i = 0; i < versionNodes.getLength(); i++) {
				versions.add(versionNodes.item(i).getTextContent());
			}
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
				System.err.println("Failed to download maven-metadata.xml for " + groupId + ":" + artifactId + " from "
						+ url + ": " + ex.getMessage());
			}
		}
		catch (Exception ex) {
			System.err.println("Failed to resolve versions for module " + groupId + ":" + artifactId + " in repository "
					+ repositoryUrl + ": " + ex.getMessage());
		}
		return versions;
	}

	private String request(String groupId, String artifactId, String repositoryUrl, String url) {
		Cached cached = this.httpCache.load(url);
		HttpHeaders httpHeaders = new HttpHeaders();
		if (cached != null) {
			httpHeaders.set("If-None-Match", cached.getEtag());
		}
		HttpEntity<Void> request = new HttpEntity<>(null, httpHeaders);
		ResponseEntity<String> response = this.rest.exchange(url, HttpMethod.GET, request, String.class);
		String metadata;
		if (response.getStatusCode().equals(HttpStatus.NOT_MODIFIED)) {
			Assert.notNull(cached, "cached must be not null");
			metadata = cached.getContent();
			LOGGER.debug("Used cached version for {}:{} from {}", groupId, artifactId, repositoryUrl);
		}
		else {
			metadata = response.getBody();
			String newEtag = response.getHeaders().getFirst("etag");
			if (newEtag != null) {
				this.httpCache.store(url, newEtag, metadata);
			}
			LOGGER.debug("Using server version for {}:{} from {}", groupId, artifactId, repositoryUrl);
		}
		return metadata;
	}

}
