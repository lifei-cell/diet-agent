package com.diet.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adds nutrition columns to databases created before nutrition constraints were introduced.
 * New installations receive these columns from diet_db.sql; existing installations are
 * migrated here because MySQL does not support ADD COLUMN IF NOT EXISTS.
 */
@Component
public class NutritionSchemaMigration implements ApplicationRunner {

    private static final String COLUMN_EXISTS_SQL = """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public NutritionSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ColumnDefinition> columns = List.of(
                new ColumnDefinition("energy_kcal", "decimal(8,2) NULL"),
                new ColumnDefinition("protein_g", "decimal(8,2) NULL"),
                new ColumnDefinition("fat_g", "decimal(8,2) NULL"),
                new ColumnDefinition("carbohydrate_g", "decimal(8,2) NULL"),
                new ColumnDefinition("fiber_g", "decimal(8,2) NULL"),
                new ColumnDefinition("sodium_mg", "decimal(10,2) NULL"),
                new ColumnDefinition("allergens", "json NULL"),
                new ColumnDefinition("nutrition_source", "varchar(64) NULL")
        );

        for (ColumnDefinition column : columns) {
            ensureColumnExists(column);
        }

        seedNutritionData();
    }

    private void ensureColumnExists(ColumnDefinition column) {
        Integer count = jdbcTemplate.queryForObject(
                COLUMN_EXISTS_SQL,
                Integer.class,
                "meal_item",
                column.name()
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE meal_item ADD COLUMN " + column.name() + " " + column.definition());
        }
    }

    private void seedNutritionData() {
        jdbcTemplate.update("""
                UPDATE meal_item
                SET energy_kcal = 420, protein_g = 18, fat_g = 12, carbohydrate_g = 62,
                    fiber_g = 4, sodium_mg = 900, allergens = JSON_ARRAY('鸡蛋', '麸质'), nutrition_source = 'SEED_ESTIMATE'
                WHERE id = 1 AND energy_kcal IS NULL
                """);
        jdbcTemplate.update("""
                UPDATE meal_item
                SET energy_kcal = 320, protein_g = 16, fat_g = 9, carbohydrate_g = 45,
                    fiber_g = 2, sodium_mg = 780, allergens = JSON_ARRAY('麸质'), nutrition_source = 'SEED_ESTIMATE'
                WHERE id = 2 AND energy_kcal IS NULL
                """);
        jdbcTemplate.update("""
                UPDATE meal_item
                SET energy_kcal = 460, protein_g = 38, fat_g = 14, carbohydrate_g = 42,
                    fiber_g = 8, sodium_mg = 620, allergens = JSON_ARRAY(), nutrition_source = 'SEED_ESTIMATE'
                WHERE id = 3 AND energy_kcal IS NULL
                """);
        jdbcTemplate.update("""
                UPDATE meal_item
                SET energy_kcal = 760, protein_g = 26, fat_g = 42, carbohydrate_g = 68,
                    fiber_g = 8, sodium_mg = 1800, allergens = JSON_ARRAY('花生', '大豆'), nutrition_source = 'SEED_ESTIMATE'
                WHERE id = 4 AND energy_kcal IS NULL
                """);
        jdbcTemplate.update("""
                UPDATE meal_item
                SET energy_kcal = 560, protein_g = 31, fat_g = 24, carbohydrate_g = 52,
                    fiber_g = 6, sodium_mg = 980, allergens = JSON_ARRAY(), nutrition_source = 'SEED_ESTIMATE'
                WHERE id = 5 AND energy_kcal IS NULL
                """);
    }

    private record ColumnDefinition(String name, String definition) {
    }
}
