package com.zhangxuan.runrunv2.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.config.OrderPolicyTypeHandler;
import com.zhangxuan.runrunv2.config.SingleRowInterceptor;
import com.zhangxuan.runrunv2.config.V2PersistenceConfiguration;
import com.zhangxuan.runrunv2.entity.configuration.DeliveryZoneEntity;
import com.zhangxuan.runrunv2.entity.courier.CourierCertificationEntity;
import com.zhangxuan.runrunv2.entity.identity.AppUserEntity;

import org.apache.ibatis.session.LocalCacheScope;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 校验实际 Mapper 工厂、XML 和实体边界，防止 legacy 被自动配置或通用处理器污染。 */
class V2PersistenceArchitectureTest {
    @Test
    void factoryLoadsOnlyV2XmlAndExplicitHandlersWithoutOpeningADatabase() throws Exception {
        var config = new V2PersistenceConfiguration();
        var dataSource =
                new DriverManagerDataSource("jdbc:postgresql://unused.invalid/never-connected");
        var factory = config.v2SqlSessionFactory(dataSource, new ObjectMapper());
        var mybatis = factory.getConfiguration();
        var resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/v2/**/*.xml");
        assertEquals(16, resources.length, "新增 Mapper 必须同步核对 XML 制品清单");
        assertEquals(16, mybatis.getMapperRegistry().getMappers().size());
        for (Class<?> mapper : mybatis.getMapperRegistry().getMappers()) {
            assertTrue(mapper.getName().startsWith("com.zhangxuan.runrunv2.mapper."));
            for (var method : mapper.getDeclaredMethods()) {
                if (!method.isDefault() && !method.isSynthetic()) {
                    assertTrue(mybatis.hasStatement(mapper.getName() + "." + method.getName()));
                }
            }
        }
        for (var resource : resources) {
            try (var stream = resource.getInputStream()) {
                String xml =
                        new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(xml.contains("${"), resource.toString());
            }
        }
        assertEquals(LocalCacheScope.STATEMENT, mybatis.getLocalCacheScope());
        assertFalse(mybatis.isCacheEnabled());
        assertTrue(mybatis.isCallSettersOnNulls());
        assertTrue(mybatis.isReturnInstanceForEmptyRow());
        assertNull(mybatis.getTypeHandlerRegistry().getTypeHandler(Map.class));
        assertNotNull(
                mybatis.getTypeHandlerRegistry()
                        .getMappingTypeHandler(OrderPolicyTypeHandler.class));
        assertEquals(1, mybatis.getInterceptors().size());
        assertTrue(mybatis.getInterceptors().get(0) instanceof SingleRowInterceptor);
    }

    @Test
    void legacyCannotDiscoverMybatisAutoConfigurationAndEntityKeysStayDatabaseGenerated()
            throws Exception {
        assertThrows(
                ClassNotFoundException.class,
                () ->
                        Class.forName(
                                "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"));
        for (Class<?> entity :
                List.of(
                        DeliveryZoneEntity.class,
                        AppUserEntity.class,
                        CourierCertificationEntity.class)) {
            assertEquals("runrun_v2", entity.getAnnotation(TableName.class).schema());
            assertEquals(
                    IdType.AUTO, entity.getDeclaredField("id").getAnnotation(TableId.class).type());
        }
    }

    @Test
    void v2MainNoLongerContainsTheMigratedJdbcPath() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/com/zhangxuan/runrunv2"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("JdbcTemplate"), file.toString());
                assertFalse(source.contains("DriverManager.getConnection"), file.toString());
                assertFalse(source.contains("SqlParameterValue"), file.toString());
            }
        }
    }
}
