package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.CompleteOrderRequest;
import com.zhangxuan.runruncampus.dto.ConfirmPackageSizeRequest;
import com.zhangxuan.runruncampus.dto.CreateOrderRequest;
import com.zhangxuan.runruncampus.dto.CreateRatingRequest;
import com.zhangxuan.runruncampus.dto.LocationUpdateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.vo.CourierLocationResponse;
import com.zhangxuan.runruncampus.vo.CourierRatingResponse;
import com.zhangxuan.runruncampus.vo.DeliveryCodeResponse;
import com.zhangxuan.runruncampus.vo.MineOrderPage;
import com.zhangxuan.runruncampus.vo.OrderEventResponse;
import com.zhangxuan.runruncampus.vo.OrderResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 订单创建与状态管理业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface OrderService {

    /**
     * 校验用户角色、取送区域及优惠条件后创建 legacy 订单。
     *
     * <p>本入口不提供请求去重；订单与创建事件、优惠权益变更使用原有事务边界。
     *
     * @param customer 当前用户账号
     * @param request 下单地址、服务规格及计价校验参数
     * @return 订单详情及当前状态
     */
    OrderResponse create(AppUser customer, CreateOrderRequest request);

    /**
     * 使用可选请求标识创建 legacy 订单，并在用户锁内校验重复请求内容。
     *
     * <p>同一标识和相同内容返回已创建订单；标识被其他下单内容占用时拒绝。
     *
     * @param customer 当前用户账号
     * @param request 下单地址、服务规格及计价校验参数
     * @param requestKey 创建订单的幂等标识
     * @return 订单详情及当前状态
     */
    OrderResponse create(AppUser customer, CreateOrderRequest request, UUID requestKey);

    /**
     * 查询当前账号参与的全部订单。
     *
     * @param user 当前已识别账号
     * @return 订单列表，顺序和筛选沿用原查询
     */
    List<OrderResponse> mine(AppUser user);

    /**
     * 查询当前账号参与的进行中订单。
     *
     * @param user 当前已识别账号
     * @return 订单列表，顺序和筛选沿用原查询
     */
    List<OrderResponse> mineActive(AppUser user);

    /**
     * 按分页及可选时间、状态条件查询历史订单。
     *
     * @param user 当前已识别账号
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 本人订单分页
     */
    MineOrderPage mineHistory(AppUser user, int page, int size);

    /**
     * 按分页及可选时间、状态条件查询历史订单。
     *
     * @param user 当前已识别账号
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @param status 可选业务状态筛选
     * @param startDate 历史订单起始日期
     * @param endDate 历史订单结束日期
     * @return 本人订单分页
     */
    MineOrderPage mineHistory(
            AppUser user,
            int page,
            int size,
            OrderStatus status,
            LocalDate startDate,
            LocalDate endDate);

    /**
     * 查询当前骑手可领取的订单。
     *
     * @param courier 当前骑手账号或标识
     * @return 订单列表，顺序和筛选沿用原查询
     */
    List<OrderResponse> available(AppUser courier);

    /**
     * 读取当前账号有权访问的订单详情。
     *
     * @param user 当前已识别账号
     * @param id 目标业务记录标识
     * @return 订单详情及当前状态
     */
    OrderResponse get(AppUser user, Long id);

    /**
     * 读取有权访问的订单事件记录。
     *
     * @param user 当前已识别账号
     * @param id 目标业务记录标识
     * @return 订单状态事件列表，顺序和筛选沿用原查询
     */
    List<OrderEventResponse> events(AppUser user, Long id);

    /**
     * 读取或记录订单履约位置，保留原有位置权限与事件审计。
     *
     * @param user 当前已识别账号
     * @param id 目标业务记录标识
     * @return 骑手位置
     */
    CourierLocationResponse location(AppUser user, Long id);

    /**
     * 记录骑手位置并返回允许参与者查看的位置。
     *
     * @param courier 当前骑手账号或标识
     * @param id 目标业务记录标识
     * @param request 位置更新
     * @return 骑手位置
     */
    CourierLocationResponse updateLocation(AppUser courier, Long id, LocationUpdateRequest request);

    /**
     * 校验接单资格、名额和订单状态后领取任务。
     *
     * @param courier 当前骑手账号或标识
     * @param id 目标业务记录标识
     * @return 订单详情及当前状态
     */
    OrderResponse accept(AppUser courier, Long id);

    /**
     * 按原取消规则关闭订单及资金义务，保存退款和事件。
     *
     * @param customer 当前用户账号
     * @param id 目标业务记录标识
     * @return 订单详情及当前状态
     */
    OrderResponse cancel(AppUser customer, Long id);

    /**
     * 骑手提交实际规格与调整价格，进入用户确认流程。
     *
     * @param courier 当前骑手账号或标识
     * @param id 目标业务记录标识
     * @param request 确认包裹规格
     * @return 订单详情及当前状态
     */
    OrderResponse confirmPackageSize(AppUser courier, Long id, ConfirmPackageSizeRequest request);

    /**
     * 用户同意规格变更并执行原有资金调整。
     *
     * @param customer 当前用户账号
     * @param id 目标业务记录标识
     * @return 订单详情及当前状态
     */
    OrderResponse approvePackageSize(AppUser customer, Long id);

    /**
     * 用户拒绝规格变更，按原流程处理订单与资金。
     *
     * @param customer 当前用户账号
     * @param id 目标业务记录标识
     * @return 订单详情及当前状态
     */
    OrderResponse rejectPackageSize(AppUser customer, Long id);

    /**
     * 在取件凭证满足条件后记录骑手已取件。
     *
     * @param courier 当前骑手账号或标识
     * @param id 目标业务记录标识
     * @return 订单详情及当前状态
     */
    OrderResponse markPickedUp(AppUser courier, Long id);

    /**
     * 向有权用户返回订单取件交付验证码。
     *
     * @param customer 当前用户账号
     * @param id 目标业务记录标识
     * @return 投递验证码
     */
    DeliveryCodeResponse deliveryCode(AppUser customer, Long id);

    /**
     * 验证交付条件后完成订单，保持原结算与通知规则。
     *
     * @param courier 当前骑手账号或标识
     * @param id 目标业务记录标识
     * @param request 用户提供的交付验证码
     * @return 订单详情及当前状态
     */
    OrderResponse complete(AppUser courier, Long id, CompleteOrderRequest request);

    /**
     * 用户对已完成订单评价，保留评价次数和归属检查。
     *
     * @param customer 当前用户账号
     * @param id 目标业务记录标识
     * @param request 评价分值及评价内容
     * @return 订单详情及当前状态
     */
    OrderResponse rate(AppUser customer, Long id, CreateRatingRequest request);

    /**
     * 汇总骑手已获得的评价。
     *
     * @param courier 当前骑手账号或标识
     * @return 骑手评价
     */
    CourierRatingResponse courierRating(AppUser courier);
}
