package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.FulfillmentProvenanceCreateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.FulfillmentProvenanceEntryVO;
import com.zhangxuan.runruncampus.vo.FulfillmentProvenanceSummaryVO;
import java.util.List;





























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface FulfillmentProvenanceService {

    FulfillmentProvenanceEntryVO record(AppUser administrator, FulfillmentProvenanceCreateRequest request);

    FulfillmentProvenanceEntryVO verify(AppUser administrator, long id);

    List<FulfillmentProvenanceEntryVO> list(int page);

    FulfillmentProvenanceSummaryVO summary();
}
