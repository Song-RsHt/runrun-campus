package com.zhangxuan.runrunv2.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.zhangxuan.runruncampus.RunRunCampusBackendApplication;
import com.zhangxuan.runrunv2.IdentityApplication;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/** Enforces boundaries for both applications without starting or substituting either database. */
class BackendLayeringTest {

  private record Source(Path path, String text) {}

  private List<Source> sources() throws Exception {
    try (var files = Files.walk(Path.of("src/main/java/com/zhangxuan"))) {
      return files
          .filter(p -> p.toString().endsWith(".java"))
          .map(
              p -> {
                try {
                  return new Source(p, Files.readString(p));
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              })
          .toList();
    }
  }

  private void reject(Source source, String expression) {
    assertFalse(
        Pattern.compile(expression).matcher(source.text()).find(),
        () -> source.path() + " violates " + expression);
  }

  @Test
  void applicationsHaveIndependentScansAndNoCrossNamespaceReferences() throws Exception {
    assertArrayEquals(
        new Class<?>[] {IdentityApplication.class},
        IdentityApplication.class.getAnnotation(ComponentScan.class).basePackageClasses());
    assertEquals(
        3, IdentityApplication.class.getAnnotation(EnableAutoConfiguration.class).exclude().length);
    assertEquals(
        0,
        RunRunCampusBackendApplication.class
            .getAnnotation(SpringBootApplication.class)
            .scanBasePackages()
            .length);
    for (var source : sources()) {
      String other = source.path().toString().contains("runrunv2") ? "runruncampus" : "runrunv2";
      reject(source, "com\\.zhangxuan\\." + other + "\\.");
    }
  }

  @Test
  void controllersAndServicesCannotBypassPersistenceBoundaries() throws Exception {
    for (var source : sources()) {
      if (source.text().matches("(?s).*package [\\w.]+\\.controller\\.[\\w.]+;.*")) {
        reject(
            source,
            "JdbcTemplate|DataSource|EntityManager|com\\.zhangxuan\\.[\\w.]+\\.(?:repository|mapper)\\.");
      }
      if (source.text().matches("(?s).*package [\\w.]+\\.service\\.[\\w.]+;.*")) {
        reject(
            source,
            "HttpServletRequest|HttpServletResponse|ResponseEntity|JdbcTemplate|EntityManager|DataSource|com\\.zhangxuan\\.[\\w.]+\\.controller\\.");
      }
    }
  }

  @Test
  void persistenceDoesNotDependOnServicesOrHttp() throws Exception {
    for (var source : sources()) {
      if (source.text().matches("(?s).*package [\\w.]+\\.(?:mapper|repository)\\.[\\w.]+;.*")) {
        reject(
            source,
            "com\\.zhangxuan\\.[\\w.]+\\.(?:service|controller)\\.|HttpServlet|ResponseEntity");
      }
      if (source.text().matches("(?s).*package [\\w.]+\\.mapper\\.[\\w.]+;.*")) {
        reject(source, "@Transactional|PlatformTransactionManager|TransactionTemplate");
      }
    }
  }

  @Test
  void schedulingIsOnlyRegisteredOnThinTasks() throws Exception {
    int scheduledMethods = 0;
    for (var source : sources()) {
      var matches = Pattern.compile("@Scheduled\\s*\\(").matcher(source.text());
      while (matches.find()) {
        scheduledMethods++;
        assertTrue(
            source.text().matches("(?s).*package [\\w.]+\\.task\\.[\\w.]+;.*"),
            source.path().toString());
        reject(
            source,
            "JdbcTemplate|EntityManager|com\\.zhangxuan\\.[\\w.]+\\.(?:repository|mapper)\\.");
      }
    }
    assertEquals(8, scheduledMethods, "Review registration and profile gates when adding a task");
  }
}
