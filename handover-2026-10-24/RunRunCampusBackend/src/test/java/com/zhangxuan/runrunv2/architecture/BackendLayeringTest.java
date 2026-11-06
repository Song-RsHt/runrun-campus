package com.zhangxuan.runrunv2.architecture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhangxuan.runruncampus.RunRunCampusBackendApplication;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.impl.auth.AdminMfaServiceImpl;
import com.zhangxuan.runrunv2.IdentityApplication;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 校验两个应用的技术层、接口依赖与调度边界，不启动或替换数据库。 */
class BackendLayeringTest {
    private static final String OWNER = "com.zhangxuan.";
    private static final Set<String> TECHNICAL_LAYERS =
            Set.of(
                    "controller",
                    "mapper",
                    "repository",
                    "dto",
                    "vo",
                    "entity",
                    "model",
                    "enums",
                    "constant",
                    "exception",
                    "handler",
                    "security",
                    "config",
                    "task",
                    "bootstrap");
    private static final Set<String> BUSINESS_LAYERS =
            Set.of(
                    "controller",
                    "mapper",
                    "repository",
                    "dto",
                    "vo",
                    "entity",
                    "model",
                    "enums",
                    "constant",
                    "task");

    private record Source(Path path, String text, String packageName) {
        String namespace() {
            return packageName.substring(0, packageName.indexOf('.', OWNER.length()));
        }

        String layer() {
            return packageName.substring(namespace().length() + 1);
        }

        String technicalLayer() {
            return layer().split("\\.")[0];
        }

        String businessDomain() {
            String[] parts = layer().split("\\.");
            return parts[0].equals("service") && parts[1].equals("impl") ? parts[2] : parts[1];
        }

        Class<?> type() throws ClassNotFoundException {
            String name = path.getFileName().toString().replace(".java", "");
            return Class.forName(packageName + "." + name, false, getClass().getClassLoader());
        }
    }

    private List<Source> sources() throws IOException {
        try (var files = Files.walk(Path.of("src/main/java/com/zhangxuan"))) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .map(
                            p -> {
                                try {
                                    String text = Files.readString(p);
                                    var matcher =
                                            Pattern.compile("(?m)^package ([\\w.]+);")
                                                    .matcher(text);
                                    assertTrue(matcher.find(), p.toString());
                                    return new Source(p, text, matcher.group(1));
                                } catch (IOException error) {
                                    throw new UncheckedIOException(error);
                                }
                            })
                    .toList();
        }
    }

    private void reject(Source source, String expression) {
        assertFalse(
                Pattern.compile(expression).matcher(source.text()).find(),
                () -> source.path() + " 违反分层约束：" + expression);
    }

    @Test
    void applicationsHaveIndependentScansAndNoCrossNamespaceReferences() throws Exception {
        assertArrayEquals(
                new Class<?>[] {IdentityApplication.class},
                IdentityApplication.class.getAnnotation(ComponentScan.class).basePackageClasses());
        assertEquals(
                3,
                IdentityApplication.class
                        .getAnnotation(EnableAutoConfiguration.class)
                        .exclude()
                        .length);
        assertEquals(
                0,
                RunRunCampusBackendApplication.class
                        .getAnnotation(SpringBootApplication.class)
                        .scanBasePackages()
                        .length);
        for (var source : sources()) {
            String other = source.packageName().contains("runrunv2") ? "runruncampus" : "runrunv2";
            reject(source, "com\\.zhangxuan\\." + other + "\\.");
        }
    }

    @Test
    void businessClassesLiveInDomainSubpackagesAndSourcePathsMatchPackages() throws Exception {
        for (var source : sources()) {
            String name = source.packageName();
            String namespace =
                    name.startsWith(OWNER + "runrunv2")
                            ? OWNER + "runrunv2"
                            : OWNER + "runruncampus";
            assertTrue(
                    source.path()
                            .toString()
                            .replace('\\', '/')
                            .contains(name.replace('.', '/') + "/"),
                    source.path().toString());
            if (name.equals(namespace)) {
                assertTrue(
                        source.type().isAnnotationPresent(SpringBootApplication.class)
                                || source.type() == IdentityApplication.class,
                        source.path().toString());
                continue;
            }
            String layer = name.substring(namespace.length() + 1);
            String first = layer.split("\\.")[0];
            if (first.equals("service")) {
                assertTrue(layer.matches("service\\.(?:impl\\.)?[a-z][a-z0-9]*"), name);
            } else if (first.equals("integration")) {
                assertTrue(layer.startsWith("integration."), name);
            } else if (BUSINESS_LAYERS.contains(first)) {
                assertTrue(layer.matches(first + "\\.[a-z][a-z0-9]*"), name);
            } else {
                assertTrue(TECHNICAL_LAYERS.contains(layer), name);
            }
            Class<?> type = source.type();
            if (type.isAnnotationPresent(RestController.class)) {
                assertEquals("controller", first, name);
            }
            if (type.isAnnotationPresent(Entity.class)) {
                assertEquals("entity", first, name);
            }
        }
    }

    @Test
    void businessServicesHaveContractsAndInjectOtherServicesThroughInterfaces() throws Exception {
        int contracts = 0;
        int implementations = 0;
        for (var source : sources()) {
            if (!source.packageName().contains(".service")) {
                continue;
            }
            Class<?> type = source.type();
            if (!source.layer().startsWith("service.impl.")) {
                assertTrue(type.isInterface(), type.getName());
                assertFalse(type.isAnnotationPresent(Service.class), type.getName());
                if (!type.getSimpleName().equals("OriginalPaymentWriter")) {
                    Class<?> impl =
                            Class.forName(
                                    source.namespace()
                                            + ".service.impl."
                                            + source.businessDomain()
                                            + "."
                                            + type.getSimpleName()
                                            + "Impl");
                    assertTrue(type.isAssignableFrom(impl), type.getName());
                    contracts++;
                }
            } else {
                implementations++;
                assertTrue(type.getSimpleName().endsWith("ServiceImpl"), type.getName());
                assertTrue(type.isAnnotationPresent(Service.class), type.getName());
                if (type.getSimpleName().equals("LoginCaptchaServiceImpl")) {
                    // 保留验证码组件的默认随机源/时钟构造器和测试专用构造器。
                    assertNotNull(type.getConstructor());
                    assertEquals(2, type.getDeclaredConstructors().length);
                } else if (type.getDeclaredConstructors().length > 1) {
                    assertEquals(
                            1,
                            Arrays.stream(type.getDeclaredConstructors())
                                    .filter(
                                            constructor ->
                                                    constructor.isAnnotationPresent(
                                                            Autowired.class))
                                    .count(),
                            type.getName());
                }
                assertEquals(1, type.getInterfaces().length, type.getName());
                Class<?> contract = type.getInterfaces()[0];
                assertEquals(
                        source.namespace() + ".service." + source.businessDomain(),
                        contract.getPackageName());
                for (var method : type.getDeclaredMethods()) {
                    if (Modifier.isPublic(method.getModifiers())
                            && !Modifier.isStatic(method.getModifiers())
                            && !method.isSynthetic()) {
                        assertNotNull(
                                contract.getMethod(method.getName(), method.getParameterTypes()),
                                method.toString());
                    }
                }
                for (var field : type.getDeclaredFields()) {
                    assertFalse(
                            field.getType().getPackageName().contains(".service.impl."),
                            field.toString());
                    assertFalse(field.isAnnotationPresent(Autowired.class), field.toString());
                }
            }
        }
        assertEquals(47, contracts, "新增业务契约时须核对对应实现和边界");
        assertEquals(contracts, implementations);
    }

    @Test
    void controllersAndServicesCannotBypassPersistenceOrTransportBoundaries() throws Exception {
        for (var source : sources()) {
            if (source.packageName().contains(".controller.")) {
                reject(
                        source,
                        "JdbcTemplate|DataSource|EntityManager|com\\.zhangxuan\\.[\\w.]+\\.(?:repository|mapper)\\.");
                reject(source, "com\\.zhangxuan\\.[\\w.]+\\.service\\.impl\\.");
            }
            if (source.packageName().contains(".service")) {
                reject(
                        source,
                        "(?m)^import"
                            + " (?:jakarta\\.servlet|javax\\.servlet|org\\.springframework\\.(?:http|web))\\.");
                reject(
                        source,
                        "JdbcTemplate|EntityManager|DataSource|com\\.zhangxuan\\.[\\w.]+\\.controller\\.");
            }
        }
    }

    @Test
    void persistenceDoesNotDependOnServicesOrHttpOrOwnTransactions() throws Exception {
        for (var source : sources()) {
            if (source.packageName().contains(".mapper.")
                    || source.packageName().contains(".repository.")) {
                reject(
                        source,
                        "com\\.zhangxuan\\.[\\w.]+\\.(?:service|controller)\\.|HttpServlet|ResponseEntity");
            }
            if (source.packageName().contains(".mapper.")) {
                reject(source, "@Transactional|PlatformTransactionManager|TransactionTemplate");
            }
        }
    }

    @Test
    void schedulingRegistrationsAndFeatureConditionsStayUnchanged() throws Exception {
        Map<String, String> actual = new HashMap<>();
        for (var source : sources()) {
            Class<?> type = source.type();
            for (var method : type.getDeclaredMethods()) {
                Scheduled schedule = method.getAnnotation(Scheduled.class);
                if (schedule == null) {
                    continue;
                }
                assertEquals("task", source.technicalLayer(), method.toString());
                reject(
                        source,
                        "JdbcTemplate|EntityManager|com\\.zhangxuan\\.[\\w.]+\\.(?:repository|mapper)\\.");
                String key =
                        source.namespace().substring(OWNER.length())
                                + "."
                                + type.getSimpleName()
                                + "."
                                + method.getName();
                actual.put(
                        key,
                        schedule.cron()
                                + "|"
                                + schedule.fixedDelayString()
                                + "|"
                                + schedule.initialDelayString()
                                + "|"
                                + schedule.zone());
                assertEquals(-1, schedule.fixedRate());
                assertEquals(-1, schedule.fixedDelay());
                assertFalse(type.isAnnotationPresent(Profile.class), type.getName());
                ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
                if (type.getSimpleName().equals("FulfillmentCompletionJob")) {
                    assertNotNull(condition);
                    assertArrayEquals(new String[] {"rrc.fulfillment.enabled"}, condition.name());
                    assertEquals("true", condition.havingValue());
                    assertFalse(condition.matchIfMissing());
                } else if (type.getSimpleName().equals("PaymentExpiryJob")) {
                    assertNotNull(condition);
                    assertArrayEquals(new String[] {"rrc.payment.mock-enabled"}, condition.name());
                    assertEquals("true", condition.havingValue());
                    assertFalse(condition.matchIfMissing());
                } else if (type.getSimpleName().equals("PushDeliveryProcessorTask")) {
                    assertNotNull(condition);
                    assertArrayEquals(new String[] {"app.push.enabled"}, condition.name());
                    assertEquals("true", condition.havingValue());
                    assertFalse(condition.matchIfMissing());
                } else {
                    assertTrue(condition == null, type.getName());
                }
            }
        }
        assertEquals(
                Map.of(
                        "runruncampus.CourierCertificationMaintenanceTask.run",
                                "${app.courier-certification.maintenance-cron:0 0 9 * *"
                                        + " *}|||Asia/Shanghai",
                        "runruncampus.RiskSignalMaintenanceTask.scan",
                                "${app.risk.scan-cron:0 */10 * * * *}|||",
                        "runruncampus.PushDeliveryProcessorTask.process",
                                "|${app.push.retry-poll-millis:30000}|${app.push.retry-initial-delay-millis:30000}|",
                        "runruncampus.WechatRefundProcessorTask.process",
                                "|${wechat.pay.refund-poll-millis:60000}||",
                        "runrunv2.CourierEvidenceGovernanceServiceTask.cleanupDue",
                                "|${rrc.evidence.cleanup-delay-ms:60000}|${rrc.evidence.cleanup-delay-ms:60000}|",
                        "runrunv2.FulfillmentCompletionJob.complete",
                                "|${rrc.fulfillment.completion-delay-ms:15000}|${rrc.fulfillment.completion-delay-ms:15000}|",
                        "runrunv2.PaymentExpiryJob.expire",
                                "|${rrc.payment.expiry-delay-ms:15000}|${rrc.payment.expiry-delay-ms:15000}|",
                        "runrunv2.NotificationDeliveryTask.deliver",
                                "|${rrc.notifications.worker-delay-ms:15000}||"),
                actual);
    }

    @Test
    void mfaFailureAdaptationPreservesOriginalRollbackRules() {
        var attributes = new AnnotationTransactionAttributeSource();
        long checked =
                Arrays.stream(AdminMfaServiceImpl.class.getDeclaredMethods())
                        .filter(
                                method -> {
                                    var transaction =
                                            attributes.getTransactionAttribute(
                                                    method, AdminMfaServiceImpl.class);
                                    if (transaction == null
                                            || !(method.getName().equals("begin")
                                                    || method.getName().equals("verify")
                                                    || method.getName().equals("replace"))) {
                                        return false;
                                    }
                                    assertFalse(
                                            transaction.rollbackOn(
                                                    new BusinessFailure(401, "测试失败")),
                                            method.toString());
                                    assertFalse(
                                            transaction.rollbackOn(
                                                    new ResponseStatusException(
                                                            HttpStatus.UNAUTHORIZED)),
                                            method.toString());
                                    assertTrue(
                                            transaction.rollbackOn(
                                                    new IllegalStateException("测试失败")),
                                            method.toString());
                                    return true;
                                })
                        .count();
        assertEquals(4, checked);
    }
}
