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

package org.springframework.boot.test.autoconfigure.testcontainers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.testcontainers.containers.GenericContainer;

import org.springframework.boot.autoconfigure.serviceconnection.ServiceConnection;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.test.autoconfigure.serviceconnection.ServiceConnectionResolver;
import org.springframework.boot.test.autoconfigure.serviceconnection.ServiceConnectionSource;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ReflectionUtils;

/**
 * A {@link ServiceConnectionResolver} that resolves service connections from
 * {@link GenericContainer} fields.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @since 3.1.0
 */
public class GenericContainerServiceConnectionResolver implements ServiceConnectionResolver {

	private final List<GenericContainerServiceConnectionAdapter> adapters;

	GenericContainerServiceConnectionResolver() {
		this(SpringFactoriesLoader
			.forDefaultResourceLocation(GenericContainerServiceConnectionResolver.class.getClassLoader()));
	}

	GenericContainerServiceConnectionResolver(SpringFactoriesLoader loader) {
		this.adapters = loader.load(GenericContainerServiceConnectionAdapter.class);
	}

	@Override
	public Iterable<ServiceConnection> resolveConnections(Class<?> testClass) {
		List<ServiceConnection> connections = new ArrayList<>();
		List<GenericContainerServiceConnectionSource> sources = findSources(testClass);
		for (GenericContainerServiceConnectionSource source : sources) {
			List<ServiceConnection> connectionsForSource = adapt(source);
			if (connectionsForSource.isEmpty()) {
				throw new IllegalStateException("No service connections could be created from source '" + source + "'");
			}
			connections.addAll(connectionsForSource);
		}
		return connections;
	}

	@SuppressWarnings("unchecked")
	private List<GenericContainerServiceConnectionSource> findSources(Class<?> testClass) {
		List<GenericContainerServiceConnectionSource> fieldSources = new ArrayList<>();
		ReflectionUtils.doWithFields(testClass, (field) -> {
			MergedAnnotations annotations = MergedAnnotations.from(field);
			annotations.stream(ServiceConnectionSource.class)
				.map((serviceConnectionSource) -> (Class<? extends ServiceConnection>) serviceConnectionSource
					.getClass("value"))
				.map((connectionType) -> createSource(field, connectionType))
				.forEach(fieldSources::add);
		}, (field) -> GenericContainer.class.isAssignableFrom(field.getType()));
		return fieldSources;
	}

	private GenericContainerServiceConnectionSource createSource(Field field,
			Class<? extends ServiceConnection> connectionType) {
		ReflectionUtils.makeAccessible(field);
		GenericContainer<?> container = (GenericContainer<?>) ReflectionUtils.getField(field, null);
		return new GenericContainerServiceConnectionSource(container, field.getName() + "ServiceConnection",
				new FieldOrigin(field), connectionType);
	}

	private List<ServiceConnection> adapt(GenericContainerServiceConnectionSource source) {
		return this.adapters.stream().map((adapter) -> adapter.adapt(source)).filter(Objects::nonNull).toList();
	}

	private static class FieldOrigin implements Origin {

		private final Field field;

		FieldOrigin(Field field) {
			this.field = field;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			FieldOrigin other = (FieldOrigin) obj;
			return Objects.equals(this.field, other.field);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.field);
		}

		@Override
		public String toString() {
			return this.field.toString();
		}

	}

}
