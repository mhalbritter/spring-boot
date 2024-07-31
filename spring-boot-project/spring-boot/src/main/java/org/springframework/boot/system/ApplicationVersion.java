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

package org.springframework.boot.system;

/**
 * Application version.
 *
 * @author Moritz Halbritter
 * @since 3.4.0
 */
public class ApplicationVersion {

	private final String version;

	public ApplicationVersion(Class<?> applicationClass) {
		this(readVersion(applicationClass));
	}

	protected ApplicationVersion(String version) {
		this.version = version;
	}

	/**
	 * Returns the application version or {@code null}.
	 * @return the application version or {@code null}
	 */
	public String asString() {
		return this.version;
	}

	/**
	 * Whether the application version is available.
	 * @return whether the application version is available
	 */
	public boolean isAvailable() {
		return this.version != null;
	}

	@Override
	public String toString() {
		return (this.version != null) ? this.version : "???";
	}

	private static String readVersion(Class<?> applicationClass) {
		Package sourcePackage = (applicationClass != null) ? applicationClass.getPackage() : null;
		return (sourcePackage != null) ? sourcePackage.getImplementationVersion() : null;
	}

}
