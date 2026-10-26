package com.estatecrm.crm_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Lead;
import java.util.UUID;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class DealRepositoryQueryTests {

    @Test
    void approvedProductQueryCompilesWithHibernateWithoutConnectingToDatabase() throws Exception {
        String hql = DealRepository.class.getMethod("hasOtherApprovedDeal", UUID.class, UUID.class)
                .getAnnotation(Query.class).value();
        var configuration = new Configuration()
                .addAnnotatedClass(Deal.class)
                .addAnnotatedClass(Lead.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setProperty("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl");
        try (var factory = configuration.buildSessionFactory()) {
            var parsed = factory.unwrap(SessionFactoryImplementor.class)
                    .getQueryEngine().getHqlTranslator().translate(hql, Boolean.class);
            assertThat(parsed).isNotNull();
        }
    }
}
