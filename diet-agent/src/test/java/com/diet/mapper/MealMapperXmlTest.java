package com.diet.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 防止新增动态 SQL 因 XML/OGNL 语法错误而只在应用启动时暴露。 */
class MealMapperXmlTest {

    @Test
    void shouldParseHybridRetrievalMappers() {
        parse("mapper/MealMapper.xml");
        parse("mapper/MealSlotTagMapper.xml");
    }

    private void parse(String resource) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, () -> "missing mapper resource: " + resource);
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (Exception error) {
            throw new AssertionError("failed to parse " + resource, error);
        }
    }
}
