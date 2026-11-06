package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.CreateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.Entry;
import com.zhangxuan.runruncampus.vo.Summary;
import java.util.List;






















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface FulfillmentProvenanceService {

    Entry record(AppUser administrator, CreateRequest request);

    Entry verify(AppUser administrator, long id);

    List<Entry> list(int page);

    Summary summary();
}
