package com.zhangxuan.runruncampus.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "app.proof-storage.type", havingValue = "local", matchIfMissing = true)
public class LocalOrderProofStorage implements OrderProofStorage {
    private final Path storageDirectory;

    public LocalOrderProofStorage(@Value("${app.proof-storage.local-directory}") String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void store(String objectKey, String contentType, long sizeBytes, InputStream input) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(storageDirectory);
        Files.copy(input, target);
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    @Override
    public byte[] read(String objectKey) throws IOException {
        return Files.readAllBytes(resolve(objectKey));
    }

    private Path resolve(String objectKey) {
        Path target = storageDirectory.resolve(objectKey).normalize();
        if (!storageDirectory.equals(target.getParent())) {
            throw new IllegalArgumentException("凭证文件名不正确");
        }
        return target;
    }
}
