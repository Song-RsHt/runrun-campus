package com.zhangxuan.runruncampus.service.credit;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.credit.RiskSignalStatus;
import com.zhangxuan.runruncampus.vo.credit.CreditChangeVO;
import com.zhangxuan.runruncampus.vo.credit.CreditProfileVO;
import com.zhangxuan.runruncampus.vo.credit.RiskSignalPageVO;
import com.zhangxuan.runruncampus.vo.credit.RiskSignalVO;

/** 信用分及风险信号业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CreditService {

    /**
     * 查询当前账号的信用资料及事件分页。
     *
     * @param user 当前已识别账号
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 用户信用分及信用事件分页
     */
    CreditProfileVO profile(AppUser user, int page, int size);

    /**
     * 查询指定用户的信用资料及事件分页。
     *
     * @param userId 目标用户标识
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 用户信用分及信用事件分页
     */
    CreditProfileVO profileForAdmin(Long userId, int page, int size);

    /**
     * 管理员按理由调整信用分，保留可追溯事件。
     *
     * @param administrator 已通过权限校验的管理员
     * @param userId 目标用户标识
     * @param delta 信用分调整值
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @return 信用分变更记录
     */
    CreditChangeVO adjust(AppUser administrator, Long userId, int delta, String reason);

    /**
     * 冲销指定信用事件并保存操作理由。
     *
     * @param administrator 已通过权限校验的管理员
     * @param userId 目标用户标识
     * @param eventId 待冲销信用事件标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @return 信用分变更记录
     */
    CreditChangeVO reverse(AppUser administrator, Long userId, Long eventId, String reason);

    /**
     * 确认风险信号并执行指定信用分调整。
     *
     * @param administrator 已通过权限校验的管理员
     * @param signalId 风险信号标识
     * @param delta 信用分调整值
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @return 信用分变更记录
     */
    CreditChangeVO confirmSignal(AppUser administrator, Long signalId, int delta, String reason);

    /**
     * 驳回风险信号并保存说明。
     *
     * @param administrator 已通过权限校验的管理员
     * @param signalId 风险信号标识
     * @param note 处理说明
     * @return 风险信号
     */
    RiskSignalVO dismissSignal(AppUser administrator, Long signalId, String note);

    /**
     * 按状态分页查询风险信号。
     *
     * @param status 可选业务状态筛选
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 风险信号分页
     */
    RiskSignalPageVO signals(RiskSignalStatus status, int page, int size);
}
