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

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.Banner.Mode;

/**
 * Spring application properties.
 *
 * @author Moritz Halbritter
 */
class ApplicationProperties {

	private boolean allowBeanDefinitionOverriding;

	private boolean allowCircularReferences;

	private Banner.Mode bannerMode = Banner.Mode.CONSOLE;

	private boolean keepAlive;

	private boolean lazyInitialization = false;

	private boolean logStartupInfo = true;

	private boolean registerShutdownHook = true;

	private Set<String> sources = new LinkedHashSet<>();

	private WebApplicationType webApplicationType;

	boolean isAllowBeanDefinitionOverriding() {
		return this.allowBeanDefinitionOverriding;
	}

	void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	boolean isAllowCircularReferences() {
		return this.allowCircularReferences;
	}

	void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	Mode getBannerMode() {
		return this.bannerMode;
	}

	void setBannerMode(Mode bannerMode) {
		this.bannerMode = bannerMode;
	}

	boolean isKeepAlive() {
		return this.keepAlive;
	}

	void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	boolean isLazyInitialization() {
		return this.lazyInitialization;
	}

	void setLazyInitialization(boolean lazyInitialization) {
		this.lazyInitialization = lazyInitialization;
	}

	boolean isLogStartupInfo() {
		return this.logStartupInfo;
	}

	void setLogStartupInfo(boolean logStartupInfo) {
		this.logStartupInfo = logStartupInfo;
	}

	boolean isRegisterShutdownHook() {
		return this.registerShutdownHook;
	}

	void setRegisterShutdownHook(boolean registerShutdownHook) {
		this.registerShutdownHook = registerShutdownHook;
	}

	Set<String> getSources() {
		return this.sources;
	}

	void setSources(Set<String> sources) {
		this.sources = sources;
	}

	WebApplicationType getWebApplicationType() {
		return this.webApplicationType;
	}

	void setWebApplicationType(WebApplicationType webApplicationType) {
		this.webApplicationType = webApplicationType;
	}

}
