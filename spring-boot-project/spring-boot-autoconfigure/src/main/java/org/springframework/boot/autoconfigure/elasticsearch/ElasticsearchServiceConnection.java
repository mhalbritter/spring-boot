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

package org.springframework.boot.autoconfigure.elasticsearch;

import java.util.List;

import org.springframework.boot.autoconfigure.ServiceConnection;

/**
 * A connection to an Elasticsearch service.
 *
 * @author Moritz Halbritter
 * @since 3.1.0
 */
public interface ElasticsearchServiceConnection extends ServiceConnection {

	/**
	 * Comma-separated list of the Elasticsearch instances to use.
	 * @return comma-separated list of the Elasticsearch instances to use
	 */
	List<String> getUris();

	/**
	 * Username for authentication with Elasticsearch.
	 * @return username for authentication with Elasticsearch
	 */
	String getUsername();

	/**
	 * Password for authentication with Elasticsearch.
	 * @return password for authentication with Elasticsearch
	 */
	String getPassword();

	/**
	 * Prefix added to the path of every request sent to Elasticsearch.
	 * @return prefix added to the path of every request sent to Elasticsearch
	 */
	String getPathPrefix();

}
