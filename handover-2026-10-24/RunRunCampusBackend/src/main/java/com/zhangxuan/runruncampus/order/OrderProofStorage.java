package com.zhangxuan.runruncampus.order;

import java.io.IOException;
import java.io.InputStream;

public interface OrderProofStorage {
    void store(String objectKey, String contentType, long sizeBytes, InputStream input) throws IOException;

    void delete(String objectKey) throws IOException;

    byte[] read(String objectKey) throws IOException;
}
