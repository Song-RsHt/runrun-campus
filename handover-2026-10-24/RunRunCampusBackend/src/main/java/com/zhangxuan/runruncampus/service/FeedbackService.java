package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import com.zhangxuan.runruncampus.vo.FeedbackResponse;
import java.util.List;















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface FeedbackService {

    List<FeedbackResponse> list(AppUser user);

    FeedbackResponse submit(AppUser user, FeedbackType type, String content);
}
