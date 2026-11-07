package com.zhangxuan.runrunv2;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.zhangxuan.runrunv2.bootstrap.IdentityStartupGuard;
import com.zhangxuan.runrunv2.bootstrap.LocalTestBootstrap;
import com.zhangxuan.runrunv2.mapper.identity.LocalTestMapper;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.service.identity.DeploymentIdentityService;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;
import java.util.stream.Stream;

class LocalTestBoundaryTest {

    static Stream<Arguments> unsafeInputs() {
        return Stream.of(
                Arguments.of("RRC_V2_ENVIRONMENT", "production"),
                Arguments.of("RRC_V2_JDBC_URL", "jdbc:postgresql://external.invalid:5432/database"),
                Arguments.of("rrc.payment.mock-enabled", "false"),
                Arguments.of("rrc.identity.wechat-enabled", "true"),
                Arguments.of("rrc.identity.app-id", "unexpected-app"),
                Arguments.of("RRC_V2_ADMIN_PHONE", "real-phone-is-forbidden"),
                Arguments.of("RRC_V2_BOOTSTRAP_CONFIRM", "unconfirmed"),
                Arguments.of("RRC_V2_ADMIN_PASSWORD", "REPLACE_LOCALLY"));
    }

    @ParameterizedTest
    @MethodSource("unsafeInputs")
    void unsafeFixtureConfigurationCannotWriteAccounts(String key, String value) {
        var env =
                new MockEnvironment()
                        .withProperty("RRC_V2_ENVIRONMENT", "test")
                        .withProperty(
                                "RRC_V2_JDBC_URL", "jdbc:postgresql://postgres:5432/rrc_v2_test")
                        .withProperty("rrc.payment.mock-enabled", "true")
                        .withProperty("rrc.identity.wechat-enabled", "false")
                        .withProperty("rrc.identity.app-id", "synthetic-local-app")
                        .withProperty("RRC_V2_ADMIN_PHONE", "test-admin")
                        .withProperty("RRC_V2_BOOTSTRAP_CONFIRM", "CREATE_ISOLATED_TEST_ADMIN")
                        .withProperty("RRC_V2_ADMIN_PASSWORD", UUID.randomUUID().toString())
                        .withProperty(key, value);
        var mapper = mock(LocalTestMapper.class);
        var deployment = mock(DeploymentIdentityService.class);
        var bootstrap =
                new LocalTestBootstrap(
                        mapper,
                        env,
                        mock(IdentitySecrets.class),
                        deployment,
                        mock(PlatformTransactionManager.class),
                        mock(IdentityStartupGuard.class));
        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verifyNoInteractions(mapper, deployment);
    }
}
