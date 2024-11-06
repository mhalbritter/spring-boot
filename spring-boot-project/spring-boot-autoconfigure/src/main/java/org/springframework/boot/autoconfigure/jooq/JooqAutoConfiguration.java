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

package org.springframework.boot.autoconfigure.jooq;

import java.io.IOException;
import java.io.InputStream;

import javax.sql.DataSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.jooq.ConnectionProvider;
import org.jooq.DSLContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.TransactionProvider;
import org.jooq.conf.Settings;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for JOOQ.
 *
 * @author Andreas Ahlenstorf
 * @author Michael Simons
 * @author Dmytro Nosan
 * @author Moritz Halbritter
 * @since 1.3.0
 */
@AutoConfiguration(after = { DataSourceAutoConfiguration.class, TransactionAutoConfiguration.class })
@ConditionalOnClass(DSLContext.class)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(JooqProperties.class)
public class JooqAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ConnectionProvider.class)
	public DataSourceConnectionProvider dataSourceConnectionProvider(DataSource dataSource) {
		return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource));
	}

	@Bean
	@ConditionalOnBean(PlatformTransactionManager.class)
	@ConditionalOnMissingBean(TransactionProvider.class)
	public SpringTransactionProvider transactionProvider(PlatformTransactionManager txManager) {
		return new SpringTransactionProvider(txManager);
	}

	@Bean
	@Order(0)
	public DefaultExecuteListenerProvider jooqExceptionTranslatorExecuteListenerProvider(
			ExceptionTranslatorExecuteListener exceptionTranslatorExecuteListener) {
		return new DefaultExecuteListenerProvider(exceptionTranslatorExecuteListener);
	}

	@Bean
	@ConditionalOnMissingBean
	public ExceptionTranslatorExecuteListener jooqExceptionTranslator() {
		return ExceptionTranslatorExecuteListener.DEFAULT;
	}

	@Bean
	@ConditionalOnMissingBean(DSLContext.class)
	public DefaultDSLContext dslContext(org.jooq.Configuration configuration) {
		return new DefaultDSLContext(configuration);
	}

	@Bean
	@ConditionalOnMissingBean(org.jooq.Configuration.class)
	DefaultConfiguration jooqConfiguration(JooqProperties properties, ConnectionProvider connectionProvider,
			DataSource dataSource, ObjectProvider<TransactionProvider> transactionProvider,
			ObjectProvider<ExecuteListenerProvider> executeListenerProviders,
			ObjectProvider<DefaultConfigurationCustomizer> configurationCustomizers,
			ObjectProvider<Settings> settingsProvider) {
		DefaultConfiguration configuration = new DefaultConfiguration();
		configuration.set(properties.determineSqlDialect(dataSource));
		configuration.set(connectionProvider);
		transactionProvider.ifAvailable(configuration::set);
		settingsProvider.ifAvailable(configuration::set);
		configuration.set(executeListenerProviders.orderedStream().toArray(ExecuteListenerProvider[]::new));
		configurationCustomizers.orderedStream().forEach((customizer) -> customizer.customize(configuration));
		return configuration;
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = "spring.jooq", name = "config")
	@ConditionalOnClass(JAXBContext.class)
	static class SettingsConfiguration {

		@Bean
		@ConditionalOnMissingBean
		Settings settings(JooqProperties properties) throws IOException {
			Resource resource = properties.getConfig();
			Assert.state(resource.exists(),
					() -> "Resource %s set in spring.jooq.config does not exist".formatted(resource));
			try (InputStream stream = resource.getInputStream()) {
				return new JaxbSettingsLoader().load(stream);
			}
		}

	}

	private static final class JaxbSettingsLoader {

		Settings load(InputStream inputStream) {
			try {
				// See
				// https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxb-unmarshaller
				SAXParserFactory spf = SAXParserFactory.newInstance();
				spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
				spf.setNamespaceAware(true);
				spf.setXIncludeAware(false);
				Source xmlSource = new SAXSource(spf.newSAXParser().getXMLReader(), new InputSource(inputStream));
				JAXBContext jc = JAXBContext.newInstance(Settings.class);
				Unmarshaller um = jc.createUnmarshaller();
				return um.unmarshal(xmlSource, Settings.class).getValue();
			}
			catch (ParserConfigurationException | JAXBException | SAXException ex) {
				throw new IllegalStateException("Failed to unmarshal settings", ex);
			}
		}

	}

}
