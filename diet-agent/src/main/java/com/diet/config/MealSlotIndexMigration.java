package com.diet.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 为已有实例创建并回填餐食标签倒排表。INSERT IGNORE 使其可重复执行；新建、更新、删除
 * 由 MealSlotTagService 在业务事务内实时维护。
 */
@Component
public class MealSlotIndexMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public MealSlotIndexMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS meal_slot_tag (
                    meal_id BIGINT NOT NULL,
                    slot_name VARCHAR(64) NOT NULL,
                    tag_value VARCHAR(64) NOT NULL,
                    PRIMARY KEY (meal_id, slot_name, tag_value),
                    INDEX idx_meal_slot_lookup (slot_name, tag_value, meal_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        backfill("mealTime", "meal_time");
        backfill("mood", "mood");
        backfill("scene", "scene");
        backfill("healthGoal", "health_goal");
        backfill("cuisine", "cuisine");
        backfill("taste", "taste");
        backfill("convenience", "convenience");
    }

    private void backfill(String slotName, String jsonColumn) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO meal_slot_tag (meal_id, slot_name, tag_value)
                SELECT id, ?, tags.tag_value
                FROM meal_item
                JOIN JSON_TABLE(%s, '$[*]' COLUMNS(tag_value VARCHAR(64) PATH '$')) AS tags ON 1 = 1
                """.formatted(jsonColumn), slotName);
    }
}
