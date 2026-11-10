package com.zhangxuan.runruncampus.support;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInfo;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContextManager;

/** 类级数据库测试先释放 Spring 资源，再交给 Testcontainers 停库，避免退出时重试已关闭的数据库。 */
public abstract class PostgresSpringContextTest {

    @AfterAll
    static void releaseSpringContext(TestInfo testInfo) {
        Class<?> testClass = testInfo.getTestClass().orElseThrow();
        // 用户级 @AfterAll 先于扩展的 afterAll；同时移除缓存和关闭上下文，不能只关闭池而留下缓存。
        new TestContextManager(testClass)
                .getTestContext()
                .markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
        System.getLogger(PostgresSpringContextTest.class.getName())
                .log(
                        System.Logger.Level.INFO,
                        "Spring context released before container teardown: "
                                + testClass.getSimpleName());
    }
}
