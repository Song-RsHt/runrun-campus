package com.zhangxuan.runrunv2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.config.V2PersistenceConfiguration;

import org.springframework.jdbc.core.JdbcTemplate;

/** 独立数据库测试复用项目真实 MyBatis 工厂，JDBC 仍用于检查数据库事实。 */
public final class V2MapperFixture {
    private V2MapperFixture() {}

    public static <T> T mapper(JdbcTemplate jdbc, Class<T> type) {
        try {
            var configuration = new V2PersistenceConfiguration();
            var factory =
                    configuration.v2SqlSessionFactory(
                            jdbc.getDataSource(), new ObjectMapper().findAndRegisterModules());
            return configuration
                    .v2SqlSessionTemplate(factory, jdbc.getDataSource())
                    .getMapper(type);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize v2 test Mapper", exception);
        }
    }
}
