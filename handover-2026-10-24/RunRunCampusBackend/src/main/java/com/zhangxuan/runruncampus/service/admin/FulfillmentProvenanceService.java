package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.dto.provenance.FulfillmentProvenanceCreateRequest;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.vo.provenance.FulfillmentProvenanceEntryVO;
import com.zhangxuan.runruncampus.vo.provenance.FulfillmentProvenanceSummaryVO;

import java.util.List;

/** 履约来源及财务凭证登记业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface FulfillmentProvenanceService {

    /**
     * 登记履约来源及财务凭证登记事件及必要的操作来源。
     *
     * @param administrator 已通过权限校验的管理员
     * @param request 履约来源及财务凭证登记创建
     * @return 履约来源及财务核验记录
     */
    FulfillmentProvenanceEntryVO record(
            AppUser administrator, FulfillmentProvenanceCreateRequest request);

    /**
     * 管理员复核履约来源凭证的摘要并登记核验人及时间。
     *
     * @param administrator 已通过权限校验的管理员
     * @param id 目标业务记录标识
     * @return 履约来源及财务核验记录
     */
    FulfillmentProvenanceEntryVO verify(AppUser administrator, long id);

    /**
     * 查询履约来源及财务凭证登记列表，保留现有筛选和分页口径。
     *
     * @param page 从零开始的分页页码
     * @return 履约来源及财务核验记录列表，顺序和筛选沿用原查询
     */
    List<FulfillmentProvenanceEntryVO> list(int page);

    /**
     * 汇总履约来源记录的核验与财务指标。
     *
     * @return 履约来源及财务核验汇总
     */
    FulfillmentProvenanceSummaryVO summary();
}
