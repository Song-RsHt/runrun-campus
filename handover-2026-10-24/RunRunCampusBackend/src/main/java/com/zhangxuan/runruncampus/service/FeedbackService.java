package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import com.zhangxuan.runruncampus.vo.FeedbackResponse;

import java.util.List;

/** 用户反馈业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface FeedbackService {

    /**
     * 查询用户反馈列表，保留现有筛选和分页口径。
     *
     * @param user 当前已识别账号
     * @return 用户反馈列表，顺序和筛选沿用原查询
     */
    List<FeedbackResponse> list(AppUser user);

    /**
     * 提交用户反馈资料。
     *
     * @param user 当前已识别账号
     * @param type 本次操作的业务分类
     * @param content 消息或反馈正文
     * @return 用户反馈
     */
    FeedbackResponse submit(AppUser user, FeedbackType type, String content);
}
