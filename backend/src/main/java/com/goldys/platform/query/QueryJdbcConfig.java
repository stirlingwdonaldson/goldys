package com.goldys.platform.query;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A dedicated {@link JdbcTemplate} for the relational query backend, with a statement-level query
 * timeout so a pathological plan cannot hold a connection (or the server) unbounded. The timeout is
 * a JDBC statement timeout, which PostgreSQL maps to {@code statement_timeout} — a genuine
 * server-side cancellation, not just a client-side guard.
 */
@Configuration
public class QueryJdbcConfig {

  @Bean("queryJdbcTemplate")
  JdbcTemplate queryJdbcTemplate(DataSource dataSource) {
    JdbcTemplate template = new JdbcTemplate(dataSource);
    template.setQueryTimeout(8); // seconds
    return template;
  }
}
