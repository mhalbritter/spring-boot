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

package org.springframework.boot.autoconfigure.thread;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Virtual thread support.
 *
 * @author Moritz Halbritter
 * @since 3.2.0
 */
public class VirtualThreads {

	private final Executor executor;

	VirtualThreads() {
		Method method = ReflectionUtils.findMethod(Executors.class, "newVirtualThreadPerTaskExecutor");
		Assert.notNull(method, "Executors.newVirtualThreadPerTaskExecutor() method is missing");
		this.executor = (Executor) ReflectionUtils.invokeMethod(method, null);
	}

	/**
	 * Returns the virtual thread executor.
	 * @return the virtual thread executor
	 */
	public Executor getExecutor() {
		return this.executor;
	}

}
