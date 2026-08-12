package com.diet.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Brings installations created before distributed session coordination up to the current schema.
 * The checks make the migration repeatable because {@code CREATE/ADD ... IF NOT EXISTS} is not
 * consistently available across supported MySQL versions.
 */
@Component
@Order(30)
public class SessionConsistencySchemaMigration implements ApplicationRunner {
    private static final String COLUMN_EXISTS_SQL = """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """;
    private static final String INDEX_EXISTS_SQL = """
            SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SessionConsistencySchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("diet_sessions", "version", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn("diet_messages", "request_id", "VARCHAR(128) NULL");
        ensureMessageIdempotencyIndex();
        ensureChatRequestTable();
    }

    private void ensureColumn(String table, String column, String definition) {
        if (!columnExists(table, column)) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            } catch (DataAccessException ex) {
                // Another replica can win the schema change between the existence check and ALTER.
                if (!columnExists(table, column)) {
                    throw ex;
                }
            }
        }
    }

    private void ensureMessageIdempotencyIndex() {
        if (!indexExists("diet_messages", "uk_message_request_role")) {
            try {
                jdbcTemplate.execute("CREATE UNIQUE INDEX uk_message_request_role "
                        + "ON diet_messages (session_id, request_id, role)");
            } catch (DataAccessException ex) {
                // The same index may have been created by another replica at the same time.
                if (!indexExists("diet_messages", "uk_message_request_role")) {
                    throw ex;
                }
            }
        }
    }

    private void ensureChatRequestTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS diet_chat_request (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    session_id VARCHAR(64) NOT NULL,
                    request_id VARCHAR(128) NOT NULL,
                    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    processing_token VARCHAR(64) NULL,
                    response_json JSON NULL,
                    trace_id VARCHAR(128) NULL,
                    failure_code VARCHAR(128) NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_chat_request_user_request (user_id, request_id),
                    INDEX idx_chat_request_status (status, updated_at),
                    INDEX idx_chat_request_session (session_id, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(COLUMN_EXISTS_SQL, Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbcTemplate.queryForObject(INDEX_EXISTS_SQL, Integer.class, table, index);
        return count != null && count > 0;
    }
}
