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
 * Application title.
 *
 * @author Moritz Halbritter
 * @since 3.4.0
 */
public class ApplicationTitle {

	private final String title;

	public ApplicationTitle(Class<?> applicationClass) {
		this(readTitle(applicationClass));
	}

	protected ApplicationTitle(String title) {
		this.title = title;
	}

	/**
	 * Returns the application title or {@code null}.
	 * @return the application title or {@code null}
	 */
	public String asString() {
		return this.title;
	}

	/**
	 * Whether the application title is available.
	 * @return whether the application title is available
	 */
	public boolean isAvailable() {
		return this.title != null;
	}

	@Override
	public String toString() {
		return (this.title != null) ? this.title : "???";
	}

	private static String readTitle(Class<?> applicationClass) {
		Package sourcePackage = (applicationClass != null) ? applicationClass.getPackage() : null;
		return (sourcePackage != null) ? sourcePackage.getImplementationTitle() : null;
	}

}
