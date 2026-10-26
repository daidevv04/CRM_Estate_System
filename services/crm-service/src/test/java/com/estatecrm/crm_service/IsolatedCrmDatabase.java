package com.estatecrm.crm_service;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.support.TestPropertySourceUtils;

/** Every integration context gets its own empty schema; public data is never a test fixture. */
public class IsolatedCrmDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var env = context.getEnvironment();
        String url = env.getRequiredProperty("spring.datasource.url");
        String username = env.getRequiredProperty("spring.datasource.username");
        String password = env.getRequiredProperty("spring.datasource.password");
        String schema = "crm_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET LOCAL search_path TO " + schema);
            var resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:db/migration/V*__*.sql");
            Arrays.sort(resources, Comparator.comparingInt(resource -> Integer.parseInt(
                    resource.getFilename().substring(1, resource.getFilename().indexOf("__")))));
            for (var resource : resources) {
                String sql = resource.getContentAsString(StandardCharsets.UTF_8)
                        .replace("CREATE EXTENSION IF NOT EXISTS pgcrypto;", "");
                statement.execute(sql);
            }
            connection.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize isolated CRM test schema", e);
        }
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "spring.datasource.hikari.connection-init-sql=SET search_path TO " + schema,
                "spring.jpa.properties.hibernate.default_schema=" + schema);
        ((DefaultListableBeanFactory) context.getBeanFactory()).registerDisposableBean(schema, () -> {
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        });
    }
}
