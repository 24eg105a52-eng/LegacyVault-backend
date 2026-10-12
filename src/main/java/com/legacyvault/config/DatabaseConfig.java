package com.legacyvault.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Intelligent DataSource configuration supporting Render PostgreSQL deployments
 * via DATABASE_URL or individual DB_* variables, while preserving local development
 * configurations (MySQL / local database).
 */
@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceProperties properties,
            @Value("${DATABASE_URL:#{null}}") String renderDatabaseUrl,
            @Value("${DB_HOST:#{null}}") String dbHost,
            @Value("${DB_PORT:#{null}}") String dbPort,
            @Value("${DB_NAME:#{null}}") String dbName,
            @Value("${DB_USERNAME:#{null}}") String dbUser,
            @Value("${DB_PASSWORD:#{null}}") String dbPass) {

        HikariConfig hikariConfig = new HikariConfig();

        // 1. Highest Priority: Render's DATABASE_URL
        if (StringUtils.hasText(renderDatabaseUrl)) {
            logger.info("[DatabaseConfig] Configuring PostgreSQL DataSource from Render DATABASE_URL...");
            configureFromRenderDatabaseUrl(hikariConfig, renderDatabaseUrl.trim());
        }
        // 2. Individual DB_* environment variables
        else if (StringUtils.hasText(dbHost) && StringUtils.hasText(dbName)) {
            logger.info("[DatabaseConfig] Configuring PostgreSQL DataSource from DB_HOST/DB_NAME environment variables...");
            int port = StringUtils.hasText(dbPort) ? Integer.parseInt(dbPort.trim()) : 5432;
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=prefer", dbHost.trim(), port, dbName.trim());
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setDriverClassName("org.postgresql.Driver");
            if (StringUtils.hasText(dbUser)) {
                hikariConfig.setUsername(dbUser.trim());
            }
            if (StringUtils.hasText(dbPass)) {
                hikariConfig.setPassword(dbPass);
            }
        }
        // 3. Fallback to standard Spring datasource properties (local MySQL or local dev profile)
        else {
            logger.info("[DatabaseConfig] Configuring DataSource from standard application properties...");
            hikariConfig.setJdbcUrl(properties.determineUrl());
            hikariConfig.setUsername(properties.determineUsername());
            hikariConfig.setPassword(properties.determinePassword());
            if (StringUtils.hasText(properties.getDriverClassName())) {
                hikariConfig.setDriverClassName(properties.getDriverClassName());
            }
        }

        // Connection pool defaults suitable for cloud web services and local development
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);

        return new HikariDataSource(hikariConfig);
    }

    /**
     * Parses standard Render PostgreSQL connection URLs:
     * e.g. postgres://user:password@host:port/database or postgresql://...
     * and normalizes to standard JDBC format: jdbc:postgresql://host:port/database?sslmode=require
     */
    public static void configureFromRenderDatabaseUrl(HikariConfig config, String url) {
        try {
            if (url.startsWith("jdbc:")) {
                config.setJdbcUrl(url);
                config.setDriverClassName("org.postgresql.Driver");
                return;
            }

            URI uri = new URI(url);
            String userInfo = uri.getUserInfo();
            String username = null;
            String password = null;

            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                if (parts.length > 1) {
                    password = parts[1];
                }
            }

            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }

            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=require", host, port, path);
            config.setJdbcUrl(jdbcUrl);
            config.setDriverClassName("org.postgresql.Driver");
            if (username != null) {
                config.setUsername(username);
            }
            if (password != null) {
                config.setPassword(password);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Render DATABASE_URL provided: " + e.getMessage(), e);
        }
    }
}
