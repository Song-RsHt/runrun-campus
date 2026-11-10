package com.zhangxuan.runruncampus.integration.storage;

import java.io.IOException;
import java.io.InputStream;

/** 订单私有凭证存储组件；保持当前应用的配置与协作边界。 */
public interface OrderProofStorage {

    void store(String objectKey, String contentType, long sizeBytes, InputStream input)
            throws IOException;

    void delete(String objectKey) throws IOException;

    byte[] read(String objectKey) throws IOException;
}
