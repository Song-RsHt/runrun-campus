package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.OrderQuoteRequest;
import com.zhangxuan.runrunv2.dto.ServiceConfigChangeRequest;
import com.zhangxuan.runrunv2.model.OrderPricing;
import com.zhangxuan.runrunv2.vo.OrderQuoteVO;
import com.zhangxuan.runrunv2.vo.ServiceConfigVO;

import java.util.Map;

/** 新系统服务配置及计价业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface ServiceConfigService {

    /**
     * 读取服务配置，更新模式下先锁共享配置行。
     *
     * @param update 是否锁定服务配置用于更新
     * @return 当前服务配置行；更新模式下该行已加锁
     */
    Map<String, Object> settings(boolean update);

    /**
     * 读取当前服务配置及其并发版本。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @return 新系统服务配置及计价
     */
    ServiceConfigVO config(String token);

    /**
     * 按服务配置、地址、时间和规格计算报价，不创建订单。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param request 订单创建与状态管理报价
     * @return 订单创建与状态管理报价
     */
    OrderQuoteVO quote(String token, OrderQuoteRequest request);

    /**
     * 使用给定配置计算金额，保持原有整数计价与舍入。
     *
     * @param request 订单创建与状态管理报价
     * @param settings 当前业务使用的服务配置快照
     * @return 订单计价快照
     */
    OrderPricing price(OrderQuoteRequest request, Map<String, Object> settings);

    /**
     * 按代码读取配送区域。
     *
     * @param code 配送区域代码
     * @return 匹配代码的配送区域配置行
     */
    Map<String, Object> zone(String code);

    /**
     * 按代码读取服务点。
     *
     * @param code 服务点代码
     * @return 匹配代码的服务点配置行
     */
    Map<String, Object> point(String code);

    /**
     * 根据营业及运行设置验证是否允许新建订单。
     *
     * @param settings 当前业务使用的服务配置快照
     */
    void allowNewOrder(Map<String, Object> settings);

    /**
     * 将服务设置映射为现有接口策略字段。
     *
     * @param settings 当前业务使用的服务配置快照
     * @return 写入订单的服务策略快照字段
     */
    Map<String, Object> policy(Map<String, Object> settings);

    /**
     * 校验配置变更，先锁单例再原子更新区域、服务点和版本。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param key 本次操作的幂等键，作用域由现有业务规则决定
     * @param change 新系统服务配置及计价变更
     * @return 新系统服务配置及计价
     */
    ServiceConfigVO patch(String token, String key, ServiceConfigChangeRequest change);
}
