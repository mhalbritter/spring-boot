/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.boot.undertow.servlet;

import java.io.File;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import io.undertow.servlet.api.SessionPersistenceManager.PersistentSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FileSessionPersistence}.
 *
 * @author Phillip Webb
 */
class FileSessionPersistenceTests {

	private File dir;

	private FileSessionPersistence persistence;

	private final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

	private final Date expiration = new Date(System.currentTimeMillis() + 10000);

	@BeforeEach
	void setup(@TempDir File tempDir) {
		this.dir = tempDir;
		this.dir.mkdir();
		this.persistence = new FileSessionPersistence(this.dir);
	}

	@Test
	void loadsNullForMissingFile() {
		Map<String, PersistentSession> attributes = this.persistence.loadSessionAttributes("test", this.classLoader);
		assertThat(attributes).isNull();
	}

	@Test
	void persistAndLoad() {
		Map<String, PersistentSession> sessionData = new LinkedHashMap<>();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("spring", "boot");
		PersistentSession session = new PersistentSession(this.expiration, data);
		sessionData.put("abc", session);
		this.persistence.persistSessions("test", sessionData);
		Map<String, PersistentSession> restored = this.persistence.loadSessionAttributes("test", this.classLoader);
		assertThat(restored).isNotNull();
		assertThat(restored.get("abc").getExpiration()).isEqualTo(this.expiration);
		assertThat(restored.get("abc").getSessionData()).containsEntry("spring", "boot");
	}

	@Test
	void dontRestoreExpired() {
		Date expired = new Date(System.currentTimeMillis() - 1000);
		Map<String, PersistentSession> sessionData = new LinkedHashMap<>();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("spring", "boot");
		PersistentSession session = new PersistentSession(expired, data);
		sessionData.put("abc", session);
		this.persistence.persistSessions("test", sessionData);
		Map<String, PersistentSession> restored = this.persistence.loadSessionAttributes("test", this.classLoader);
		assertThat(restored).isNotNull();
		assertThat(restored).doesNotContainKey("abc");
	}

	@Test
	void deleteFileOnClear() {
		File sessionFile = new File(this.dir, "test.session");
		Map<String, PersistentSession> sessionData = new LinkedHashMap<>();
		this.persistence.persistSessions("test", sessionData);
		assertThat(sessionFile).exists();
		this.persistence.clear("test");
		assertThat(sessionFile).doesNotExist();
	}

}
