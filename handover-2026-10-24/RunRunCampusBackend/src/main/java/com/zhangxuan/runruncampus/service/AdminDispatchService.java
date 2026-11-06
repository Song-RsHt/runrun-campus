package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.vo.DispatchCandidatesVO;
import com.zhangxuan.runruncampus.vo.DispatchResultVO;






























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminDispatchService {

    DispatchCandidatesVO candidates(Long orderId, int requestedPage);

    DispatchResultVO dispatch(AppUser administrator, Long orderId, Long courierId);
}
