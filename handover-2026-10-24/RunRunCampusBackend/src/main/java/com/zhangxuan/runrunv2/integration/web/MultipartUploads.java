package com.zhangxuan.runrunv2.integration.web;

import com.zhangxuan.runrunv2.model.UploadSource;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;







/** 将 HTTP 上传适配为业务输入；仅延迟委托，保持原有校验、锁和文件读取顺序。 */
public final class MultipartUploads {
    private MultipartUploads() {}

    public static UploadSource adapt(MultipartFile file) {
        if (file == null) {
            return null;
        }
        return new UploadSource() {
            @Override
            public boolean isEmpty() { return file.isEmpty(); }
            @Override
            public long getSize() { return file.getSize(); }
            @Override
            public String getContentType() { return file.getContentType(); }
            @Override
            public String getOriginalFilename() { return file.getOriginalFilename(); }
            @Override
            public byte[] getBytes() throws IOException { return file.getBytes(); }
            @Override
            public InputStream getInputStream() throws IOException { return file.getInputStream(); }
        };
    }
}
