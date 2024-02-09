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

package org.springframework.boot.jarmode.layertools;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link ToolsJarMode}.
 *
 * @author Moritz Halbritter
 */
class ToolsJarModeTests {

	private ToolsJarMode mode;

	@TempDir
	private Path temp;

	@BeforeEach
	void setUp() {
		Context context = Mockito.mock(Context.class);
		given(context.getArchiveFile()).willReturn(this.temp.resolve("file.jar").toFile());
		ToolsJarMode.contextOverride = context;
		this.mode = new ToolsJarMode();
	}

	@Test
	void shouldAcceptToolsMode() {
		assertThat(this.mode.accepts("tools")).isTrue();
		assertThat(this.mode.accepts("something-else")).isFalse();
	}

	@Test
	void help() {
		run("help");
	}

	@Test
	void extract() {
		// run("extract", "--launcher", "--layers", "layer1,layer2", "--destination",
		// "/tmp");
	}

	private void run(String... args) {
		this.mode.run("tools", args);
	}

}
