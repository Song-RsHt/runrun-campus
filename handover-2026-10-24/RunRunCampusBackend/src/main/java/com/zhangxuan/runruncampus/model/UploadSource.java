package com.zhangxuan.runruncampus.model;

import java.io.IOException;
import java.io.InputStream;





/** 待上传文件的延迟读取契约；业务层在完成鉴权和状态校验后决定何时读取内容。 */
public interface UploadSource {
    boolean isEmpty();
    long getSize();
    String getContentType();
    String getOriginalFilename();
    byte[] getBytes() throws IOException;
    InputStream getInputStream() throws IOException;
}
