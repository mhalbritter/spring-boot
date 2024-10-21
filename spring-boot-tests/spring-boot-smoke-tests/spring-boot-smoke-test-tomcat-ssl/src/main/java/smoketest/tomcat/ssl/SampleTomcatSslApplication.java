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

package smoketest.tomcat.ssl;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.jks.JksSslStoreBundle;
import org.springframework.boot.ssl.jks.JksSslStoreDetails;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class SampleTomcatSslApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(SampleTomcatSslApplication.class, args);
		DefaultSslBundleRegistry bundleRegistry = context.getBean(DefaultSslBundleRegistry.class);
		new Thread(() -> {
			SslBundle sslDemo = bundleRegistry.getBundle("ssldemo");
			SslBundle chains = bundleRegistry.getBundle("chains");
			SslBundle newBundle = SslBundle
				.of(new JksSslStoreBundle(new JksSslStoreDetails("PKCS12", null, "classpath:new.p12", "secret"), null));
			// Switch to ssldemo to chains bundle
			sleep(Duration.ofSeconds(10));
			bundleRegistry.updateBundle("ssldemo", chains);
			// Restore old ssldemo bundle
			sleep(Duration.ofSeconds(10));
			bundleRegistry.updateBundle("ssldemo", sslDemo);
			// Add new bundle
			sleep(Duration.ofSeconds(10));
			bundleRegistry.registerBundle("new", newBundle);
		}).start();
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException ex) {
			throw new RuntimeException(ex);
		}
	}

}
