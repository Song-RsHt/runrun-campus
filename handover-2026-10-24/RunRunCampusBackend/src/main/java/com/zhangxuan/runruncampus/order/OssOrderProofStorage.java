package com.zhangxuan.runruncampus.order;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.Credentials;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProviderSupplier;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
@ConditionalOnProperty(name = "app.proof-storage.type", havingValue = "oss")
public class OssOrderProofStorage implements OrderProofStorage {
    private final OSSClient client;
    private final String bucket;

    public OssOrderProofStorage(@Value("${app.proof-storage.oss.region}") String region,
                                @Value("${app.proof-storage.oss.bucket}") String bucket,
                                @Value("${app.proof-storage.oss.ecs-role-name}") String ecsRoleName,
                                @Value("${app.proof-storage.oss.use-internal-endpoint:true}") boolean useInternalEndpoint) {
        OSSClientBuilder builder = OSSClient.newBuilder()
                .credentialsProvider(ecsRoleCredentialsProvider(ecsRoleName))
                .region(region);
        if (useInternalEndpoint) {
            builder.useInternalEndpoint(true);
        }
        this.client = builder.build();
        this.bucket = bucket;
    }

    private CredentialsProvider ecsRoleCredentialsProvider(String roleName) {
        try {
            Client credentialClient = new Client(new Config()
                    .setType("ecs_ram_role")
                    .setRoleName(roleName));
            return new CredentialsProviderSupplier(() -> {
                try {
                    var credential = credentialClient.getCredential();
                    return new Credentials(credential.getAccessKeyId(), credential.getAccessKeySecret(),
                            credential.getSecurityToken());
                } catch (Exception exception) {
                    throw new IllegalStateException("无法获取 ECS RAM 角色临时凭证", exception);
                }
            });
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化 ECS RAM 角色凭证", exception);
        }
    }

    @Override
    public void store(String objectKey, String contentType, long sizeBytes, InputStream input) throws IOException {
        try {
            client.putObject(PutObjectRequest.newBuilder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(Math.toIntExact(sizeBytes))
                    .body(BinaryData.fromStream(input, sizeBytes))
                    .build());
        } catch (RuntimeException exception) {
            throw new IOException("OSS upload failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            client.deleteObject(DeleteObjectRequest.newBuilder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            throw new IOException("OSS delete failed", exception);
        }
    }

    @Override
    public byte[] read(String objectKey) throws IOException {
        try (var result = client.getObject(GetObjectRequest.newBuilder()
                .bucket(bucket).key(objectKey).build())) {
            byte[] image = result.body().readNBytes(8 * 1024 * 1024 + 1);
            if (image.length > 8 * 1024 * 1024) throw new IOException("OSS image exceeds 8MB");
            return image;
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("OSS download failed", exception);
        }
    }

    @PreDestroy
    void close() throws Exception {
        client.close();
    }
}
