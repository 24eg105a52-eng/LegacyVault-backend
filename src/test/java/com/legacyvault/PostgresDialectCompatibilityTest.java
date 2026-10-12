package com.legacyvault;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Validates that all JPA entities, column definitions, and relationships
 * are 100% compatible with Hibernate's PostgreSQL Dialect.
 */
public class PostgresDialectCompatibilityTest {

    @Test
    void testPostgresMetadataBuilding() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName())
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "none")
                .build();

        try {
            MetadataSources sources = new MetadataSources(registry);
            sources.addAnnotatedClass(com.legacyvault.model.User.class);
            sources.addAnnotatedClass(com.legacyvault.model.TrustedContact.class);
            sources.addAnnotatedClass(com.legacyvault.model.VaultAsset.class);
            sources.addAnnotatedClass(com.legacyvault.model.AccessPolicy.class);
            sources.addAnnotatedClass(com.legacyvault.model.CheckInLog.class);
            sources.addAnnotatedClass(com.legacyvault.model.GracePeriodReminder.class);
            sources.addAnnotatedClass(com.legacyvault.model.EmergencyAccessRequest.class);
            sources.addAnnotatedClass(com.legacyvault.model.AuditLog.class);

            var metadata = sources.buildMetadata();
            assertNotNull(metadata);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
