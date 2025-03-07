/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.boot.web.embedded.tomcat;

import org.apache.catalina.core.AprLifecycleListener;

import org.springframework.boot.system.JavaVersion;

/**
 * APR availability.
 *
 * @author Moritz Halbritter
 * @since 3.4.4
 */
public enum AprAvailability {

	/**
	 * APR is available.
	 */
	AVAILABLE,
	/**
	 * APR is not available.
	 */
	NOT_AVAILABLE,
	/**
	 * Auto-detect APR availability. On Java 24 and later auto-detection is disabled and
	 * is equivalent to {@link #NOT_AVAILABLE}.
	 */
	AUTO_DETECT;

	/**
	 * Determines whether APR is available.
	 * @return whether APR is available
	 */
	public boolean isAvailable() {
		return switch (this) {
			case AVAILABLE -> true;
			case NOT_AVAILABLE -> false;
			case AUTO_DETECT -> java24OrLater() ? false : AprLifecycleListener.isAprAvailable();
		};
	}

	private boolean java24OrLater() {
		return JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.TWENTY_FOUR);
	}

}
