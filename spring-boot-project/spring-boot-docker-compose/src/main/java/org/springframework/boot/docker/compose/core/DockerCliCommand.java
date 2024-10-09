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

package org.springframework.boot.docker.compose.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.logging.LogLevel;

/**
 * Commands that can be executed by the {@link DockerCli}.
 *
 * @param <R> the response type
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 */
abstract sealed class DockerCliCommand<R> {

	private final Type type;

	private final LogLevel logLevel;

	private final Class<?> responseType;

	private final boolean listResponse;

	private final List<String> command;

	private DockerCliCommand(Type type, Class<?> responseType, boolean listResponse, String... command) {
		this(type, LogLevel.OFF, responseType, listResponse, command);
	}

	private DockerCliCommand(Type type, LogLevel logLevel, Class<?> responseType, boolean listResponse,
			String... command) {
		this.type = type;
		this.logLevel = logLevel;
		this.responseType = responseType;
		this.listResponse = listResponse;
		this.command = List.of(command);
	}

	Type getType() {
		return this.type;
	}

	LogLevel getLogLevel() {
		return this.logLevel;
	}

	List<String> getCommand() {
		return this.command;
	}

	@SuppressWarnings("unchecked")
	R deserialize(String json) {
		if (this.responseType == Void.class) {
			return null;
		}
		return (R) ((!this.listResponse) ? DockerJson.deserialize(json, this.responseType)
				: DockerJson.deserializeToList(json, this.responseType));
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		DockerCliCommand<?> other = (DockerCliCommand<?>) obj;
		boolean result = this.type == other.type;
		result = result && this.responseType == other.responseType;
		result = result && this.listResponse == other.listResponse;
		result = result && this.command.equals(other.command);
		return result;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.type, this.responseType, this.listResponse, this.command);
	}

	@Override
	public String toString() {
		return "DockerCliCommand [type=%s, responseType=%s, listResponse=%s, command=%s]".formatted(this.type,
				this.responseType, this.listResponse, this.command);
	}

	protected static String[] join(Collection<String> command, Collection<String> args) {
		List<String> result = new ArrayList<>(command);
		result.addAll(args);
		return result.toArray(String[]::new);
	}

	protected static String[] join(Collection<String> arguments, String... additionalArguments) {
		List<String> result = new ArrayList<>(arguments);
		result.addAll(Arrays.asList(additionalArguments));
		return result.toArray(String[]::new);
	}

	/**
	 * The {@code docker context} command.
	 */
	static final class Context extends DockerCliCommand<List<DockerCliContextResponse>> {

		Context() {
			super(Type.DOCKER, DockerCliContextResponse.class, true, "context", "ls", "--format={{ json . }}");
		}

	}

	/**
	 * The {@code docker inspect} command.
	 */
	static final class Inspect extends DockerCliCommand<List<DockerCliInspectResponse>> {

		Inspect(Collection<String> ids) {
			super(Type.DOCKER, DockerCliInspectResponse.class, true,
					join(List.of("inspect", "--format={{ json . }}"), ids));
		}

	}

	/**
	 * The {@code docker compose config} command.
	 */
	static final class ComposeConfig extends DockerCliCommand<DockerCliComposeConfigResponse> {

		ComposeConfig(List<String> arguments) {
			super(Type.DOCKER_COMPOSE, DockerCliComposeConfigResponse.class, false,
					join(arguments, "config", "--format=json"));
		}

	}

	/**
	 * The {@code docker compose ps} command.
	 */
	static final class ComposePs extends DockerCliCommand<List<DockerCliComposePsResponse>> {

		ComposePs(List<String> arguments) {
			super(Type.DOCKER_COMPOSE, DockerCliComposePsResponse.class, true, join(arguments, "ps", "--format=json"));
		}

	}

	/**
	 * The {@code docker compose up} command.
	 */
	static final class ComposeUp extends DockerCliCommand<Void> {

		/**
		 * Creates a new {@link ComposeUp} instance.
		 * @param logLevel the log level to use
		 * @param arguments the arguments to pass to Docker Compose
		 * @param commandArguments the arguments to pass to the up command
		 */
		ComposeUp(LogLevel logLevel, List<String> arguments, List<String> commandArguments) {
			super(Type.DOCKER_COMPOSE, logLevel, Void.class, false, getCommand(arguments, commandArguments));
		}

		private static String[] getCommand(List<String> arguments, List<String> commandArguments) {
			List<String> result = new ArrayList<>(arguments);
			result.add("up");
			result.add("--no-color");
			result.add("--detach");
			result.add("--wait");
			result.addAll(commandArguments);
			return result.toArray(String[]::new);
		}

	}

	/**
	 * The {@code docker compose down} command.
	 */
	static final class ComposeDown extends DockerCliCommand<Void> {

		/**
		 * Creates a new {@link ComposeDown} instance.
		 * @param timeout the timeout to use
		 * @param arguments the arguments to pass to Docker Compose
		 * @param commandArguments the arguments to pass to the down command
		 */
		ComposeDown(Duration timeout, List<String> arguments, List<String> commandArguments) {
			super(Type.DOCKER_COMPOSE, Void.class, false, getCommand(timeout, arguments, commandArguments));
		}

		private static String[] getCommand(Duration timeout, List<String> arguments, List<String> commandArguments) {
			List<String> command = new ArrayList<>(arguments);
			command.add("down");
			command.add("--timeout");
			command.add(Long.toString(timeout.toSeconds()));
			command.addAll(commandArguments);
			return command.toArray(String[]::new);
		}

	}

	/**
	 * The {@code docker compose start} command.
	 */
	static final class ComposeStart extends DockerCliCommand<Void> {

		/**
		 * Creates a new {@link ComposeStart} instance.
		 * @param logLevel the log level to use
		 * @param arguments the arguments to pass to Docker Compose
		 * @param commandArguments the arguments to pass to the start command
		 */
		ComposeStart(LogLevel logLevel, List<String> arguments, List<String> commandArguments) {
			super(Type.DOCKER_COMPOSE, logLevel, Void.class, false, getCommand(arguments, commandArguments));
		}

		private static String[] getCommand(List<String> arguments, List<String> commandArguments) {
			List<String> command = new ArrayList<>(arguments);
			command.add("start");
			command.addAll(commandArguments);
			return command.toArray(String[]::new);
		}

	}

	/**
	 * The {@code docker compose stop} command.
	 */
	static final class ComposeStop extends DockerCliCommand<Void> {

		/**
		 * Creates a new {@link ComposeStop} instance.
		 * @param timeout the timeout to use
		 * @param arguments the arguments to pass to Docker Compose
		 * @param commandArguments the arguments to pass to the stop command
		 */
		ComposeStop(Duration timeout, List<String> arguments, List<String> commandArguments) {
			super(Type.DOCKER_COMPOSE, Void.class, false, getCommand(timeout, arguments, commandArguments));
		}

		private static String[] getCommand(Duration timeout, List<String> arguments, List<String> commandArguments) {
			List<String> command = new ArrayList<>(arguments);
			command.add("stop");
			command.add("--timeout");
			command.add(Long.toString(timeout.toSeconds()));
			command.addAll(commandArguments);
			return command.toArray(String[]::new);
		}

	}

	/**
	 * Command Types.
	 */
	enum Type {

		/**
		 * A command executed using {@code docker}.
		 */
		DOCKER,

		/**
		 * A command executed using {@code docker compose} or {@code docker-compose}.
		 */
		DOCKER_COMPOSE

	}

}
