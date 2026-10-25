package com.zhangxuan.runruncampus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Profile("prod")
@Order(0)
public class ProductionConfigurationValidator implements ApplicationRunner {
    private static final Pattern PHONE = Pattern.compile("1\\d{10}");
    private static final Pattern VERIFIED_SSL = Pattern.compile("[?&]sslmode=verify-full(?:&|$)");
    private static final Pattern RDS_CA = Pattern.compile(
            "[?&]sslrootcert=/run/secrets/RDS_CA_CERTIFICATE(?:&|$)");

    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String adminPhone;
    private final String adminPassword;
    private final String proofStorageType;
    private final String ossRegion;
    private final String ossBucket;
    private final String ossRoleName;
    private final boolean useInternalEndpoint;

    public ProductionConfigurationValidator(
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${app.admin.phone}") String adminPhone,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.proof-storage.type}") String proofStorageType,
            @Value("${app.proof-storage.oss.region}") String ossRegion,
            @Value("${app.proof-storage.oss.bucket}") String ossBucket,
            @Value("${app.proof-storage.oss.ecs-role-name}") String ossRoleName,
            @Value("${app.proof-storage.oss.use-internal-endpoint}") boolean useInternalEndpoint) {
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
        this.proofStorageType = proofStorageType;
        this.ossRegion = ossRegion;
        this.ossBucket = ossBucket;
        this.ossRoleName = ossRoleName;
        this.useInternalEndpoint = useInternalEndpoint;
    }

    @Override
    public void run(ApplicationArguments args) {
        require(datasourceUrl.startsWith("jdbc:postgresql://"), "生产数据库必须使用 PostgreSQL JDBC 地址");
        require(VERIFIED_SSL.matcher(datasourceUrl).find(), "生产数据库必须启用 sslmode=verify-full");
        require(RDS_CA.matcher(datasourceUrl).find(), "生产数据库必须使用挂载的 RDS CA 证书");
        require(!datasourceUsername.isBlank() && !datasourceUsername.equalsIgnoreCase("postgres"),
                "生产数据库必须使用非 postgres 的最小权限账号");
        requireSecretLength(datasourcePassword, 32, 32, "生产数据库密码");
        require(PHONE.matcher(adminPhone).matches(), "初始管理员手机号格式无效");
        requireSecretLength(adminPassword, 16, 72, "初始管理员密码");
        require(!datasourcePassword.equals(adminPassword), "数据库密码与管理员密码不能相同");
        require(proofStorageType.equals("oss"), "生产环境必须使用 OSS 凭证存储");
        requireValidValue(ossRegion, "OSS Region");
        requireValidValue(ossBucket, "OSS Bucket");
        requireValidValue(ossRoleName, "ECS RAM 角色名");
        require(useInternalEndpoint, "生产 ECS 必须通过 OSS 内网端点访问");
    }

    private void requireSecretLength(String value, int minimumLength, int maximumLength, String name) {
        require(value.length() >= minimumLength && value.length() <= maximumLength,
                name + "长度必须为 " + minimumLength + " 至 " + maximumLength + " 位");
        requireValidValue(value, name);
    }

    private void requireValidValue(String value, String name) {
        String normalized = value.toLowerCase(Locale.ROOT);
        require(!value.isBlank()
                        && !normalized.contains("replace-with")
                        && !normalized.contains("change-this")
                        && !normalized.contains("example"),
                name + "仍是占位值或为空");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("生产配置校验失败：" + message);
        }
    }
}
