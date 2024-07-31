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

package org.springframework.boot;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.system.ApplicationPid;
import org.springframework.boot.system.ApplicationTitle;
import org.springframework.boot.system.ApplicationVersion;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * {@link PropertySource} which provides information about the application, like the
 * title, the PID or the version.
 *
 * @author Moritz Halbritter
 */
class ApplicationInfoPropertySource extends MapPropertySource {

	static final String NAME = "applicationInfo";

	ApplicationInfoPropertySource(Class<?> mainClass) {
		super(NAME, getProperties(mainClass));
	}

	private static Map<String, Object> getProperties(Class<?> mainClass) {
		Map<String, Object> result = new HashMap<>();
		ApplicationVersion applicationVersion = new ApplicationVersion(mainClass);
		if (applicationVersion.isAvailable()) {
			result.put("spring.application.version", applicationVersion.asString());
		}
		ApplicationTitle applicationTitle = new ApplicationTitle(mainClass);
		if (applicationTitle.isAvailable()) {
			result.put("spring.application.title", applicationTitle.asString());
		}
		ApplicationPid applicationPid = new ApplicationPid();
		if (applicationPid.isAvailable()) {
			result.put("spring.application.pid", applicationPid.asLong());
		}
		return result;
	}

}
