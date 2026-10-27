package com.zhangxuan.runrunv2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Deployment access is host access plus isolated DB credentials; no web flag can assert this identity. */
public class IdentityAdminCommand {
    public static void main(String[] args) {
        if (System.console() == null || args.length == 0) throw new IllegalStateException("An interactive controlled deployment console is required");
        System.setProperty("spring.config.name", "application-v2");
        SpringApplication application = new SpringApplication(IdentityApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run()) {
            DeploymentIdentityService deploy = context.getBean(DeploymentIdentityService.class);
            String reason = System.console().readLine("Reason: ");
            String requestId = System.console().readLine("Request ID (reuse for a retry): ");
            String confirmation = System.console().readLine("Type CREATE DEPLOYMENT IDENTITY / CREATE ADMIN <id> / ENABLE <id> / DISABLE <id>: ");
            if ("bootstrap".equals(args[0]) && args.length == 1) {
                System.console().printf("Deployment actor ID: %s%n", deploy.bootstrap(reason, confirmation, requestId));
            } else if (args.length == 3) {
                long actorId = Long.parseLong(System.getenv("RRC_DEPLOYMENT_ACTOR_ID"));
                long target = Long.parseLong(args[1]);
                long expected = Long.parseLong(args[2]);
                var result = switch (args[0]) {
                    case "create-admin" -> deploy.provisionAdmin(actorId, target, expected, reason, confirmation, requestId);
                    case "enable" -> deploy.changeAccess(actorId, target, true, expected, reason, confirmation, requestId);
                    case "disable" -> deploy.changeAccess(actorId, target, false, expected, reason, confirmation, requestId);
                    default -> throw IdentityFailure.invalid();
                };
                System.console().printf("Account %s: enabled=%s securityVersion=%d%n", result.userId(), result.enabled(), result.securityVersion());
            } else throw IdentityFailure.invalid();
        }
    }
}
