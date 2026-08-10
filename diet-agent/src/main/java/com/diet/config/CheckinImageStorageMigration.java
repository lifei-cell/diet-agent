package com.diet.config;

import com.diet.service.storage.CheckinImageStorage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Migrates legacy check-in BLOB columns to MinIO object keys without losing existing images. */
@Component
@Order(20)
public class CheckinImageStorageMigration implements ApplicationRunner {
    private static final String COLUMN_EXISTS_SQL = """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CheckinImageStorage imageStorage;

    public CheckinImageStorageMigration(JdbcTemplate jdbcTemplate, CheckinImageStorage imageStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.imageStorage = imageStorage;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrate("diet_checkin_draft", "drafts", false);
        migrate("diet_checkin", "confirmed", true);
    }

    private void migrate(String table, String category, boolean numericId) {
        ensureObjectKeyColumn(table);
        if (columnExists(table, "image_data")) {
            List<LegacyImage> rows = jdbcTemplate.query(
                    "SELECT id, user_id, image_data, image_media_type FROM " + table
                            + " WHERE image_data IS NOT NULL AND (image_object_key IS NULL OR image_object_key = '')",
                    (resultSet, rowNum) -> new LegacyImage(
                            resultSet.getString("id"),
                            resultSet.getLong("user_id"),
                            resultSet.getBytes("image_data"),
                            resultSet.getString("image_media_type")
                    )
            );
            for (LegacyImage image : rows) {
                String objectKey = imageStorage.legacyObjectKey(category, image.userId(), image.id(), image.mediaType());
                imageStorage.store(objectKey, image.data(), image.mediaType());
                jdbcTemplate.update("UPDATE " + table + " SET image_object_key = ? WHERE id = ?", objectKey, image.id());
            }
            Integer missing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE image_object_key IS NULL OR image_object_key = ''", Integer.class);
            if (missing != null && missing == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN image_data");
            }
        }
        Integer missingKeys = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE image_object_key IS NULL OR image_object_key = ''", Integer.class);
        if (missingKeys != null && missingKeys == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN image_object_key varchar(255) NOT NULL");
        }
    }

    private void ensureObjectKeyColumn(String table) {
        if (!columnExists(table, "image_object_key")) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN image_object_key varchar(255) NULL AFTER user_id");
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(COLUMN_EXISTS_SQL, Integer.class, table, column);
        return count != null && count > 0;
    }

    private record LegacyImage(String id, Long userId, byte[] data, String mediaType) {
    }
}
