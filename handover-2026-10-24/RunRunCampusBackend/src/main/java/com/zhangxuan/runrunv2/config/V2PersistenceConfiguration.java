package com.zhangxuan.runrunv2.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.model.order.OrderAddress;
import com.zhangxuan.runrunv2.model.order.OrderPricing;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;

import java.util.Map;

import javax.sql.DataSource;

/** 仅由 v2 扫描装配，复用 Spring 数据源与事务；不影响 legacy JPA。 */
@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.zhangxuan.runrunv2.mapper",
        annotationClass = Mapper.class,
        sqlSessionTemplateRef = "v2SqlSessionTemplate")
public class V2PersistenceConfiguration {
    @Bean
    public SqlSessionFactory v2SqlSessionFactory(DataSource dataSource, ObjectMapper json)
            throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        // JDBC 原来每次访问数据库；不能缓存锁查询或同事务中的可变投影。
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.setCacheEnabled(false);
        configuration.setCallSettersOnNulls(true);
        configuration.setReturnInstanceForEmptyRow(true);
        configuration.addInterceptor(new SingleRowInterceptor());
        configuration.getTypeHandlerRegistry().register(Void.class, new VoidFunctionTypeHandler());
        // null Java 类型只登记处理器实例，不替换 Map/Object 默认映射；仅 XML 显式引用。
        configuration
                .getTypeHandlerRegistry()
                .register((Class<Map<String, Object>>) null, new OutboxPayloadTypeHandler(json));
        OrderJson orderJson = new OrderJson(json);
        configuration
                .getTypeHandlerRegistry()
                .register((Class<OrderAddress>) null, new OrderAddressTypeHandler(orderJson));
        configuration
                .getTypeHandlerRegistry()
                .register((Class<OrderPricing>) null, new OrderPricingTypeHandler(orderJson));
        configuration
                .getTypeHandlerRegistry()
                .register((Class<Map<String, Object>>) null, new OrderPolicyTypeHandler(orderJson));
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/v2/**/*.xml"));
        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate v2SqlSessionTemplate(
            SqlSessionFactory v2SqlSessionFactory, DataSource dataSource) {
        MyBatisExceptionTranslator delegate = new MyBatisExceptionTranslator(dataSource, true);
        return new SqlSessionTemplate(
                v2SqlSessionFactory,
                v2SqlSessionFactory.getConfiguration().getDefaultExecutorType(),
                exception -> {
                    // MyBatis 包装执行器异常；恢复原 JDBC 的单行异常，SQL 异常仍由官方翻译器处理。
                    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                        if (cause instanceof DataAccessException failure) {
                            return failure;
                        }
                    }
                    return delegate.translateExceptionIfPossible(exception);
                });
    }
}
