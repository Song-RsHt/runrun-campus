package com.zhangxuan.runrunv2.integration.storage;

import java.io.*;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class V2StudentCardStorage {

  private final Path root;

  public V2StudentCardStorage(
      @Value("${rrc.evidence.root:${java.io.tmpdir}/runrun-v2-evidence}") String root) {
    this.root = Paths.get(root).toAbsolutePath().normalize();
  }

  public void store(String key, String type, byte[] bytes) throws IOException {
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) throw new IOException("path");
    Files.createDirectories(p.getParent());
    Files.write(p, bytes, StandardOpenOption.CREATE_NEW);
  }

  public byte[] read(String key) throws IOException {
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) throw new IOException("path");
    return Files.readAllBytes(p);
  }

  public void delete(String key) throws IOException {
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) throw new IOException("path");
    Files.deleteIfExists(p);
  }
}
