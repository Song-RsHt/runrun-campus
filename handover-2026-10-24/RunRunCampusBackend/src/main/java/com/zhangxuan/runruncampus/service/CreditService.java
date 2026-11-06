package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.RiskSignalStatus;
import com.zhangxuan.runruncampus.vo.CreditChangeVO;
import com.zhangxuan.runruncampus.vo.CreditProfileVO;
import com.zhangxuan.runruncampus.vo.RiskSignalPageVO;
import com.zhangxuan.runruncampus.vo.RiskSignalVO;































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CreditService {

    CreditProfileVO profile(AppUser user, int page, int size);

    CreditProfileVO profileForAdmin(Long userId, int page, int size);

    CreditChangeVO adjust(AppUser administrator, Long userId, int delta, String reason);

    CreditChangeVO reverse(AppUser administrator, Long userId, Long eventId, String reason);

    CreditChangeVO confirmSignal(AppUser administrator, Long signalId, int delta, String reason);

    RiskSignalVO dismissSignal(AppUser administrator, Long signalId, String note);

    RiskSignalPageVO signals(RiskSignalStatus status, int page, int size);
}
