package com.zhangxuan.runruncampus.service.address;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.order.DeliveryZone;
import com.zhangxuan.runruncampus.vo.address.AddressResponse;

import java.util.List;

/** 收件地址业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AddressService {

    /**
     * 查询收件地址列表，保留现有筛选和分页口径。
     *
     * @param user 当前已识别账号
     * @return 收件地址列表，顺序和筛选沿用原查询
     */
    List<AddressResponse> list(AppUser user);

    /**
     * 创建收件地址记录，按现有校验和持久化规则处理。
     *
     * @param user 当前已识别账号
     * @param label 收件地址标签
     * @param address 收件地址正文
     * @param defaultAddress 是否设置为默认地址
     * @return 收件地址
     */
    AddressResponse create(AppUser user, String label, String address, boolean defaultAddress);

    /**
     * 创建收件地址记录，按现有校验和持久化规则处理。
     *
     * @param user 当前已识别账号
     * @param label 收件地址标签
     * @param address 收件地址正文
     * @param deliveryZone 收件地址所属配送区域
     * @param defaultAddress 是否设置为默认地址
     * @return 收件地址
     */
    AddressResponse create(
            AppUser user,
            String label,
            String address,
            DeliveryZone deliveryZone,
            boolean defaultAddress);

    /**
     * 更新收件地址记录，保持原有权限及并发条件。
     *
     * @param user 当前已识别账号
     * @param id 目标业务记录标识
     * @param label 收件地址标签
     * @param address 收件地址正文
     * @param defaultAddress 是否设置为默认地址
     * @return 收件地址
     */
    AddressResponse update(
            AppUser user, Long id, String label, String address, boolean defaultAddress);

    /**
     * 更新收件地址记录，保持原有权限及并发条件。
     *
     * @param user 当前已识别账号
     * @param id 目标业务记录标识
     * @param label 收件地址标签
     * @param address 收件地址正文
     * @param deliveryZone 收件地址所属配送区域
     * @param defaultAddress 是否设置为默认地址
     * @return 收件地址
     */
    AddressResponse update(
            AppUser user,
            Long id,
            String label,
            String address,
            DeliveryZone deliveryZone,
            boolean defaultAddress);

    /**
     * 设置用户默认收件地址，并取消原默认地址。
     *
     * @param user 当前已识别账号
     * @param id 目标业务记录标识
     * @return 收件地址
     */
    AddressResponse setDefault(AppUser user, Long id);

    /**
     * 删除属于当前用户的收件地址。
     *
     * @param user 当前已识别账号
     * @param id 目标业务记录标识
     */
    void delete(AppUser user, Long id);
}
